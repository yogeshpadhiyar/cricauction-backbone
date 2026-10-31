package com.cricauction.modules.notification.service.impl;

import com.cricauction.modules.auth.entity.User;
import com.cricauction.modules.notification.dto.request.RegisterDeviceTokenRequest;
import com.cricauction.modules.notification.dto.response.NotificationResponse;
import com.cricauction.modules.notification.entity.enums.NotificationType;
import com.cricauction.modules.notification.service.NotificationPersistenceService;
import com.cricauction.modules.notification.service.NotificationQueryService;
import com.cricauction.modules.notification.service.NotificationService;
import com.cricauction.modules.notification.service.PushNotificationDispatcher;
import com.cricauction.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationQueryService queryService;
    private final NotificationPersistenceService persistenceService;
    private final PushNotificationDispatcher pushDispatcher;

    @Override
    public List<NotificationResponse> listNotifications(UserPrincipal currentUser) {
        log.debug("Listing notifications for userId={}", currentUser.getId());
        return queryService.getUserNotifications(currentUser.getId());
    }

    @Override
    public void markAsRead(UserPrincipal currentUser, String notificationId) {
        log.debug("Marking notification {} as read for userId={}", notificationId, currentUser.getId());
        persistenceService.markAsRead(currentUser.getId(), notificationId);
    }

    @Override
    public void markAllAsRead(UserPrincipal currentUser) {
        log.debug("Marking all notifications as read for userId={}", currentUser.getId());
        persistenceService.markAllAsRead(currentUser.getId());
    }

    @Override
    public void registerDeviceToken(UserPrincipal currentUser, RegisterDeviceTokenRequest request) {
        log.debug("Registering device token for userId={}, platform={}", currentUser.getId(), request.getPlatform());
        persistenceService.registerDevicePushToken(currentUser.getId(), request.getDeviceToken(), request.getPlatform());
    }

    @Override
    public void sendNotification(User recipient, String title, String body, NotificationType type, String referenceId) {
        if (recipient == null) {
            return;
        }
        persistenceService.createNotification(recipient, title, body, type, referenceId);
        pushDispatcher.dispatchPush(recipient.getId(), title, body);
    }
}
