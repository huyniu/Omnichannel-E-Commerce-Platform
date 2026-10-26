package com.omni.notification.web;

import com.omni.common.api.ApiResponse;
import com.omni.common.api.PageResponse;
import com.omni.common.exception.BusinessException;
import com.omni.common.web.AuthUser;
import com.omni.common.web.CurrentUser;
import com.omni.notification.domain.NotificationLog;
import com.omni.notification.repository.NotificationLogRepository;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications")
public class NotificationController {

    private final NotificationLogRepository logs;

    public record NotificationResponse(String id, String eventType, String orderId, String orderCode, String subject,
                                       String body, boolean read, Instant createdAt) {
        static NotificationResponse from(NotificationLog n) {
            return new NotificationResponse(n.getId(), n.getEventType(), n.getOrderId(), n.getOrderCode(),
                    n.getSubject(), n.getBody(), n.isRead(), n.getCreatedAt());
        }
    }

    public record DeliveryResponse(String id, String eventId, String eventType, String channel, String recipient,
                                   String subject, String status, Instant createdAt) {
        static DeliveryResponse from(NotificationLog n) {
            return new DeliveryResponse(n.getId(), n.getEventId(), n.getEventType(), n.getChannel().name(),
                    n.getRecipient(), n.getSubject(), n.getStatus().name(), n.getCreatedAt());
        }
    }

    /** In-app inbox of the current user. */
    @GetMapping("/me")
    public ApiResponse<PageResponse<NotificationResponse>> mine(@Parameter(hidden = true) @AuthUser CurrentUser user,
                                                                @RequestParam(defaultValue = "0") int page,
                                                                @RequestParam(defaultValue = "20") int size) {
        var result = logs.findByUserIdAndChannel(user.id(), NotificationLog.Channel.IN_APP,
                PageRequest.of(page, Math.min(size, 100), Sort.by("createdAt").descending()));
        return ApiResponse.ok(PageResponse.of(result, NotificationResponse::from));
    }

    @PostMapping("/{id}/read")
    public ApiResponse<Void> markRead(@Parameter(hidden = true) @AuthUser CurrentUser user, @PathVariable String id) {
        NotificationLog n = logs.findById(id).filter(l -> user.id().equals(l.getUserId()))
                .orElseThrow(() -> BusinessException.notFound("Notification"));
        n.setRead(true);
        logs.save(n);
        return ApiResponse.ok("OK", null);
    }

    /** Delivery journal for operators (all channels). */
    @GetMapping("/admin")
    public ApiResponse<PageResponse<DeliveryResponse>> journal(@Parameter(hidden = true) @AuthUser CurrentUser user,
                                                               @RequestParam(defaultValue = "0") int page,
                                                               @RequestParam(defaultValue = "50") int size) {
        user.requireStaff();
        var result = logs.findAll(PageRequest.of(page, Math.min(size, 200), Sort.by("createdAt").descending()));
        return ApiResponse.ok(PageResponse.of(result, DeliveryResponse::from));
    }
}
