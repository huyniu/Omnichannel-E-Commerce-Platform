package com.omni.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Stock level of one SKU. Mutations go through conditional UPDATE statements in
 * {@link com.omni.order.repository.InventoryRepository} so stock can never go negative,
 * even without the Redis lock.
 */
@Entity
@Table(name = "inventory")
@Getter
@Setter
@NoArgsConstructor
public class InventoryItem {

    @Id
    private String sku;

    @Column(nullable = false)
    private int available;

    @Column(nullable = false)
    private int reserved;

    @Column(nullable = false)
    private int sold;

    @Version
    private Long version;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
