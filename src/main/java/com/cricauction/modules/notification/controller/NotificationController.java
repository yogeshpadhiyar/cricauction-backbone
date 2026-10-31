package com.cricauction.modules.notification.controller;

import com.cricauction.common.api.ApiResponse;
import com.cricauction.modules.notification.dto.request.RegisterDeviceTokenRequest;
import com.cricauction.modules.notification.dto.response.NotificationResponse;
import com.cricauction.modules.notification.service.NotificationService;
import com.cricauction.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Enterprise REST Controller exposing Module 9: Notifications & Push Alerts (APIs 59 - 62).
 */
@Slf4j
@Validated
@RestController
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
@Tag(name = "Module 9: Notifications & Push Alerts", description = "Endpoints for in-app notification inbox, read/unread states, and mobile push token registration (FCM/APNS)")
@SecurityRequirement(name = "bearerAuth")
public class NotificationController {

    private final NotificationService notificationService;

    /**
     * API 59: List Notifications
     */
    @GetMapping({"/api/v1/notifications", "/notifications"})
    @Operation(summary = "API 59: List Notifications", description = "Displays inbox of auction alerts, invite notices, and match schedules for the authenticated user")
    public ResponseEntity<ApiResponse<List<NotificationResponse>>> listNotifications(
            @AuthenticationPrincipal UserPrincipal currentUser
    ) {
        List<NotificationResponse> notifications = notificationService.listNotifications(currentUser);
        return ResponseEntity.ok(ApiResponse.success("Notifications retrieved successfully", notifications));
    }

    /**
     * API 60: Mark Notification as Read
     */
    @PatchMapping({"/api/v1/notifications/{id}/read", "/notifications/{id}/read"})
    @Operation(summary = "API 60: Mark Notification as Read", description = "Marks an individual notification as read upon tap")
    public ResponseEntity<ApiResponse<Void>> markAsRead(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable("id") @NotBlank @Size(max = 100) String id
    ) {
        notificationService.markAsRead(currentUser, id);
        return ResponseEntity.ok(ApiResponse.success("Notification marked as read", null));
    }

    /**
     * API 61: Mark All Notifications as Read
     */
    @PostMapping({"/api/v1/notifications/read-all", "/notifications/read-all"})
    @Operation(summary = "API 61: Mark All Notifications as Read", description = "Clears unread badge counter in one tap")
    public ResponseEntity<ApiResponse<Void>> markAllAsRead(
            @AuthenticationPrincipal UserPrincipal currentUser
    ) {
        notificationService.markAllAsRead(currentUser);
        return ResponseEntity.ok(ApiResponse.success("All notifications marked as read", null));
    }

    /**
     * API 62: Register Device Push Token (FCM / APNS)
     */
    @PostMapping({"/api/v1/notifications/device-token", "/notifications/device-token"})
    @Operation(summary = "API 62: Register Device Push Token", description = "Registers mobile/web push token on app startup for remote push notifications")
    public ResponseEntity<ApiResponse<Void>> registerDeviceToken(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @Valid @RequestBody RegisterDeviceTokenRequest request
    ) {
        notificationService.registerDeviceToken(currentUser, request);
        return ResponseEntity.ok(ApiResponse.success("Device push token registered successfully", null));
    }
}
