package com.omni.notification.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.omni.common.event.EventEnvelope;
import com.omni.common.event.Events;

import java.text.NumberFormat;
import java.util.Locale;
import java.util.Optional;

/**
 * Turns a domain event into a customer-facing message. Events that customers do not care about
 * return empty.
 */
final class MessageTemplates {

    private MessageTemplates() {
    }

    record Message(Long userId, String email, String orderId, String orderCode, String subject, String body) {
    }

    static Optional<Message> render(EventEnvelope event) {
        JsonNode p = event.payload();
        String code = text(p, "orderCode");
        String subject;
        String body;
        switch (event.eventType()) {
            case Events.ORDER_CREATED -> {
                subject = "[Omni Shop] Đã nhận đơn hàng " + code;
                body = "Cảm ơn bạn đã đặt hàng!\n\nMã đơn: " + code + "\nTổng tiền: " + vnd(p.path("totalAmount").asLong())
                        + "\nPhương thức thanh toán: " + text(p, "paymentMethod")
                        + "\n\nChúng tôi sẽ thông báo khi đơn hàng được xác nhận.";
            }
            case Events.ORDER_CONFIRMED -> {
                subject = "[Omni Shop] Đơn hàng " + code + " đã được xác nhận";
                body = "Đơn hàng " + code + " (" + vnd(p.path("totalAmount").asLong()) + ") đã được xác nhận và đang được chuẩn bị.";
            }
            case Events.ORDER_CANCELLED -> {
                subject = "[Omni Shop] Đơn hàng " + code + " đã bị hủy";
                body = "Đơn hàng " + code + " đã bị hủy.\nLý do: " + text(p, "reason")
                        + (p.path("refundRequired").asBoolean() ? "\nSố tiền đã thanh toán sẽ được hoàn lại." : "");
            }
            case Events.ORDER_STATUS_CHANGED -> {
                String status = text(p, "newStatus");
                subject = "[Omni Shop] Cập nhật đơn hàng " + code;
                body = switch (status) {
                    case "SHIPPING" -> "Đơn hàng " + code + " đang được giao đến bạn.";
                    case "COMPLETED" -> "Đơn hàng " + code + " đã giao thành công. Cảm ơn bạn đã mua sắm!";
                    default -> "Đơn hàng " + code + " chuyển sang trạng thái " + status + ".";
                };
            }
            case Events.PAYMENT_SUCCEEDED -> {
                subject = "[Omni Shop] Thanh toán thành công cho đơn " + code;
                body = "Chúng tôi đã nhận " + vnd(p.path("amount").asLong()) + " qua " + text(p, "provider") + ".";
            }
            case Events.PAYMENT_FAILED -> {
                subject = "[Omni Shop] Thanh toán thất bại cho đơn " + code;
                body = "Thanh toán cho đơn " + code + " không thành công (" + text(p, "reason") + "). Đơn hàng đã được hủy.";
            }
            case Events.PAYMENT_REFUNDED -> {
                subject = "[Omni Shop] Hoàn tiền đơn " + code;
                body = "Đã hoàn " + vnd(p.path("amount").asLong()) + " cho đơn " + code + ".";
            }
            default -> {
                return Optional.empty();
            }
        }
        Long userId = p.hasNonNull("userId") ? p.get("userId").asLong() : null;
        return Optional.of(new Message(userId, text(p, "customerEmail"), text(p, "orderId"), code, subject, body));
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? "" : v.asText();
    }

    private static String vnd(long amount) {
        return NumberFormat.getInstance(Locale.forLanguageTag("vi-VN")).format(amount) + " ₫";
    }
}
