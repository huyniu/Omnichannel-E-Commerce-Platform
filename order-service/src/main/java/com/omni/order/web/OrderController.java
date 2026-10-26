package com.omni.order.web;

import com.omni.common.api.ApiResponse;
import com.omni.common.api.PageResponse;
import com.omni.common.web.AuthUser;
import com.omni.common.web.CurrentUser;
import com.omni.order.domain.OrderStatus;
import com.omni.order.service.OrderService;
import com.omni.order.web.Dtos.CancelRequest;
import com.omni.order.web.Dtos.OrderResponse;
import com.omni.order.web.Dtos.OrderSummary;
import com.omni.order.web.Dtos.PlaceOrderRequest;
import com.omni.order.web.Dtos.StatusUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Tag(name = "Orders")
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Place an order",
            description = "Reserves stock atomically. Send an Idempotency-Key header to make retries safe.")
    public ApiResponse<OrderResponse> place(@Parameter(hidden = true) @AuthUser CurrentUser user,
                                            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                            @Valid @RequestBody PlaceOrderRequest req) {
        return ApiResponse.ok("Order placed", orderService.place(user, req, idempotencyKey));
    }

    @GetMapping
    public ApiResponse<PageResponse<OrderSummary>> mine(@Parameter(hidden = true) @AuthUser CurrentUser user,
                                                        @RequestParam(defaultValue = "0") int page,
                                                        @RequestParam(defaultValue = "20") int size) {
        var result = orderService.listMine(user.id(), PageRequest.of(page, Math.min(size, 100), Sort.by("createdAt").descending()));
        return ApiResponse.ok(PageResponse.of(result, OrderSummary::from));
    }

    @GetMapping("/{id}")
    public ApiResponse<OrderResponse> get(@Parameter(hidden = true) @AuthUser CurrentUser user, @PathVariable UUID id) {
        return ApiResponse.ok(orderService.get(user, id));
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<OrderResponse> cancel(@Parameter(hidden = true) @AuthUser CurrentUser user, @PathVariable UUID id,
                                             @Valid @RequestBody(required = false) CancelRequest req) {
        return ApiResponse.ok("Order cancelled", orderService.cancelByCustomer(user, id, req == null ? null : req.reason()));
    }

    // ---- Back office ----

    @GetMapping("/admin")
    public ApiResponse<PageResponse<OrderSummary>> all(@Parameter(hidden = true) @AuthUser CurrentUser user,
                                                       @RequestParam(required = false) OrderStatus status,
                                                       @RequestParam(defaultValue = "0") int page,
                                                       @RequestParam(defaultValue = "20") int size) {
        user.requireStaff();
        var result = orderService.listAll(status, PageRequest.of(page, Math.min(size, 100), Sort.by("createdAt").descending()));
        return ApiResponse.ok(PageResponse.of(result, OrderSummary::from));
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<OrderResponse> updateStatus(@Parameter(hidden = true) @AuthUser CurrentUser user,
                                                   @PathVariable UUID id, @Valid @RequestBody StatusUpdateRequest req) {
        user.requireStaff();
        return ApiResponse.ok(orderService.updateStatus(id, req.status(), req.reason(), user));
    }
}
