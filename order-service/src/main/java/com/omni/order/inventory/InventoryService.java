package com.omni.order.inventory;

import com.omni.common.exception.BusinessException;
import com.omni.order.domain.InventoryItem;
import com.omni.order.domain.StockReservation;
import com.omni.order.repository.InventoryRepository;
import com.omni.order.repository.StockReservationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Stock movements. All methods join the caller's transaction so stock, reservations, order state and
 * outbox events commit (or roll back) together.
 */
@Service
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryRepository inventory;
    private final StockReservationRepository reservations;

    /** Reserves every line or none (any shortage rolls back the whole transaction). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void reserve(UUID orderId, Map<String, Integer> quantities) {
        // Sorted order = consistent row-lock order across concurrent transactions (no DB deadlocks).
        new TreeMap<>(quantities).forEach((sku, qty) -> {
            if (inventory.reserve(sku, qty) == 0) {
                throw BusinessException.conflict("OUT_OF_STOCK", "Not enough stock for " + sku);
            }
            StockReservation r = new StockReservation();
            r.setOrderId(orderId);
            r.setSku(sku);
            r.setQuantity(qty);
            reservations.save(r);
        });
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void commit(UUID orderId) {
        for (StockReservation r : reservations.findByOrderId(orderId)) {
            if (r.getStatus() == StockReservation.Status.RESERVED) {
                if (inventory.commit(r.getSku(), r.getQuantity()) == 0) {
                    throw new IllegalStateException("Reservation inconsistent for " + r.getSku());
                }
                r.setStatus(StockReservation.Status.COMMITTED);
            }
        }
    }

    /** Compensating action: gives every unit of the order back to available stock. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void releaseAll(UUID orderId) {
        for (StockReservation r : reservations.findByOrderId(orderId)) {
            switch (r.getStatus()) {
                case RESERVED -> {
                    inventory.release(r.getSku(), r.getQuantity());
                    r.setStatus(StockReservation.Status.RELEASED);
                }
                case COMMITTED -> {
                    inventory.returnSold(r.getSku(), r.getQuantity());
                    r.setStatus(StockReservation.Status.RETURNED);
                }
                default -> {
                    // already compensated
                }
            }
        }
    }

    @Transactional(readOnly = true)
    public List<String> skusOf(UUID orderId) {
        return reservations.findByOrderId(orderId).stream().map(StockReservation::getSku).toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Integer> availability(Collection<String> skus) {
        return inventory.findBySkuIn(skus).stream()
                .collect(Collectors.toMap(InventoryItem::getSku, InventoryItem::getAvailable));
    }

    @Transactional(readOnly = true)
    public List<InventoryItem> find(Collection<String> skus) {
        return inventory.findBySkuIn(skus);
    }

    @Transactional(readOnly = true)
    public InventoryItem get(String sku) {
        return inventory.findById(sku).orElseThrow(() -> BusinessException.notFound("SKU " + sku));
    }

    /** Adds (or removes, if negative) units of available stock; creates the SKU row on first restock. */
    @Transactional
    public InventoryItem adjust(String sku, int delta) {
        if (inventory.insertIfAbsent(sku, Math.max(delta, 0)) == 1) {
            return inventory.findById(sku).orElseThrow();
        }
        if (inventory.adjust(sku, delta) == 0) {
            throw BusinessException.conflict("INVALID_ADJUSTMENT", "Available stock cannot become negative");
        }
        return inventory.findById(sku).orElseThrow();
    }
}
