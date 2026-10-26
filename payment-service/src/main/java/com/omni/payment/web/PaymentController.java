package com.omni.payment.web;

import com.omni.common.api.ApiResponse;
import com.omni.common.exception.BusinessException;
import com.omni.common.web.AuthUser;
import com.omni.common.web.CurrentUser;
import com.omni.payment.config.PaymentProperties;
import com.omni.payment.domain.Payment;
import com.omni.payment.provider.GatewayResult;
import com.omni.payment.provider.MockGateway;
import com.omni.payment.provider.VnPayGateway;
import com.omni.payment.service.PaymentService;
import com.omni.payment.service.PaymentService.CallbackOutcome;
import com.omni.payment.web.Dtos.CheckoutResponse;
import com.omni.payment.web.Dtos.MockWebhook;
import com.omni.payment.web.Dtos.PaymentResponse;
import com.omni.payment.web.Dtos.ReconciliationLine;
import com.omni.payment.web.Dtos.TransactionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@Tag(name = "Payments")
public class PaymentController {

    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");

    private final PaymentService paymentService;
    private final MockGateway mockGateway;
    private final VnPayGateway vnPayGateway;
    private final PaymentProperties props;

    @GetMapping("/orders/{orderId}")
    public ApiResponse<PaymentResponse> get(@Parameter(hidden = true) @AuthUser CurrentUser user, @PathVariable UUID orderId) {
        return ApiResponse.ok(paymentService.getForOrder(user, orderId));
    }

    @PostMapping("/orders/{orderId}/checkout")
    @Operation(summary = "Get the gateway URL to pay an order (VNPay sandbox or mock gateway)")
    public ApiResponse<CheckoutResponse> checkout(@Parameter(hidden = true) @AuthUser CurrentUser user,
                                                  @PathVariable UUID orderId, HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        String ip = forwarded != null ? forwarded.split(",")[0].trim() : request.getRemoteAddr();
        return ApiResponse.ok(paymentService.checkout(user, orderId, ip));
    }

    // ---------- Mock gateway (demo / load test) ----------

    @PostMapping("/mock/{txnRef}/simulate")
    @Operation(summary = "Simulate the customer paying on the mock gateway",
            description = "Builds a signed webhook exactly like the gateway would and delivers it. result=SUCCESS|FAILED")
    public ApiResponse<Map<String, String>> simulate(@Parameter(hidden = true) @AuthUser CurrentUser user,
                                                     @PathVariable String txnRef,
                                                     @RequestParam(defaultValue = "SUCCESS") String result) {
        requireMockEnabled();
        Payment p = paymentService.requireByTxnRef(txnRef);
        if (!p.getUserId().equals(user.id()) && !user.isStaff()) {
            throw BusinessException.notFound("Payment");
        }
        String status = "SUCCESS".equalsIgnoreCase(result) ? "SUCCESS" : "FAILED";
        String providerTxnId = "MOCK-" + UUID.randomUUID().toString().substring(0, 8);
        MockWebhook webhook = new MockWebhook(txnRef, status, p.getAmount(), providerTxnId,
                mockGateway.sign(txnRef, status, p.getAmount()));
        return mockWebhook(webhook);
    }

    @PostMapping("/webhook/mock")
    @Operation(summary = "Webhook called by the mock gateway (HMAC-SHA256 signed)")
    public ApiResponse<Map<String, String>> mockWebhook(@Valid @RequestBody MockWebhook body) {
        requireMockEnabled();
        if (!mockGateway.verify(body.txnRef(), body.status(), body.amount(), body.signature())) {
            throw BusinessException.unauthorized("Invalid webhook signature");
        }
        boolean success = "SUCCESS".equals(body.status());
        CallbackOutcome outcome = paymentService.applyGatewayResult(new GatewayResult(body.txnRef(), success,
                body.amount(), body.providerTxnId(), success ? null : "Declined by mock gateway", body.toString()));
        if (outcome == CallbackOutcome.NOT_FOUND) {
            throw BusinessException.notFound("Payment");
        }
        if (outcome == CallbackOutcome.AMOUNT_MISMATCH) {
            throw BusinessException.badRequest("AMOUNT_MISMATCH", "Amount does not match");
        }
        return ApiResponse.ok(Map.of("outcome", outcome.name()));
    }

    private void requireMockEnabled() {
        if (!props.mock().enabled()) {
            throw BusinessException.notFound("Mock gateway");
        }
    }

    // ---------- VNPay ----------

    /**
     * Instant Payment Notification, server-to-server from VNPay. The response format is dictated by VNPay
     * (RspCode/Message), so it intentionally does not use the ApiResponse envelope.
     */
    @GetMapping("/vnpay/ipn")
    public Map<String, String> vnpayIpn(@RequestParam Map<String, String> params) {
        GatewayResult result = vnPayGateway.verify(params);
        if (result == null) {
            return Map.of("RspCode", "97", "Message", "Invalid Checksum");
        }
        return switch (paymentService.applyGatewayResult(result)) {
            case APPLIED -> Map.of("RspCode", "00", "Message", "Confirm Success");
            case ALREADY_PROCESSED -> Map.of("RspCode", "02", "Message", "Order already confirmed");
            case NOT_FOUND -> Map.of("RspCode", "01", "Message", "Order not found");
            case AMOUNT_MISMATCH -> Map.of("RspCode", "04", "Message", "Invalid amount");
        };
    }

    /** Browser redirect after paying on VNPay: only displays the result, the IPN is the source of truth. */
    @GetMapping("/vnpay/return")
    public ApiResponse<Map<String, Object>> vnpayReturn(@RequestParam Map<String, String> params) {
        GatewayResult result = vnPayGateway.verify(params);
        if (result == null) {
            throw BusinessException.badRequest("INVALID_SIGNATURE", "Invalid VNPay signature");
        }
        return ApiResponse.ok(Map.of("txnRef", result.txnRef(), "success", result.success(), "amount", result.amount()));
    }

    // ---------- Back office ----------

    @GetMapping("/admin/reconciliation")
    @Operation(summary = "Daily reconciliation: count and total amount per method and status")
    public ApiResponse<List<ReconciliationLine>> reconcile(@Parameter(hidden = true) @AuthUser CurrentUser user,
                                                           @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                           @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        user.requireStaff();
        return ApiResponse.ok(paymentService.reconcile(from.atStartOfDay(VN).toInstant(), to.plusDays(1).atStartOfDay(VN).toInstant())
                .stream().map(r -> new ReconciliationLine(r.getMethod(), r.getStatus(), r.getCount(), r.getTotal())).toList());
    }

    @GetMapping("/admin/{paymentId}/transactions")
    public ApiResponse<List<TransactionResponse>> transactions(@Parameter(hidden = true) @AuthUser CurrentUser user,
                                                               @PathVariable UUID paymentId) {
        user.requireStaff();
        return ApiResponse.ok(paymentService.history(paymentId).stream().map(TransactionResponse::from).toList());
    }
}
