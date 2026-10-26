package com.omni.product.search;

import com.omni.product.domain.Product;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.Setting;

import java.util.List;

/**
 * Denormalized, search-optimized projection of {@link Product}. MongoDB stays the source of truth;
 * this index can always be rebuilt with POST /api/products/reindex.
 */
@Document(indexName = "products", createIndex = false)
@Setting(settingPath = "elasticsearch/product-settings.json")
@Getter
@Setter
@NoArgsConstructor
public class ProductDocument {

    @Id
    private String id;

    @Field(type = FieldType.Text, analyzer = "vi_folding")
    private String name;

    @Field(type = FieldType.Keyword)
    private String slug;

    @Field(type = FieldType.Text, analyzer = "vi_folding")
    private String description;

    @Field(type = FieldType.Keyword)
    private String brand;

    @Field(type = FieldType.Keyword)
    private String categoryId;

    @Field(type = FieldType.Keyword)
    private List<String> skus;

    @Field(type = FieldType.Long)
    private long minPrice;

    @Field(type = FieldType.Long)
    private long maxPrice;

    @Field(type = FieldType.Keyword, index = false)
    private String image;

    @Field(type = FieldType.Keyword)
    private String status;

    public static ProductDocument from(Product p) {
        ProductDocument d = new ProductDocument();
        d.setId(p.getId());
        d.setName(p.getName());
        d.setSlug(p.getSlug());
        d.setDescription(p.getDescription());
        d.setBrand(p.getBrand());
        d.setCategoryId(p.getCategoryId());
        d.setSkus(p.getVariants().stream().map(Product.Variant::getSku).toList());
        d.setMinPrice(p.minPrice());
        d.setMaxPrice(p.maxPrice());
        d.setImage(p.getImages().isEmpty() ? null : p.getImages().get(0));
        d.setStatus(p.getStatus().name());
        return d;
    }
}
