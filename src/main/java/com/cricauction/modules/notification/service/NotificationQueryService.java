package com.cricauction.modules.notification.service;

import com.cricauction.modules.notification.dto.response.NotificationResponse;
import com.cricauction.modules.notification.entity.Notification;
import com.cricauction.modules.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Strategy 1 (Read-Before-Write): Dedicated read-only query service.
 * Executed exclusively on CricAuction-Replica-Pool via @Transactional(readOnly = true).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationQueryService {

    private final NotificationRepository notificationRepository;

    public List<NotificationResponse> getUserNotifications(Long userId) {
        log.debug("Strategy 1: Fetching notifications from replica pool for userId={}", userId);
        List<Notification> list = notificationRepository.findTop50ByUserIdOrderByCreatedAtDesc(userId);
        return list.stream()
                .map(this::mapToResponse)
                .toList();
    }

    private NotificationResponse mapToResponse(Notification n) {
        return NotificationResponse.builder()
                .id(n.getPublicId())
                .title(n.getTitle())
                .body(n.getBody())
                .read(n.isRead())
                .createdAt(n.getCreatedAt())
                .type(n.getType() != null ? n.getType().name() : "GENERAL")
                .referenceId(n.getReferenceId())
                .build();
    }
}
