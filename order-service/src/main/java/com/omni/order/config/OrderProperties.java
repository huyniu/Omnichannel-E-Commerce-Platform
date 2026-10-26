package com.omni.order.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * @param paymentTimeout   unpaid online orders are cancelled (stock released) after this delay
 * @param shippingFee      flat shipping fee in VND
 * @param freeShippingFrom orders whose subtotal reaches this amount ship for free
 * @param maxQuantityPerSku per-line purchase limit (protects flash-sale stock from hoarding)
 * @param lockWait         how long a request waits for the SKU lock before giving up (HTTP 429)
 * @param lockLease        safety lease: the lock auto-expires if the holder crashes
 */
@ConfigurationProperties(prefix = "order")
public record OrderProperties(Duration paymentTimeout, long shippingFee, long freeShippingFrom,
                              int maxQuantityPerSku, Duration lockWait, Duration lockLease) {
}
