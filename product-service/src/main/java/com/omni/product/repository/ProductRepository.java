package com.omni.product.repository;

import com.omni.product.domain.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductRepository extends MongoRepository<Product, String> {

    Optional<Product> findBySlug(String slug);

    @Query("{ 'variants.sku': { $in: ?0 } }")
    List<Product> findByVariantSkus(Collection<String> skus);

    @Query("{ 'variants.sku': ?0 }")
    Optional<Product> findByVariantSku(String sku);

    boolean existsBySlug(String slug);

    /** Fallback search used when Elasticsearch is unavailable. */
    @Query("{ $and: [ { 'status': 'ACTIVE' }, { $or: [ { 'name': { $regex: ?0, $options: 'i' } }, { 'brand': { $regex: ?0, $options: 'i' } } ] } ] }")
    Page<Product> searchActiveByKeyword(String regex, Pageable pageable);

    Page<Product> findByStatus(Product.Status status, Pageable pageable);
}
