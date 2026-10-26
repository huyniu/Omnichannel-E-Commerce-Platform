package com.omni.product.config;

import com.omni.product.domain.Category;
import com.omni.product.repository.CategoryRepository;
import com.omni.product.service.ProductService;
import com.omni.product.web.Dtos.ProductRequest;
import com.omni.product.web.Dtos.VariantRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Seeds a small demo catalog on an empty database. The SKUs match the initial stock inserted by
 * the Order &amp; Inventory Service migration (V2__seed_inventory.sql).
 */
@Component
@ConditionalOnProperty(name = "product.seed-sample-data", havingValue = "true")
@RequiredArgsConstructor
public class SampleDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SampleDataSeeder.class);

    private final CategoryRepository categories;
    private final ProductService productService;

    @Override
    public void run(ApplicationArguments args) {
        if (categories.count() > 0) {
            return;
        }
        String phones = category("Điện thoại", "dien-thoai");
        String laptops = category("Laptop", "laptop");
        String accessories = category("Phụ kiện", "phu-kien");
        String fashion = category("Thời trang", "thoi-trang");

        product("iPhone 15", "Apple", phones, "Chip A16 Bionic, Dynamic Island, camera 48MP.",
                Map.of("screen", "6.1 inch OLED", "chip", "A16 Bionic"),
                List.of(variant("IP15-128-BLK", "128GB - Đen", 19_990_000, Map.of("storage", "128GB", "color", "Đen")),
                        variant("IP15-256-BLK", "256GB - Đen", 22_990_000, Map.of("storage", "256GB", "color", "Đen"))));
        product("Samsung Galaxy S24", "Samsung", phones, "Galaxy AI, màn hình Dynamic AMOLED 2X 120Hz.",
                Map.of("screen", "6.2 inch AMOLED", "chip", "Exynos 2400"),
                List.of(variant("SS-S24-256-GRY", "256GB - Xám", 18_990_000, Map.of("storage", "256GB", "color", "Xám"))));
        product("MacBook Air M3 13 inch", "Apple", laptops, "Mỏng nhẹ, pin 18 giờ, chip Apple M3.",
                Map.of("cpu", "Apple M3 8-core", "ram", "8GB"),
                List.of(variant("MBA-M3-13-256", "8GB/256GB", 27_490_000, Map.of("storage", "256GB"))));
        product("AirPods Pro 2", "Apple", accessories, "Chống ồn chủ động, cổng USB-C.",
                Map.of("connectivity", "Bluetooth 5.3"),
                List.of(variant("AIRPODS-PRO2", "USB-C", 5_990_000, Map.of())));
        product("Áo thun Basic Cotton", "Omni Basic", fashion, "100% cotton, form regular.",
                Map.of("material", "Cotton", "fit", "Regular"),
                List.of(variant("TSHIRT-BASIC-M-WHT", "Size M - Trắng", 199_000, Map.of("size", "M", "color", "Trắng")),
                        variant("TSHIRT-BASIC-L-WHT", "Size L - Trắng", 199_000, Map.of("size", "L", "color", "Trắng"))));
        product("Tai nghe Bluetooth Flash Sale", "Omni Audio", accessories,
                "Sản phẩm Flash Sale số lượng giới hạn, dùng cho kịch bản kiểm thử tải.",
                Map.of("battery", "20h"),
                List.of(variant("FLASH-001", "Mặc định", 99_000, Map.of())));
        log.info("Seeded demo catalog");
    }

    private String category(String name, String slug) {
        Category c = new Category();
        c.setName(name);
        c.setSlug(slug);
        return categories.save(c).getId();
    }

    private void product(String name, String brand, String categoryId, String description,
                         Map<String, String> attributes, List<VariantRequest> variants) {
        productService.create(new ProductRequest(name, null, description, brand, categoryId,
                List.of("https://placehold.co/600x600?text=" + name.replace(' ', '+')), attributes, variants, null));
    }

    private static VariantRequest variant(String sku, String name, long price, Map<String, String> options) {
        return new VariantRequest(sku, name, price, null, options, null, true);
    }
}
