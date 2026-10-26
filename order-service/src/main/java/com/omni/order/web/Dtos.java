package com.omni.order.web;

import com.omni.order.domain.InventoryItem;
import com.omni.order.domain.Order;
import com.omni.order.domain.OrderItem;
import com.omni.order.domain.OrderStatus;
import com.omni.order.domain.OrderStatusHistory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class Dtos {

    private Dtos() {
    }

    public record OrderLine(@NotBlank String sku, @Min(1) int quantity) {
    }

    public record ShippingInfo(@NotBlank String receiverName,
                               @NotBlank @Pattern(regexp = "^\\+?[0-9]{9,15}$", message = "invalid phone number") String phone,
                               @NotBlank @Size(max = 500) String address) {
    }

    public record PlaceOrderRequest(@NotEmpty @Size(max = 20) List<@Valid OrderLine> items,
                                    @NotNull Order.PaymentMethod paymentMethod,
                                    @NotNull @Valid ShippingInfo shipping,
                                    @Size(max = 500) String note) {
    }

    public record CancelRequest(@Size(max = 255) String reason) {
    }

    public record StatusUpdateRequest(@NotNull OrderStatus status, @Size(max = 255) String reason) {
    }

    public record ItemResponse(String sku, String productId, String productName, String variantName, String imageUrl,
                               long unitPrice, int quantity, long lineTotal) {
        static ItemResponse from(OrderItem i) {
            return new ItemResponse(i.getSku(), i.getProductId(), i.getProductName(), i.getVariantName(),
                    i.getImageUrl(), i.getUnitPrice(), i.getQuantity(), i.getLineTotal());
        }
    }

    public record HistoryResponse(OrderStatus from, OrderStatus to, String reason, String changedBy, Instant at) {
        static HistoryResponse from(OrderStatusHistory h) {
            return new HistoryResponse(h.getFromStatus(), h.getToStatus(), h.getReason(), h.getChangedBy(), h.getCreatedAt());
        }
    }

    public record OrderResponse(String id, String code, Long userId, String status, String paymentMethod,
                                String paymentStatus, long subtotal, long shippingFee, long totalAmount,
                                String shippingName, String shippingPhone, String shippingAddress, String note,
                                String cancelReason, List<ItemResponse> items, List<HistoryResponse> history,
                                Instant createdAt, Instant updatedAt) {
        public static OrderResponse from(Order o, boolean withHistory) {
            return new OrderResponse(o.getId().toString(), o.getCode(), o.getUserId(), o.getStatus().name(),
                    o.getPaymentMethod().name(), o.getPaymentStatus().name(), o.getSubtotal(), o.getShippingFee(),
                    o.getTotalAmount(), o.getShippingName(), o.getShippingPhone(), o.getShippingAddress(), o.getNote(),
                    o.getCancelReason(), o.getItems().stream().map(ItemResponse::from).toList(),
                    withHistory ? o.getHistory().stream().map(HistoryResponse::from).toList() : null,
                    o.getCreatedAt(), o.getUpdatedAt());
        }
    }

    public record OrderSummary(String id, String code, String status, String paymentStatus, long totalAmount,
                               Instant createdAt) {
        public static OrderSummary from(Order o) {
            return new OrderSummary(o.getId().toString(), o.getCode(), o.getStatus().name(),
                    o.getPaymentStatus().name(), o.getTotalAmount(), o.getCreatedAt());
        }
    }

    public record InventoryResponse(String sku, int available, int reserved, int sold) {
        public static InventoryResponse from(InventoryItem i) {
            return new InventoryResponse(i.getSku(), i.getAvailable(), i.getReserved(), i.getSold());
        }
    }

    public record AdjustStockRequest(@NotNull Integer delta) {
    }
}
