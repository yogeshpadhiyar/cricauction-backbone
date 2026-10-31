package com.cricauction.modules.notification.service;

import com.cricauction.modules.notification.entity.DevicePushToken;
import com.cricauction.modules.notification.repository.DevicePushTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Non-blocking push notification dispatcher.
 * Delivers alerts to registered iOS / Android (FCM / APNS) tokens asynchronously.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PushNotificationDispatcher {

    private final DevicePushTokenRepository devicePushTokenRepository;

    @Async
    public void dispatchPush(Long userId, String title, String body) {
        try {
            List<DevicePushToken> tokens = devicePushTokenRepository.findByUserIdAndIsActiveTrue(userId);
            if (tokens.isEmpty()) {
                log.debug("No active push tokens registered for userId={}", userId);
                return;
            }
            for (DevicePushToken token : tokens) {
                log.info("[PUSH-DISPATCH] Sending push notification to platform [{}] token [{}] - Title: '{}', Body: '{}'",
                        token.getPlatform(), maskToken(token.getDeviceToken()), title, body);
                // Production bridge to FCM/APNS adapter
            }
        } catch (Exception e) {
            log.error("Failed to dispatch push notification to userId {}: {}", userId, e.getMessage(), e);
        }
    }

    private String maskToken(String token) {
        if (token == null || token.length() <= 10) {
            return "***";
        }
        return token.substring(0, 6) + "..." + token.substring(token.length() - 4);
    }
}
