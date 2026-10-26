package com.omni.product.service;

import com.omni.common.api.PageResponse;
import com.omni.common.exception.BusinessException;
import com.omni.product.domain.Product;
import com.omni.product.repository.CategoryRepository;
import com.omni.product.repository.ProductRepository;
import com.omni.product.search.ProductSearchService;
import com.omni.product.search.ProductSearchService.SearchCriteria;
import com.omni.product.web.Dtos.ProductRequest;
import com.omni.product.web.Dtos.ProductResponse;
import com.omni.product.web.Dtos.ProductSummary;
import com.omni.product.web.Dtos.VariantRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductService.class);

    private final ProductRepository products;
    private final CategoryRepository categories;
    private final ProductSearchService searchService;

    public ProductResponse get(String idOrSlug) {
        Product p = products.findById(idOrSlug).or(() -> products.findBySlug(idOrSlug))
                .orElseThrow(() -> BusinessException.notFound("Product"));
        return ProductResponse.from(p);
    }

    public ProductResponse getBySku(String sku) {
        return ProductResponse.from(products.findByVariantSku(sku).orElseThrow(() -> BusinessException.notFound("SKU")));
    }

    public List<Product> findBySkus(Collection<String> skus) {
        return products.findByVariantSkus(skus);
    }

    /**
     * Elasticsearch first; MongoDB regex as a degraded fallback so the shop stays browsable
     * when the search cluster is down.
     */
    public PageResponse<ProductSummary> search(SearchCriteria criteria, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size);
        try {
            var result = searchService.search(criteria, pageable);
            int totalPages = (int) Math.ceil((double) result.total() / size);
            return new PageResponse<>(result.items().stream().map(ProductSummary::from).toList(), page, size,
                    result.total(), totalPages);
        } catch (Exception e) {
            log.warn("Elasticsearch search failed, falling back to MongoDB: {}", e.getMessage());
            PageRequest sorted = PageRequest.of(page, size, Sort.by("createdAt").descending());
            Page<Product> fallback = StringUtils.hasText(criteria.keyword())
                    ? products.searchActiveByKeyword(Pattern.quote(criteria.keyword()), sorted)
                    : products.findByStatus(Product.Status.ACTIVE, sorted);
            return PageResponse.of(fallback, ProductSummary::from);
        }
    }

    public ProductResponse create(ProductRequest req) {
        Product p = new Product();
        apply(p, req);
        return ProductResponse.from(saveAndIndex(p));
    }

    public ProductResponse update(String id, ProductRequest req) {
        Product p = products.findById(id).orElseThrow(() -> BusinessException.notFound("Product"));
        apply(p, req);
        return ProductResponse.from(saveAndIndex(p));
    }

    /** Soft delete: past orders keep referring to the SKU snapshot, so documents are never removed. */
    public void deactivate(String id) {
        Product p = products.findById(id).orElseThrow(() -> BusinessException.notFound("Product"));
        p.setStatus(Product.Status.INACTIVE);
        saveAndIndex(p);
    }

    public long reindex() {
        return searchService.reindex(products.findAll());
    }

    private Product saveAndIndex(Product p) {
        try {
            Product saved = products.save(p);
            searchService.index(saved);
            return saved;
        } catch (DuplicateKeyException e) {
            throw BusinessException.conflict("DUPLICATE", "Slug or SKU already exists");
        }
    }

    private void apply(Product p, ProductRequest req) {
        if (!categories.existsById(req.categoryId())) {
            throw BusinessException.badRequest("INVALID_CATEGORY", "Category does not exist");
        }
        Set<String> seen = new HashSet<>();
        for (VariantRequest v : req.variants()) {
            if (!seen.add(v.sku())) {
                throw BusinessException.badRequest("DUPLICATE_SKU", "Duplicate SKU in request: " + v.sku());
            }
        }
        p.setName(req.name());
        p.setSlug(StringUtils.hasText(req.slug()) ? req.slug() : slugify(req.name()));
        p.setDescription(req.description());
        p.setBrand(req.brand());
        p.setCategoryId(req.categoryId());
        p.setImages(req.images() == null ? new ArrayList<>() : new ArrayList<>(req.images()));
        p.setAttributes(req.attributes() == null ? new LinkedHashMap<>() : new LinkedHashMap<>(req.attributes()));
        p.setVariants(req.variants().stream().map(v -> {
            Product.Variant variant = new Product.Variant();
            variant.setSku(v.sku());
            variant.setName(v.name());
            variant.setPrice(v.price());
            variant.setCompareAtPrice(v.compareAtPrice());
            variant.setOptions(v.options() == null ? new LinkedHashMap<>() : new LinkedHashMap<>(v.options()));
            variant.setImage(v.image());
            variant.setActive(v.active() == null || v.active());
            return variant;
        }).collect(ArrayList::new, ArrayList::add, ArrayList::addAll));
        if (req.status() != null) {
            p.setStatus(req.status());
        }
    }

    static String slugify(String input) {
        String noAccents = Normalizer.normalize(input.replace('đ', 'd').replace('Đ', 'D'), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return noAccents.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }
}
