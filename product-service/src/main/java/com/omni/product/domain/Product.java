package com.omni.product.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Catalog view of a product. Free-form {@code attributes} is why this lives in MongoDB:
 * a phone has "screen"/"chip", a T-shirt has "material"/"fit", without schema migrations.
 */
@Document("products")
@CompoundIndex(name = "uniq_variant_sku", def = "{'variants.sku': 1}", unique = true, sparse = true)
@Getter
@Setter
@NoArgsConstructor
public class Product {

    public enum Status { ACTIVE, INACTIVE }

    @Id
    private String id;

    private String name;

    @Indexed(unique = true)
    private String slug;

    private String description;

    @Indexed
    private String brand;

    @Indexed
    private String categoryId;

    private List<String> images = new ArrayList<>();

    private Map<String, String> attributes = new LinkedHashMap<>();

    private List<Variant> variants = new ArrayList<>();

    private Status status = Status.ACTIVE;

    @Version
    private Long version;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    public long minPrice() {
        return variants.stream().mapToLong(Variant::getPrice).min().orElse(0);
    }

    public long maxPrice() {
        return variants.stream().mapToLong(Variant::getPrice).max().orElse(0);
    }

    /**
     * Sellable unit (SKU). Stock is NOT stored here: it belongs to the Inventory bounded context.
     */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class Variant {
        private String sku;
        private String name;
        /** VND. */
        private long price;
        private Long compareAtPrice;
        private Map<String, String> options = new LinkedHashMap<>();
        private String image;
        private boolean active = true;
    }
}
