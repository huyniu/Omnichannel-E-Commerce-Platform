package com.omni.order.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * Order state machine.
 * <pre>
 * PENDING ──payment ok──▶ CONFIRMED ──▶ SHIPPING ──▶ COMPLETED
 *    │                        │
 *    └──payment failed/timeout/cancel──▶ CANCELLED ◀──cancel (refund)
 * </pre>
 */
public enum OrderStatus {
    PENDING, CONFIRMED, SHIPPING, COMPLETED, CANCELLED;

    public Set<OrderStatus> next() {
        return switch (this) {
            case PENDING -> EnumSet.of(CONFIRMED, CANCELLED);
            case CONFIRMED -> EnumSet.of(SHIPPING, CANCELLED);
            case SHIPPING -> EnumSet.of(COMPLETED);
            case COMPLETED, CANCELLED -> EnumSet.noneOf(OrderStatus.class);
        };
    }

    public boolean canMoveTo(OrderStatus target) {
        return next().contains(target);
    }
}
