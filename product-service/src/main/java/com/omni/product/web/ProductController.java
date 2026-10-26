package com.omni.product.web;

import com.omni.common.api.ApiResponse;
import com.omni.common.api.PageResponse;
import com.omni.common.exception.BusinessException;
import com.omni.common.web.AuthUser;
import com.omni.common.web.CurrentUser;
import com.omni.product.domain.Category;
import com.omni.product.repository.CategoryRepository;
import com.omni.product.search.ProductSearchService.SearchCriteria;
import com.omni.product.service.ProductService;
import com.omni.product.web.Dtos.CategoryRequest;
import com.omni.product.web.Dtos.CategoryResponse;
import com.omni.product.web.Dtos.ProductRequest;
import com.omni.product.web.Dtos.ProductResponse;
import com.omni.product.web.Dtos.ProductSummary;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@Tag(name = "Product Catalog")
public class ProductController {

    private final ProductService productService;
    private final CategoryRepository categories;

    @GetMapping("/api/products")
    public ApiResponse<PageResponse<ProductSummary>> search(@RequestParam(required = false) String q,
                                                            @RequestParam(required = false) String categoryId,
                                                            @RequestParam(required = false) String brand,
                                                            @RequestParam(required = false) Long minPrice,
                                                            @RequestParam(required = false) Long maxPrice,
                                                            @RequestParam(defaultValue = "0") int page,
                                                            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(productService.search(new SearchCriteria(q, categoryId, brand, minPrice, maxPrice),
                Math.max(page, 0), Math.min(Math.max(size, 1), 100)));
    }

    @GetMapping("/api/products/{idOrSlug}")
    public ApiResponse<ProductResponse> get(@PathVariable String idOrSlug) {
        return ApiResponse.ok(productService.get(idOrSlug));
    }

    @GetMapping("/api/products/sku/{sku}")
    public ApiResponse<ProductResponse> getBySku(@PathVariable String sku) {
        return ApiResponse.ok(productService.getBySku(sku));
    }

    @PostMapping("/api/products")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ProductResponse> create(@Parameter(hidden = true) @AuthUser CurrentUser user,
                                               @Valid @RequestBody ProductRequest req) {
        user.requireStaff();
        return ApiResponse.ok(productService.create(req));
    }

    @PutMapping("/api/products/{id}")
    public ApiResponse<ProductResponse> update(@Parameter(hidden = true) @AuthUser CurrentUser user,
                                               @PathVariable String id, @Valid @RequestBody ProductRequest req) {
        user.requireStaff();
        return ApiResponse.ok(productService.update(id, req));
    }

    @DeleteMapping("/api/products/{id}")
    public ApiResponse<Void> deactivate(@Parameter(hidden = true) @AuthUser CurrentUser user, @PathVariable String id) {
        user.requireStaff();
        productService.deactivate(id);
        return ApiResponse.ok("Product deactivated", null);
    }

    @PostMapping("/api/products/reindex")
    public ApiResponse<Map<String, Long>> reindex(@Parameter(hidden = true) @AuthUser CurrentUser user) {
        user.requireAdmin();
        return ApiResponse.ok(Map.of("indexed", productService.reindex()));
    }

    @GetMapping("/api/categories")
    public ApiResponse<List<CategoryResponse>> categories() {
        return ApiResponse.ok(categories.findAll().stream().map(CategoryResponse::from).toList());
    }

    @PostMapping("/api/categories")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CategoryResponse> createCategory(@Parameter(hidden = true) @AuthUser CurrentUser user,
                                                        @Valid @RequestBody CategoryRequest req) {
        user.requireStaff();
        if (categories.existsBySlug(req.slug())) {
            throw BusinessException.conflict("DUPLICATE", "Category slug already exists");
        }
        Category c = new Category();
        c.setName(req.name());
        c.setSlug(req.slug());
        c.setParentId(req.parentId());
        return ApiResponse.ok(CategoryResponse.from(categories.save(c)));
    }
}
