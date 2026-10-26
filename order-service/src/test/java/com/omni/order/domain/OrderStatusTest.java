package com.omni.order.domain;

import com.omni.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderStatusTest {

    @Test
    void happyPathTransitionsAreAllowed() {
        assertThat(OrderStatus.PENDING.canMoveTo(OrderStatus.CONFIRMED)).isTrue();
        assertThat(OrderStatus.CONFIRMED.canMoveTo(OrderStatus.SHIPPING)).isTrue();
        assertThat(OrderStatus.SHIPPING.canMoveTo(OrderStatus.COMPLETED)).isTrue();
    }

    @Test
    void shippedOrdersCannotBeCancelled() {
        assertThat(OrderStatus.SHIPPING.canMoveTo(OrderStatus.CANCELLED)).isFalse();
        assertThat(OrderStatus.COMPLETED.next()).isEmpty();
        assertThat(OrderStatus.CANCELLED.next()).isEmpty();
    }

    @Test
    void orderRecordsHistoryAndRejectsInvalidTransition() {
        Order order = new Order();
        order.transitionTo(OrderStatus.PENDING, "placed", "user:1");
        order.transitionTo(OrderStatus.CONFIRMED, "paid", "payment-service");

        assertThat(order.getHistory()).hasSize(2);
        assertThat(order.getHistory().get(1).getFromStatus()).isEqualTo(OrderStatus.PENDING);
        assertThatThrownBy(() -> order.transitionTo(OrderStatus.COMPLETED, "skip", "x"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("CONFIRMED to COMPLETED");
    }
}
