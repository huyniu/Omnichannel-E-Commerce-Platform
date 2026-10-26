package com.omni.product.web;

import com.omni.product.domain.Category;
import com.omni.product.domain.Product;
import com.omni.product.search.ProductDocument;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class Dtos {

    private Dtos() {
    }

    public record VariantRequest(
            @NotBlank @Pattern(regexp = "^[A-Z0-9-]{3,64}$", message = "SKU must be 3-64 chars of A-Z, 0-9, '-'") String sku,
            @NotBlank String name,
            @PositiveOrZero long price,
            Long compareAtPrice,
            Map<String, String> options,
            String image,
            Boolean active) {
    }

    public record ProductRequest(
            @NotBlank String name,
            String slug,
            String description,
            @NotBlank String brand,
            @NotBlank String categoryId,
            List<String> images,
            Map<String, String> attributes,
            @NotEmpty @Valid List<VariantRequest> variants,
            Product.Status status) {
    }

    public record VariantResponse(String sku, String name, long price, Long compareAtPrice,
                                  Map<String, String> options, String image, boolean active) {
        static VariantResponse from(Product.Variant v) {
            return new VariantResponse(v.getSku(), v.getName(), v.getPrice(), v.getCompareAtPrice(), v.getOptions(),
                    v.getImage(), v.isActive());
        }
    }

    public record ProductResponse(String id, String name, String slug, String description, String brand,
                                  String categoryId, List<String> images, Map<String, String> attributes,
                                  List<VariantResponse> variants, long minPrice, long maxPrice, String status,
                                  Instant createdAt, Instant updatedAt) {
        public static ProductResponse from(Product p) {
            return new ProductResponse(p.getId(), p.getName(), p.getSlug(), p.getDescription(), p.getBrand(),
                    p.getCategoryId(), p.getImages(), p.getAttributes(),
                    p.getVariants().stream().map(VariantResponse::from).toList(), p.minPrice(), p.maxPrice(),
                    p.getStatus().name(), p.getCreatedAt(), p.getUpdatedAt());
        }
    }

    /** Lightweight card used by listing / search pages. */
    public record ProductSummary(String id, String name, String slug, String brand, String categoryId,
                                 long minPrice, long maxPrice, String image) {
        public static ProductSummary from(ProductDocument d) {
            return new ProductSummary(d.getId(), d.getName(), d.getSlug(), d.getBrand(), d.getCategoryId(),
                    d.getMinPrice(), d.getMaxPrice(), d.getImage());
        }

        public static ProductSummary from(Product p) {
            return from(ProductDocument.from(p));
        }
    }

    public record CategoryRequest(@NotBlank String name, @NotBlank String slug, String parentId) {
    }

    public record CategoryResponse(String id, String name, String slug, String parentId) {
        public static CategoryResponse from(Category c) {
            return new CategoryResponse(c.getId(), c.getName(), c.getSlug(), c.getParentId());
        }
    }
}
