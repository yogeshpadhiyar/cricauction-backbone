package com.cricauction.modules.profile.controller;

import com.cricauction.modules.auth.entity.User;
import com.cricauction.modules.auth.entity.enums.UserRole;
import com.cricauction.modules.auth.entity.Role;
import com.cricauction.modules.auth.repository.RoleRepository;
import com.cricauction.modules.auth.repository.UserRepository;
import com.cricauction.modules.profile.dto.request.UpdatePlayerProfileRequest;
import com.cricauction.modules.profile.dto.request.UpdateUserProfileRequest;
import com.cricauction.modules.profile.entity.PlayerProfile;
import com.cricauction.modules.profile.repository.PlayerProfileRepository;
import com.cricauction.security.JwtTokenProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class UserProfileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PlayerProfileRepository playerProfileRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private User testUser;
    private User otherUser;
    private String token;

    @BeforeEach
    void setUp() {
        playerProfileRepository.deleteAll();

        testUser = User.builder()
                .fullName("Jasprit Bumrah")
                .email("jasprit.test@cricauction.app")
                .mobile("+919876543299")
                .passwordHash("hashed_dummy_password")
                .city("Ahmedabad")
                .bio("Fast bowler representing India and Mumbai.")
                .isMobileVerified(true)
                .isEmailVerified(true)
                .isActive(true)
                .build();

        userRepository.findByEmail("jasprit.test@cricauction.app")
                .ifPresent(u -> userRepository.delete(u));

        Role playerRole = roleRepository.findByName(UserRole.PLAYER).orElseGet(() -> roleRepository.save(Role.builder().name(UserRole.PLAYER).build()));
        testUser.addRole(playerRole);
        testUser = userRepository.save(testUser);
        token = jwtTokenProvider.generateAccessToken(testUser);

        otherUser = User.builder()
                .fullName("Rohit Sharma")
                .email("rohit.test@cricauction.app")
                .mobile("+919876543298")
                .passwordHash("hashed_dummy_password")
                .city("Mumbai")
                .bio("Hitman and Captain.")
                .isMobileVerified(true)
                .isEmailVerified(true)
                .isActive(true)
                .build();

        userRepository.findByEmail("rohit.test@cricauction.app")
                .ifPresent(u -> userRepository.delete(u));

        Role captainRole = roleRepository.findByName(UserRole.CAPTAIN).orElseGet(() -> roleRepository.save(Role.builder().name(UserRole.CAPTAIN).build()));
        otherUser.addRole(captainRole);
        otherUser = userRepository.save(otherUser);

        playerProfileRepository.save(PlayerProfile.builder()
                .userId(otherUser.getId())
                .playingRole("Batsman")
                .battingStyle("Right-hand bat")
                .bowlingStyle("Right-arm off break")
                .jerseyNumber(45)
                .highestScore(264)
                .build());
    }

    @Test
    @DisplayName("API 11: GET /api/v1/users/profile - Returns logged-in user profile")
    void testGetUserProfile() throws Exception {
        mockMvc.perform(get("/api/v1/users/profile")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.fullName").value("Jasprit Bumrah"))
                .andExpect(jsonPath("$.data.city").value("Ahmedabad"))
                .andExpect(jsonPath("$.data.bio").value("Fast bowler representing India and Mumbai."))
                .andExpect(jsonPath("$.data.roles").isArray());
    }

    @Test
    @DisplayName("API 11b: GET /api/v1/users/{userId}/profile - Unified info with account details AND cricket player profile")
    void testGetOtherUserProfileByIdUnified() throws Exception {
        mockMvc.perform(get("/api/v1/users/" + otherUser.getId() + "/profile")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.fullName").value("Rohit Sharma"))
                .andExpect(jsonPath("$.data.city").value("Mumbai"))
                .andExpect(jsonPath("$.data.bio").value("Hitman and Captain."))
                .andExpect(jsonPath("$.data.roles").isArray())
                .andExpect(jsonPath("$.data.playerProfile.playingRole").value("Batsman"))
                .andExpect(jsonPath("$.data.playerProfile.jerseyNumber").value(45))
                .andExpect(jsonPath("$.data.playerProfile.highestScore").value(264));
    }

    @Test
    @DisplayName("Ownership Security: PATCH /api/v1/users/profile - Rejected if client tries to update another user's ID")
    void testUpdateProfileWithMismatchUserIdForbidden() throws Exception {
        UpdateUserProfileRequest request = UpdateUserProfileRequest.builder()
                .userId(otherUser.getId()) // Malicious ID injection attempt
                .fullName("Malicious Change")
                .bio("Trying to alter Rohit's bio")
                .build();

        mockMvc.perform(patch("/api/v1/users/profile")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("USER_UPDATE_FORBIDDEN"));
    }

    @Test
    @DisplayName("Ownership Security: PUT /api/v1/users/player-profile - Rejected if client tries to update another user's ID")
    void testUpdatePlayerProfileWithMismatchUserIdForbidden() throws Exception {
        UpdatePlayerProfileRequest request = UpdatePlayerProfileRequest.builder()
                .userId(otherUser.getId()) // Malicious ID injection attempt
                .playingRole("Bowler")
                .build();

        mockMvc.perform(put("/api/v1/users/player-profile")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("USER_UPDATE_FORBIDDEN"));
    }

    @Test
    @DisplayName("API 12: PATCH /api/v1/users/profile - Updates user details for logged-in user with ownership check")
    void testUpdateUserProfile() throws Exception {
        UpdateUserProfileRequest request = UpdateUserProfileRequest.builder()
                .userId(testUser.getId()) // Matching userId
                .fullName("Jasprit J. Bumrah")
                .city("Mumbai")
                .bio("Leading fast bowler for India.")
                .build();

        mockMvc.perform(patch("/api/v1/users/profile")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Profile updated"))
                .andExpect(jsonPath("$.data.fullName").value("Jasprit J. Bumrah"))
                .andExpect(jsonPath("$.data.city").value("Mumbai"))
                .andExpect(jsonPath("$.data.bio").value("Leading fast bowler for India."));
    }

    @Test
    @DisplayName("API 13: POST /api/v1/users/profile/photo - Uploads profile avatar for logged-in user with ownership check")
    void testUploadProfilePhoto() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "photo",
                "avatar.jpg",
                "image/jpeg",
                "dummy image content bytes".getBytes()
        );

        mockMvc.perform(multipart("/api/v1/users/profile/photo")
                        .file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Photo uploaded successfully"))
                .andExpect(jsonPath("$.data.profilePhotoUrl", containsString("avatars/")));
    }

    @Test
    @DisplayName("API 14: PUT & GET /api/v1/users/player-profile - Updates & retrieves cricket stats for logged-in user")
    void testUpdateAndGetPlayerProfile() throws Exception {
        UpdatePlayerProfileRequest request = UpdatePlayerProfileRequest.builder()
                .userId(testUser.getId())
                .playingRole("Bowler")
                .battingStyle("Right-hand bat")
                .bowlingStyle("Right-arm fast")
                .jerseyNumber(93)
                .cricHeroesProfileUrl("https://cricheroes.in/player-profile/99881")
                .pastTournaments(15)
                .highestScore(35)
                .bestBowling("6/19")
                .build();

        mockMvc.perform(put("/api/v1/users/player-profile")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Player cricket profile updated"))
                .andExpect(jsonPath("$.data.playingRole").value("Bowler"))
                .andExpect(jsonPath("$.data.bowlingStyle").value("Right-arm fast"))
                .andExpect(jsonPath("$.data.jerseyNumber").value(93))
                .andExpect(jsonPath("$.data.bestBowling").value("6/19"));

        // Verify GET retrieval
        mockMvc.perform(get("/api/v1/users/player-profile")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.playingRole").value("Bowler"))
                .andExpect(jsonPath("$.data.bestBowling").value("6/19"));
    }

    @Test
    @DisplayName("Negative Test: Unauthorized request without JWT fails with 401")
    void testUnauthorizedRequest() throws Exception {
        mockMvc.perform(get("/api/v1/users/profile"))
                .andExpect(status().isUnauthorized());
    }
}
