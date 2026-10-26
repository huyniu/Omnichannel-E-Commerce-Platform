package com.omni.notification.repository;

import com.omni.notification.domain.NotificationLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface NotificationLogRepository extends MongoRepository<NotificationLog, String> {

    boolean existsByEventIdAndChannel(String eventId, NotificationLog.Channel channel);

    Page<NotificationLog> findByUserIdAndChannel(Long userId, NotificationLog.Channel channel, Pageable pageable);
}
