package com.cricauction.modules.notification.service;

import com.cricauction.common.exception.ResourceNotFoundException;
import com.cricauction.modules.auth.entity.User;
import com.cricauction.modules.notification.entity.Notification;
import com.cricauction.modules.notification.entity.enums.DevicePlatform;
import com.cricauction.modules.notification.entity.enums.NotificationType;
import com.cricauction.modules.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Dedicated write-persistence service executing micro-writes on CricAuction-Master-Pool.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class NotificationPersistenceService {

    private final NotificationRepository notificationRepository;
    private final JdbcTemplate jdbcTemplate;

    /**
     * API 60: Mark individual notification as read
     */
    public void markAsRead(Long userId, String notificationId) {
        log.debug("Marking notification {} as read for userId={}", notificationId, userId);
        Instant now = Instant.now();
        int updated = notificationRepository.markAsReadByPublicId(notificationId, userId, now);
        if (updated == 0) {
            // Also check if id is a numeric ID fallback
            try {
                Long numericId = Long.parseLong(notificationId);
                Notification n = notificationRepository.findById(numericId)
                        .filter(item -> item.getUser().getId().equals(userId))
                        .orElseThrow(() -> new ResourceNotFoundException("Notification not found with id: " + notificationId));
                n.setRead(true);
                n.setReadAt(now);
                notificationRepository.save(n);
                return;
            } catch (NumberFormatException ignored) {}
            throw new ResourceNotFoundException("Notification not found with id: " + notificationId);
        }
    }

    /**
     * API 61: Mark all unread notifications as read
     */
    public int markAllAsRead(Long userId) {
        log.debug("Marking all notifications as read for userId={}", userId);
        return notificationRepository.markAllAsReadByUserId(userId, Instant.now());
    }

    /**
     * API 62: Register or update device push token (Atomic UPSERT across PostgreSQL and H2)
     */
    public void registerDevicePushToken(Long userId, String deviceToken, String platform) {
        String cleanToken = deviceToken.trim();
        String normalizedPlatform = DevicePlatform.fromString(platform).getValue();

        try {
            String sql = "INSERT INTO device_push_tokens (user_id, device_token, platform, is_active, created_at, updated_at) " +
                         "VALUES (?, ?, ?, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) " +
                         "ON CONFLICT (device_token) DO UPDATE " +
                         "SET user_id = EXCLUDED.user_id, platform = EXCLUDED.platform, is_active = true, updated_at = CURRENT_TIMESTAMP";
            jdbcTemplate.update(sql, userId, cleanToken, normalizedPlatform);
        } catch (BadSqlGrammarException ex) {
            // H2 In-Memory fallback for test profile
            String sql = "MERGE INTO device_push_tokens (user_id, device_token, platform, is_active, created_at, updated_at) " +
                         "KEY (device_token) " +
                         "VALUES (?, ?, ?, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)";
            jdbcTemplate.update(sql, userId, cleanToken, normalizedPlatform);
        }
        log.debug("UPSERT push token successfully executed for userId={}, platform={}", userId, normalizedPlatform);
    }

    /**
     * Programmatic creation of notification
     */
    public Notification createNotification(User user, String title, String body, NotificationType type, String referenceId) {
        String publicId = "notif_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        Notification notification = Notification.builder()
                .publicId(publicId)
                .user(user)
                .title(title)
                .body(body)
                .type(type != null ? type : NotificationType.GENERAL)
                .referenceId(referenceId)
                .isRead(false)
                .build();
        return notificationRepository.save(notification);
    }
}
