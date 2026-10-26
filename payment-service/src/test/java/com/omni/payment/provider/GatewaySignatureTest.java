package com.omni.payment.provider;

import com.omni.payment.config.PaymentProperties;
import com.omni.payment.domain.Payment;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringJoiner;
import java.util.TreeMap;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class GatewaySignatureTest {

    private static final String HASH_SECRET = "TESTHASHSECRET";

    private final PaymentProperties props = new PaymentProperties("http://localhost:8080",
            new PaymentProperties.Mock(true, "secret"),
            new PaymentProperties.VnPay("TESTCODE", HASH_SECRET, "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html",
                    "http://localhost:8080/api/payments/vnpay/return", 15));

    @Test
    void mockSignatureRoundTrip() {
        MockGateway gateway = new MockGateway(props);
        String sig = gateway.sign("OD1", "SUCCESS", 1000);
        assertThat(gateway.verify("OD1", "SUCCESS", 1000, sig)).isTrue();
        assertThat(gateway.verify("OD1", "SUCCESS", 999, sig)).as("tampered amount").isFalse();
    }

    @Test
    void checkoutUrlSignatureMatchesItsParameters() {
        VnPayGateway gateway = new VnPayGateway(props);
        Map<String, String> params = parse(URI.create(gateway.checkoutUrl(payment(), "1.2.3.4")).getRawQuery());

        assertThat(params).containsEntry("vnp_Amount", "19900000").containsEntry("vnp_TxnRef", "OD261026ABCDEF");
        assertThat(params.get("vnp_SecureHash")).isEqualTo(sign(params));
    }

    @Test
    void ipnIsVerifiedAndTamperingDetected() {
        VnPayGateway gateway = new VnPayGateway(props);
        Map<String, String> ipn = new LinkedHashMap<>();
        ipn.put("vnp_TxnRef", "OD261026ABCDEF");
        ipn.put("vnp_Amount", "19900000");
        ipn.put("vnp_ResponseCode", "00");
        ipn.put("vnp_TransactionStatus", "00");
        ipn.put("vnp_TransactionNo", "14000000");
        ipn.put("vnp_OrderInfo", "Thanh toan don hang OD261026ABCDEF");
        ipn.put("vnp_SecureHash", sign(ipn));

        GatewayResult result = gateway.verify(ipn);
        assertThat(result).isNotNull();
        assertThat(result.success()).isTrue();
        assertThat(result.amount()).isEqualTo(199_000);

        ipn.put("vnp_Amount", "100");
        assertThat(gateway.verify(ipn)).as("tampered amount").isNull();
    }

    private static Payment payment() {
        Payment p = new Payment();
        p.setId(UUID.randomUUID());
        p.setOrderCode("OD261026ABCDEF");
        p.setTxnRef("OD261026ABCDEF");
        p.setAmount(199_000);
        return p;
    }

    /** Independent re-implementation of the VNPay signing rule, used as the test oracle. */
    private static String sign(Map<String, String> params) {
        Map<String, String> sorted = new TreeMap<>(params);
        sorted.remove("vnp_SecureHash");
        StringJoiner joiner = new StringJoiner("&");
        sorted.forEach((k, v) -> joiner.add(k + "=" + URLEncoder.encode(v, StandardCharsets.US_ASCII)));
        return Hmac.hex("HmacSHA512", HASH_SECRET, joiner.toString());
    }

    private static Map<String, String> parse(String query) {
        Map<String, String> map = new LinkedHashMap<>();
        for (String pair : query.split("&")) {
            int i = pair.indexOf('=');
            map.put(pair.substring(0, i), URLDecoder.decode(pair.substring(i + 1), StandardCharsets.US_ASCII));
        }
        return map;
    }
}
