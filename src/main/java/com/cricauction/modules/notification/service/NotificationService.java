package com.cricauction.modules.notification.service;

import com.cricauction.modules.auth.entity.User;
import com.cricauction.modules.notification.dto.request.RegisterDeviceTokenRequest;
import com.cricauction.modules.notification.dto.response.NotificationResponse;
import com.cricauction.modules.notification.entity.enums.NotificationType;
import com.cricauction.security.UserPrincipal;

import java.util.List;

public interface NotificationService {

    /**
     * API 59: List notifications for user
     */
    List<NotificationResponse> listNotifications(UserPrincipal currentUser);

    /**
     * API 60: Mark notification as read
     */
    void markAsRead(UserPrincipal currentUser, String notificationId);

    /**
     * API 61: Mark all notifications as read
     */
    void markAllAsRead(UserPrincipal currentUser);

    /**
     * API 62: Register device push token (FCM / APNS)
     */
    void registerDeviceToken(UserPrincipal currentUser, RegisterDeviceTokenRequest request);

    /**
     * Programmatic notification dispatch helper for other modules
     */
    void sendNotification(User recipient, String title, String body, NotificationType type, String referenceId);
}
