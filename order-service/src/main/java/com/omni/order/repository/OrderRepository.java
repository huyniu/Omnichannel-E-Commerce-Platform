package com.omni.order.repository;

import com.omni.order.domain.Order;
import com.omni.order.domain.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    @EntityGraph(attributePaths = "items")
    Optional<Order> findWithItemsById(UUID id);

    Optional<Order> findByUserIdAndIdempotencyKey(Long userId, String idempotencyKey);

    Page<Order> findByUserId(Long userId, Pageable pageable);

    Page<Order> findByStatus(OrderStatus status, Pageable pageable);

    @Query("select o.id from Order o where o.status = :status and o.paymentMethod <> :excluded and o.createdAt < :before order by o.createdAt")
    List<UUID> findExpiredIds(OrderStatus status, Order.PaymentMethod excluded, Instant before, Pageable pageable);

    long countByStatus(OrderStatus status);
}
