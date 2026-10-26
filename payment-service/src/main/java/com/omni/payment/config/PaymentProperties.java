package com.omni.payment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param publicBaseUrl public URL of the API (through the gateway), used to build callback links
 */
@ConfigurationProperties(prefix = "payment")
public record PaymentProperties(String publicBaseUrl, Mock mock, VnPay vnpay) {

    /** Simulated gateway signing its webhooks with HMAC-SHA256, for local demos and load tests. */
    public record Mock(boolean enabled, String secret) {
    }

    /** VNPay sandbox: register at https://sandbox.vnpayment.vn to obtain tmnCode / hashSecret. */
    public record VnPay(String tmnCode, String hashSecret, String payUrl, String returnUrl, int expireMinutes) {
        public boolean configured() {
            return tmnCode != null && !tmnCode.isBlank() && hashSecret != null && !hashSecret.isBlank();
        }
    }
}
