package com.omni.order.service;

import com.omni.common.event.Events;
import com.omni.common.event.OrderEvents;
import com.omni.common.exception.BusinessException;
import com.omni.common.web.CurrentUser;
import com.omni.order.config.OrderProperties;
import com.omni.order.domain.Order;
import com.omni.order.domain.OrderItem;
import com.omni.order.domain.OrderStatus;
import com.omni.order.domain.ProcessedEvent;
import com.omni.order.inventory.InventoryService;
import com.omni.order.messaging.OutboxWriter;
import com.omni.order.repository.OrderRepository;
import com.omni.order.repository.ProcessedEventRepository;
import com.omni.order.web.Dtos.OrderResponse;
import com.omni.order.web.Dtos.PlaceOrderRequest;
import com.omni.proto.product.SkuInfo;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Transaction boundaries of the order aggregate. Each public method is ONE local ACID transaction:
 * order state + stock + reservations + outbox event + processed-event marker.
 * Callers that move available stock hold the SKU locks around these calls (see {@link OrderService}).
 */
@Service
@RequiredArgsConstructor
public class OrderTxService {

    private static final Logger log = LoggerFactory.getLogger(OrderTxService.class);
    private static final DateTimeFormatter CODE_DATE = DateTimeFormatter.ofPattern("yyMMdd");
    private static final char[] CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();

    private final OrderRepository orders;
    private final ProcessedEventRepository processedEvents;
    private final InventoryService inventory;
    private final OutboxWriter outbox;
    private final OrderProperties props;
    private final SecureRandom random = new SecureRandom();

    /** Result of a transaction that changed available stock (used to refresh sold-out flags). */
    public record StockChange(OrderResponse order, Map<String, Integer> availability) {
    }

    @Transactional
    public StockChange create(CurrentUser user, PlaceOrderRequest req, Map<String, Integer> quantities,
                              Map<String, SkuInfo> snapshots, String idempotencyKey) {
        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setCode(newCode());
        order.setUserId(user.id());
        order.setCustomerEmail(user.email());
        order.setPaymentMethod(req.paymentMethod());
        order.setShippingName(req.shipping().receiverName());
        order.setShippingPhone(req.shipping().phone());
        order.setShippingAddress(req.shipping().address());
        order.setNote(req.note());
        order.setIdempotencyKey(idempotencyKey);

        long subtotal = 0;
        for (Map.Entry<String, Integer> line : quantities.entrySet()) {
            SkuInfo info = snapshots.get(line.getKey());
            OrderItem item = new OrderItem();
            item.setSku(info.getSku());
            item.setProductId(info.getProductId());
            item.setProductName(info.getProductName());
            item.setVariantName(info.getVariantName());
            item.setImageUrl(info.getImageUrl());
            item.setUnitPrice(info.getPrice());
            item.setQuantity(line.getValue());
            item.setLineTotal(info.getPrice() * line.getValue());
            subtotal += item.getLineTotal();
            order.addItem(item);
        }
        long shippingFee = subtotal >= props.freeShippingFrom() ? 0 : props.shippingFee();
        order.setSubtotal(subtotal);
        order.setShippingFee(shippingFee);
        order.setTotalAmount(subtotal + shippingFee);
        order.transitionTo(OrderStatus.PENDING, "Order placed", actor(user));
        orders.saveAndFlush(order);

        // Saga step 1: reserve stock (local transaction). Shortage => everything above rolls back.
        inventory.reserve(order.getId(), quantities);

        outbox.append(Events.TOPIC_ORDER, Events.ORDER_CREATED, order.getId().toString(), new OrderEvents.OrderCreated(
                order.getId().toString(), order.getCode(), order.getUserId(), order.getCustomerEmail(),
                order.getTotalAmount(), order.getPaymentMethod().name(),
                order.getItems().stream().map(i -> new OrderEvents.Item(i.getSku(), i.getProductName(),
                        i.getUnitPrice(), i.getQuantity())).toList(),
                order.getCreatedAt()));

        if (order.getPaymentMethod() == Order.PaymentMethod.COD) {
            // Cash on delivery needs no online payment: confirm immediately.
            inventory.commit(order.getId());
            order.transitionTo(OrderStatus.CONFIRMED, "Cash on delivery", "system");
            appendConfirmed(order);
        }
        return new StockChange(OrderResponse.from(order, true), inventory.availability(quantities.keySet()));
    }

    /** Saga step 2 (success path): payment captured, reserved stock becomes sold. */
    @Transactional
    public void onPaymentSucceeded(String eventId, UUID orderId) {
        if (alreadyProcessed(eventId)) {
            return;
        }
        Optional<Order> found = orders.findById(orderId);
        if (found.isEmpty()) {
            log.warn("PaymentSucceeded for unknown order {}", orderId);
            return;
        }
        Order order = found.get();
        if (order.getStatus() == OrderStatus.PENDING) {
            inventory.commit(orderId);
            order.setPaymentStatus(Order.PaymentStatus.PAID);
            order.transitionTo(OrderStatus.CONFIRMED, "Payment succeeded", "payment-service");
            appendConfirmed(order);
        } else if (order.getStatus() == OrderStatus.CANCELLED && order.getPaymentStatus() == Order.PaymentStatus.UNPAID) {
            // Paid after the order had already timed out: ask Payment to refund.
            order.setPaymentStatus(Order.PaymentStatus.PAID);
            outbox.append(Events.TOPIC_ORDER, Events.ORDER_CANCELLED, order.getId().toString(),
                    new OrderEvents.OrderCancelled(order.getId().toString(), order.getCode(), order.getUserId(),
                            order.getCustomerEmail(), "Payment received after order was cancelled", true));
        }
    }

    /**
     * Compensating transaction: cancels the order and gives its stock back.
     *
     * @param onlyIfPending true for payment failure / timeout, where a concurrent success must win
     */
    @Transactional
    public Optional<StockChange> cancel(String eventId, UUID orderId, String reason, String actor, boolean onlyIfPending) {
        if (alreadyProcessed(eventId)) {
            return Optional.empty();
        }
        Order order = orders.findWithItemsById(orderId).orElseThrow(() -> BusinessException.notFound("Order"));
        if (onlyIfPending && order.getStatus() != OrderStatus.PENDING) {
            return Optional.empty();
        }
        if (!order.getStatus().canMoveTo(OrderStatus.CANCELLED)) {
            throw BusinessException.conflict("CANNOT_CANCEL", "Order in status " + order.getStatus() + " cannot be cancelled");
        }
        inventory.releaseAll(orderId);
        order.transitionTo(OrderStatus.CANCELLED, reason, actor);
        order.setCancelReason(reason);
        boolean refund = order.getPaymentStatus() == Order.PaymentStatus.PAID;
        outbox.append(Events.TOPIC_ORDER, Events.ORDER_CANCELLED, order.getId().toString(),
                new OrderEvents.OrderCancelled(order.getId().toString(), order.getCode(), order.getUserId(),
                        order.getCustomerEmail(), reason, refund));
        var skus = order.getItems().stream().map(OrderItem::getSku).toList();
        return Optional.of(new StockChange(OrderResponse.from(order, true), inventory.availability(skus)));
    }

    /** Fulfilment transitions driven by back-office staff (CONFIRMED -> SHIPPING -> COMPLETED). */
    @Transactional
    public OrderResponse updateStatus(UUID orderId, OrderStatus target, String reason, String actor) {
        if (target == OrderStatus.CANCELLED || target == OrderStatus.PENDING || target == OrderStatus.CONFIRMED) {
            throw BusinessException.badRequest("INVALID_TARGET", "Use the cancel endpoint, or wait for payment confirmation");
        }
        Order order = orders.findWithItemsById(orderId).orElseThrow(() -> BusinessException.notFound("Order"));
        OrderStatus from = order.transitionTo(target, reason, actor);
        if (target == OrderStatus.COMPLETED && order.getPaymentMethod() == Order.PaymentMethod.COD) {
            order.setPaymentStatus(Order.PaymentStatus.PAID);
        }
        outbox.append(Events.TOPIC_ORDER, Events.ORDER_STATUS_CHANGED, order.getId().toString(),
                new OrderEvents.OrderStatusChanged(order.getId().toString(), order.getCode(), order.getUserId(),
                        order.getCustomerEmail(), from.name(), target.name()));
        return OrderResponse.from(order, true);
    }

    @Transactional
    public void onPaymentRefunded(String eventId, UUID orderId) {
        if (alreadyProcessed(eventId)) {
            return;
        }
        orders.findById(orderId).ifPresent(o -> o.setPaymentStatus(Order.PaymentStatus.REFUNDED));
    }

    @Transactional(readOnly = true)
    public Optional<OrderResponse> findByIdempotencyKey(Long userId, String key) {
        return orders.findByUserIdAndIdempotencyKey(userId, key).map(o -> OrderResponse.from(o, true));
    }

    private void appendConfirmed(Order order) {
        outbox.append(Events.TOPIC_ORDER, Events.ORDER_CONFIRMED, order.getId().toString(),
                new OrderEvents.OrderConfirmed(order.getId().toString(), order.getCode(), order.getUserId(),
                        order.getCustomerEmail(), order.getTotalAmount()));
    }

    /** Returns true if the event was seen before; otherwise records it in the current transaction. */
    private boolean alreadyProcessed(String eventId) {
        if (eventId == null) {
            return false;
        }
        if (processedEvents.existsById(eventId)) {
            log.debug("Skipping duplicate event {}", eventId);
            return true;
        }
        processedEvents.save(new ProcessedEvent(eventId));
        return false;
    }

    private String newCode() {
        StringBuilder sb = new StringBuilder("OD").append(LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).format(CODE_DATE));
        for (int i = 0; i < 6; i++) {
            sb.append(CODE_CHARS[random.nextInt(CODE_CHARS.length)]);
        }
        return sb.toString();
    }

    private static String actor(CurrentUser user) {
        return "user:" + user.id();
    }
}
