package com.omni.payment.provider;

import com.omni.common.exception.BusinessException;
import com.omni.payment.config.PaymentProperties;
import com.omni.payment.domain.Payment;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.StringJoiner;
import java.util.TreeMap;

/**
 * VNPay payment URL (API v2.1.0) and IPN signature verification, following the VNPay integration guide:
 * parameters sorted by name, URL-encoded, signed with HMAC-SHA512 using the merchant hash secret.
 */
@Component
public class VnPayGateway {

    public static final String PROVIDER = "VNPAY";
    private static final DateTimeFormatter VNP_DATE = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");

    private final PaymentProperties.VnPay props;

    public VnPayGateway(PaymentProperties properties) {
        this.props = properties.vnpay();
    }

    public String checkoutUrl(Payment payment, String clientIp) {
        if (!props.configured()) {
            throw new BusinessException("VNPAY_NOT_CONFIGURED", "VNPay is not configured on this server", 503);
        }
        ZonedDateTime now = ZonedDateTime.now(VN);
        Map<String, String> params = new TreeMap<>();
        params.put("vnp_Version", "2.1.0");
        params.put("vnp_Command", "pay");
        params.put("vnp_TmnCode", props.tmnCode());
        params.put("vnp_Amount", String.valueOf(payment.getAmount() * 100));
        params.put("vnp_CurrCode", "VND");
        params.put("vnp_TxnRef", payment.getTxnRef());
        params.put("vnp_OrderInfo", "Thanh toan don hang " + payment.getOrderCode());
        params.put("vnp_OrderType", "other");
        params.put("vnp_Locale", "vn");
        params.put("vnp_ReturnUrl", props.returnUrl());
        params.put("vnp_IpAddr", clientIp == null ? "127.0.0.1" : clientIp);
        params.put("vnp_CreateDate", now.format(VNP_DATE));
        params.put("vnp_ExpireDate", now.plusMinutes(props.expireMinutes()).format(VNP_DATE));

        String query = canonical(params);
        String signature = Hmac.hex("HmacSHA512", props.hashSecret(), query);
        return props.payUrl() + "?" + query + "&vnp_SecureHash=" + signature;
    }

    /** Verifies an IPN / return callback and normalizes it. Returns null when the signature is invalid. */
    public GatewayResult verify(Map<String, String> callback) {
        if (!props.configured()) {
            return null;
        }
        Map<String, String> fields = new TreeMap<>(callback);
        String received = fields.remove("vnp_SecureHash");
        fields.remove("vnp_SecureHashType");
        fields.values().removeIf(v -> v == null || v.isEmpty());
        String expected = Hmac.hex("HmacSHA512", props.hashSecret(), canonical(fields));
        if (!Hmac.equalsIgnoreCase(expected, received)) {
            return null;
        }
        boolean success = "00".equals(fields.get("vnp_ResponseCode")) && "00".equals(fields.get("vnp_TransactionStatus"));
        long amount = Long.parseLong(fields.getOrDefault("vnp_Amount", "0")) / 100;
        return new GatewayResult(fields.get("vnp_TxnRef"), success, amount, fields.get("vnp_TransactionNo"),
                success ? null : "VNPay response code " + fields.get("vnp_ResponseCode"), fields.toString());
    }

    private static String canonical(Map<String, String> sorted) {
        StringJoiner joiner = new StringJoiner("&");
        sorted.forEach((k, v) -> joiner.add(URLEncoder.encode(k, StandardCharsets.US_ASCII) + "="
                + URLEncoder.encode(v, StandardCharsets.US_ASCII)));
        return joiner.toString();
    }
}
