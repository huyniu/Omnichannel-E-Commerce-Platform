package com.omni.product.repository;

import com.omni.product.domain.Category;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface CategoryRepository extends MongoRepository<Category, String> {

    boolean existsBySlug(String slug);
}
