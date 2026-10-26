package com.omni.product.search;

import co.elastic.clients.json.JsonData;
import com.omni.product.domain.Product;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * Full-text search on Elasticsearch. Every write is best-effort: if Elasticsearch is down the
 * catalog keeps working on MongoDB and the index can be rebuilt later.
 */
@Service
@RequiredArgsConstructor
public class ProductSearchService {

    private static final Logger log = LoggerFactory.getLogger(ProductSearchService.class);

    private final ElasticsearchOperations operations;
    private final ProductSearchRepository repository;

    public record SearchCriteria(String keyword, String categoryId, String brand, Long minPrice, Long maxPrice) {
    }

    public record SearchResult(List<ProductDocument> items, long total) {
    }

    /** Set once the index exists with its explicit mapping; until then writes would auto-create a wrong one. */
    private volatile boolean indexReady;

    @EventListener(ApplicationReadyEvent.class)
    public void ensureIndex() {
        try {
            IndexOperations index = operations.indexOps(ProductDocument.class);
            if (!index.exists()) {
                index.createWithMapping();
                log.info("Created Elasticsearch index 'products'");
            }
            indexReady = true;
        } catch (Exception e) {
            log.warn("Elasticsearch not reachable, search will fall back to MongoDB: {}", e.getMessage());
        }
    }

    public void index(Product product) {
        if (!indexReady) {
            ensureIndex();
        }
        try {
            repository.save(ProductDocument.from(product));
        } catch (Exception e) {
            log.warn("Failed to index product {}: {}", product.getId(), e.getMessage());
        }
    }

    public void delete(String productId) {
        try {
            repository.deleteById(productId);
        } catch (Exception e) {
            log.warn("Failed to remove product {} from index: {}", productId, e.getMessage());
        }
    }

    public long reindex(List<Product> products) {
        IndexOperations index = operations.indexOps(ProductDocument.class);
        if (index.exists()) {
            index.delete();
        }
        index.createWithMapping();
        indexReady = true;
        repository.saveAll(products.stream().map(ProductDocument::from).toList());
        return products.size();
    }

    public SearchResult search(SearchCriteria c, Pageable pageable) {
        NativeQuery query = NativeQuery.builder()
                .withQuery(q -> q.bool(b -> {
                    b.filter(f -> f.term(t -> t.field("status").value(Product.Status.ACTIVE.name())));
                    if (StringUtils.hasText(c.keyword())) {
                        b.must(m -> m.multiMatch(mm -> mm.query(c.keyword())
                                .fields("name^3", "brand^2", "description", "skus")
                                .fuzziness("AUTO")));
                    }
                    if (StringUtils.hasText(c.categoryId())) {
                        b.filter(f -> f.term(t -> t.field("categoryId").value(c.categoryId())));
                    }
                    if (StringUtils.hasText(c.brand())) {
                        b.filter(f -> f.term(t -> t.field("brand").value(c.brand())));
                    }
                    if (c.minPrice() != null) {
                        b.filter(f -> f.range(r -> r.field("maxPrice").gte(JsonData.of(c.minPrice()))));
                    }
                    if (c.maxPrice() != null) {
                        b.filter(f -> f.range(r -> r.field("minPrice").lte(JsonData.of(c.maxPrice()))));
                    }
                    return b;
                }))
                .withPageable(PageRequest.of(pageable.getPageNumber(), pageable.getPageSize()))
                .build();
        SearchHits<ProductDocument> hits = operations.search(query, ProductDocument.class);
        return new SearchResult(hits.getSearchHits().stream().map(SearchHit::getContent).toList(), hits.getTotalHits());
    }
}
