package com.omni.payment.provider;

import com.omni.payment.config.PaymentProperties;
import com.omni.payment.domain.Payment;
import org.springframework.stereotype.Component;

/**
 * Stand-in for a real gateway. It behaves like one from our side: the "gateway" calls our webhook with
 * an HMAC-SHA256 signature that must be verified before the result is trusted.
 */
@Component
public class MockGateway {

    public static final String PROVIDER = "MOCK";

    private final PaymentProperties props;

    public MockGateway(PaymentProperties props) {
        this.props = props;
    }

    public String checkoutUrl(Payment payment) {
        return props.publicBaseUrl() + "/api/payments/mock/" + payment.getTxnRef() + "/simulate?result=SUCCESS";
    }

    public String sign(String txnRef, String status, long amount) {
        return Hmac.hex("HmacSHA256", props.mock().secret(), txnRef + "|" + status + "|" + amount);
    }

    public boolean verify(String txnRef, String status, long amount, String signature) {
        return Hmac.equalsIgnoreCase(sign(txnRef, status, amount), signature);
    }
}
