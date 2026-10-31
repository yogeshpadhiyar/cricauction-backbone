package com.cricauction.modules.notification.controller;

import com.cricauction.modules.auth.entity.Role;
import com.cricauction.modules.auth.entity.User;
import com.cricauction.modules.auth.entity.enums.UserRole;
import com.cricauction.modules.auth.repository.RoleRepository;
import com.cricauction.modules.auth.repository.UserRepository;
import com.cricauction.modules.notification.dto.request.RegisterDeviceTokenRequest;
import com.cricauction.modules.notification.entity.DevicePushToken;
import com.cricauction.modules.notification.entity.Notification;
import com.cricauction.modules.notification.entity.enums.NotificationType;
import com.cricauction.modules.notification.repository.DevicePushTokenRepository;
import com.cricauction.modules.notification.repository.NotificationRepository;
import com.cricauction.security.JwtTokenProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private DevicePushTokenRepository devicePushTokenRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private User primaryUser;
    private String primaryToken;

    private User otherUser;
    private String otherToken;

    @BeforeEach
    void setUp() {
        notificationRepository.deleteAll();
        devicePushTokenRepository.deleteAll();

        Role playerRole = roleRepository.findByName(UserRole.PLAYER)
                .orElseGet(() -> roleRepository.save(Role.builder().name(UserRole.PLAYER).build()));

        String uid = UUID.randomUUID().toString().substring(0, 8);
        primaryUser = User.builder()
                .fullName("Notification Test User " + uid)
                .email("notif_user_" + uid + "@cricauction.test")
                .mobile("+9199" + uid)
                .passwordHash("$2a$10$abcdefghijklmnopqrstuvwxyz1234567890")
                .isEmailVerified(true)
                .isActive(true)
                .build();
        primaryUser.addRole(playerRole);
        primaryUser = userRepository.save(primaryUser);
        primaryToken = "Bearer " + jwtTokenProvider.generateAccessToken(primaryUser);

        otherUser = User.builder()
                .fullName("Other User " + uid)
                .email("other_user_" + uid + "@cricauction.test")
                .mobile("+9198" + uid)
                .passwordHash("$2a$10$abcdefghijklmnopqrstuvwxyz1234567890")
                .isEmailVerified(true)
                .isActive(true)
                .build();
        otherUser.addRole(playerRole);
        otherUser = userRepository.save(otherUser);
        otherToken = "Bearer " + jwtTokenProvider.generateAccessToken(otherUser);
    }

    @Test
    @DisplayName("API 59: GET /notifications - Successfully lists user notifications with unread state")
    void listNotifications_success() throws Exception {
        Notification n1 = notificationRepository.save(Notification.builder()
                .publicId("notif_spl_auction")
                .user(primaryUser)
                .title("Auction Starting Soon")
                .body("Surat Premier League auction starts in 1 hour.")
                .type(NotificationType.AUCTION_STARTING)
                .referenceId("tourn_spl_2026")
                .isRead(false)
                .build());

        Notification n2 = notificationRepository.save(Notification.builder()
                .publicId("notif_welcome")
                .user(primaryUser)
                .title("Welcome to CricAuction")
                .body("Your player profile is active.")
                .type(NotificationType.GENERAL)
                .isRead(true)
                .readAt(Instant.now().minusSeconds(3600))
                .build());

        mockMvc.perform(get("/api/v1/notifications")
                        .header("Authorization", primaryToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].id", is(n2.getPublicId())))
                .andExpect(jsonPath("$.data[0].read", is(true)))
                .andExpect(jsonPath("$.data[1].id", is(n1.getPublicId())))
                .andExpect(jsonPath("$.data[1].title", is("Auction Starting Soon")))
                .andExpect(jsonPath("$.data[1].read", is(false)));

        // Test root endpoint alias /notifications
        mockMvc.perform(get("/notifications")
                        .header("Authorization", primaryToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data", hasSize(2)));
    }

    @Test
    @DisplayName("API 60: PATCH /notifications/:id/read - Successfully marks item as read")
    void markNotificationAsRead_success() throws Exception {
        Notification n = notificationRepository.save(Notification.builder()
                .publicId("notif_mark_read_test")
                .user(primaryUser)
                .title("Match Scheduled")
                .body("Match vs City Warriors scheduled on Sunday.")
                .type(NotificationType.MATCH_SCHEDULED)
                .isRead(false)
                .build());

        mockMvc.perform(patch("/api/v1/notifications/" + n.getPublicId() + "/read")
                        .header("Authorization", primaryToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.message", containsString("marked as read")));

        Notification updated = notificationRepository.findByPublicId(n.getPublicId()).orElseThrow();
        assertTrue(updated.isRead());
        assertNotNull(updated.getReadAt());
    }

    @Test
    @DisplayName("API 60: PATCH /notifications/:id/read - When item belongs to another user, returns 404")
    void markNotificationAsRead_otherUserItem_returns404() throws Exception {
        Notification n = notificationRepository.save(Notification.builder()
                .publicId("notif_other_user")
                .user(otherUser)
                .title("Captain Invite")
                .body("You have been invited to captain Surat Strikers.")
                .type(NotificationType.CAPTAIN_INVITE)
                .isRead(false)
                .build());

        mockMvc.perform(patch("/api/v1/notifications/" + n.getPublicId() + "/read")
                        .header("Authorization", primaryToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("API 61: POST /notifications/read-all - Marks all unread items as read for caller")
    void markAllNotificationsAsRead_success() throws Exception {
        notificationRepository.save(Notification.builder()
                .publicId("notif_bulk_1")
                .user(primaryUser)
                .title("Alert 1")
                .body("Body 1")
                .isRead(false)
                .build());

        notificationRepository.save(Notification.builder()
                .publicId("notif_bulk_2")
                .user(primaryUser)
                .title("Alert 2")
                .body("Body 2")
                .isRead(false)
                .build());

        Notification otherNotif = notificationRepository.save(Notification.builder()
                .publicId("notif_other_unread")
                .user(otherUser)
                .title("Other Alert")
                .body("Other Body")
                .isRead(false)
                .build());

        mockMvc.perform(post("/api/v1/notifications/read-all")
                        .header("Authorization", primaryToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.message", containsString("All notifications marked as read")));

        assertEquals(0, notificationRepository.countByUserIdAndIsReadFalse(primaryUser.getId()));

        // Verify other user's notification is unaffected
        Notification checkOther = notificationRepository.findByPublicId(otherNotif.getPublicId()).orElseThrow();
        assertFalse(checkOther.isRead());
    }

    @Test
    @DisplayName("API 62: POST /notifications/device-token - Registers push token and updates idempotently")
    void registerDeviceToken_success() throws Exception {
        RegisterDeviceTokenRequest req1 = RegisterDeviceTokenRequest.builder()
                .deviceToken("fcm_token_device_abc_123")
                .platform("ios")
                .build();

        mockMvc.perform(post("/api/v1/notifications/device-token")
                        .header("Authorization", primaryToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.message", containsString("registered successfully")));

        DevicePushToken token1 = devicePushTokenRepository.findByDeviceToken("fcm_token_device_abc_123").orElseThrow();
        assertEquals(primaryUser.getId(), token1.getUser().getId());
        assertEquals("ios", token1.getPlatform().getValue());
        assertTrue(token1.isActive());

        // Idempotent update on same token
        RegisterDeviceTokenRequest req2 = RegisterDeviceTokenRequest.builder()
                .deviceToken("fcm_token_device_abc_123")
                .platform("android")
                .build();

        mockMvc.perform(post("/notifications/device-token")
                        .header("Authorization", primaryToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req2)))
                .andExpect(status().isOk());

        DevicePushToken token2 = devicePushTokenRepository.findByDeviceToken("fcm_token_device_abc_123").orElseThrow();
        assertEquals("android", token2.getPlatform().getValue());
    }

    @Test
    @DisplayName("API 62: POST /notifications/device-token - Rejects invalid platform with 400 Bad Request")
    void registerDeviceToken_invalidPlatform_returns400() throws Exception {
        RegisterDeviceTokenRequest req = RegisterDeviceTokenRequest.builder()
                .deviceToken("fcm_token_invalid_platform_123")
                .platform("blackberry")
                .build();

        mockMvc.perform(post("/api/v1/notifications/device-token")
                        .header("Authorization", primaryToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)));
    }

    @Test
    @DisplayName("Performance & Memory: GET /notifications - Limits inbox response to top 50 notifications")
    void listNotifications_capsAtTop50() throws Exception {
        for (int i = 1; i <= 55; i++) {
            notificationRepository.save(Notification.builder()
                    .publicId("notif_cap_" + i)
                    .user(primaryUser)
                    .title("Cap Test Notification #" + i)
                    .body("Notification body #" + i)
                    .type(NotificationType.GENERAL)
                    .isRead(false)
                    .build());
        }

        mockMvc.perform(get("/api/v1/notifications")
                        .header("Authorization", primaryToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data", hasSize(50)));
    }

    @Test
    @DisplayName("Security: Anonymous requests to notification endpoints return 401 Unauthorized")
    void unauthorizedAccess_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/notifications"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(patch("/api/v1/notifications/notif_123/read"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/notifications/read-all"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/notifications/device-token"))
                .andExpect(status().isUnauthorized());
    }
}
