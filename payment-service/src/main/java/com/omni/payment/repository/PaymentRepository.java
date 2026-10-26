package com.omni.payment.repository;

import com.omni.payment.domain.Payment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByOrderId(UUID orderId);

    Optional<Payment> findByTxnRef(String txnRef);

    Page<Payment> findByUserId(Long userId, Pageable pageable);

    interface ReconciliationRow {
        String getMethod();

        String getStatus();

        long getCount();

        long getTotal();
    }

    @Query(value = """
            SELECT method AS method, status AS status, COUNT(*) AS count, COALESCE(SUM(amount), 0) AS total
            FROM payments WHERE created_at >= :from AND created_at < :to
            GROUP BY method, status ORDER BY method, status
            """, nativeQuery = true)
    List<ReconciliationRow> reconcile(Instant from, Instant to);
}
