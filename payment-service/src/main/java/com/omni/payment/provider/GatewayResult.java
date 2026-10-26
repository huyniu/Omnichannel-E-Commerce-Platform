package com.omni.payment.provider;

/**
 * Normalized, already signature-verified outcome reported by a payment gateway.
 *
 * @param amount amount in VND as reported by the gateway (checked against our own record)
 */
public record GatewayResult(String txnRef, boolean success, long amount, String providerTxnId, String reason,
                            String rawPayload) {
}
