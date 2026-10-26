package com.omni.order.repository;

import com.omni.order.domain.InventoryItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

/**
 * Every mutation is a single conditional UPDATE: the WHERE clause is the business rule
 * ("only if enough stock"), and the row lock taken by the UPDATE makes it atomic.
 * A return value of 0 means the rule was not satisfied.
 */
public interface InventoryRepository extends JpaRepository<InventoryItem, String> {

    List<InventoryItem> findBySkuIn(Collection<String> skus);

    /** available -> reserved (place order). */
    @Modifying
    @Query(value = """
            UPDATE inventory SET available = available - :qty, reserved = reserved + :qty,
                   version = version + 1, updated_at = now()
            WHERE sku = :sku AND available >= :qty
            """, nativeQuery = true)
    int reserve(String sku, int qty);

    /** reserved -> sold (payment succeeded / COD confirmed). */
    @Modifying
    @Query(value = """
            UPDATE inventory SET reserved = reserved - :qty, sold = sold + :qty,
                   version = version + 1, updated_at = now()
            WHERE sku = :sku AND reserved >= :qty
            """, nativeQuery = true)
    int commit(String sku, int qty);

    /** reserved -> available (compensation: payment failed / timeout / cancelled before payment). */
    @Modifying
    @Query(value = """
            UPDATE inventory SET reserved = reserved - :qty, available = available + :qty,
                   version = version + 1, updated_at = now()
            WHERE sku = :sku AND reserved >= :qty
            """, nativeQuery = true)
    int release(String sku, int qty);

    /** sold -> available (a confirmed order was cancelled before shipping). */
    @Modifying
    @Query(value = """
            UPDATE inventory SET sold = sold - :qty, available = available + :qty,
                   version = version + 1, updated_at = now()
            WHERE sku = :sku AND sold >= :qty
            """, nativeQuery = true)
    int returnSold(String sku, int qty);

    /** Restock / correction by back-office staff; never lets available drop below zero. */
    @Modifying
    @Query(value = """
            UPDATE inventory SET available = available + :delta,
                   version = version + 1, updated_at = now()
            WHERE sku = :sku AND available + :delta >= 0
            """, nativeQuery = true)
    int adjust(String sku, int delta);

    @Modifying
    @Query(value = "INSERT INTO inventory (sku, available) VALUES (:sku, :available) ON CONFLICT (sku) DO NOTHING",
            nativeQuery = true)
    int insertIfAbsent(String sku, int available);
}
