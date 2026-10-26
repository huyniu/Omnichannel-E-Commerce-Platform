package com.omni.order.web;

import com.omni.common.api.ApiResponse;
import com.omni.common.web.AuthUser;
import com.omni.common.web.CurrentUser;
import com.omni.order.domain.InventoryItem;
import com.omni.order.inventory.InventoryLockService;
import com.omni.order.inventory.InventoryService;
import com.omni.order.web.Dtos.AdjustStockRequest;
import com.omni.order.web.Dtos.InventoryResponse;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
@Tag(name = "Inventory")
public class InventoryController {

    private final InventoryService inventory;
    private final InventoryLockService locks;

    /** Public: lets product pages show "in stock / sold out". */
    @GetMapping
    public ApiResponse<List<InventoryResponse>> find(@RequestParam List<String> skus) {
        return ApiResponse.ok(inventory.find(skus).stream().map(InventoryResponse::from).toList());
    }

    @GetMapping("/{sku}")
    public ApiResponse<InventoryResponse> get(@PathVariable String sku) {
        return ApiResponse.ok(InventoryResponse.from(inventory.get(sku)));
    }

    /** Restock (positive delta) or write-off (negative delta). Runs under the same SKU lock as orders. */
    @PostMapping("/{sku}/adjust")
    public ApiResponse<InventoryResponse> adjust(@Parameter(hidden = true) @AuthUser CurrentUser user,
                                                 @PathVariable String sku, @Valid @RequestBody AdjustStockRequest req) {
        user.requireStaff();
        InventoryItem item = locks.withSkuLocks(List.of(sku), () -> {
            InventoryItem updated = inventory.adjust(sku, req.delta());
            locks.refreshSoldOutFlags(Map.of(sku, updated.getAvailable()));
            return updated;
        });
        return ApiResponse.ok(InventoryResponse.from(item));
    }
}
