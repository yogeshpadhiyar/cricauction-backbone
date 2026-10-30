package com.cricauction.modules.profile.controller;

import com.cricauction.common.api.ApiResponse;
import com.cricauction.modules.auth.dto.response.UserProfileResponse;
import com.cricauction.modules.profile.dto.request.UpdatePlayerProfileRequest;
import com.cricauction.modules.profile.dto.request.UpdateUserProfileRequest;
import com.cricauction.modules.profile.dto.response.PlayerProfileResponse;
import com.cricauction.modules.profile.dto.response.ProfilePhotoUploadResponse;
import com.cricauction.modules.profile.service.ProfileService;
import com.cricauction.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@RestController
@Validated
@PreAuthorize("isAuthenticated()")
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "Profile & Cricket Profile Management", description = "User personal profile, photo upload, and cricket stats APIs")
@SecurityRequirement(name = "bearerAuth")
public class UserProfileController {

    private final ProfileService profileService;

    // 11. Current Logged-In User Profile (Includes Cricket Stats if present)
    @GetMapping("/profile")
    @Operation(summary = "11. Get Current User Profile Details", description = "Retrieves profile details including bio, city, contact, active role, and cricket stats for the logged-in user")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getUserProfile(
            @AuthenticationPrincipal UserPrincipal currentUser) {
        UserProfileResponse response = profileService.getUserProfile(currentUser);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // 11b. View Any User Profile By ID (Unified: Returns full account info + cricket stats with PII protection)
    @GetMapping("/{userId}/profile")
    @Operation(summary = "11b. View Any User Profile", description = "Allows an authenticated user to view another user's public profile and cricket stats by userId")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getUserProfileById(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable("userId") @Positive(message = "User ID must be a positive number") Long userId) {
        UserProfileResponse response = profileService.getUserProfileById(currentUser, userId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // 12. Update User Profile (Strictly for Current Logged-In User with Ownership Validation)
    @PatchMapping("/profile")
    @Operation(summary = "12. Update Current User Profile", description = "Updates personal details such as full name, city, bio, and mobile for the logged-in user with ownership verification")
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateUserProfile(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @Valid @RequestBody UpdateUserProfileRequest request) {
        UserProfileResponse response = profileService.updateUserProfile(currentUser, request);
        return ResponseEntity.ok(ApiResponse.success("Profile updated", response));
    }

    // 13. Upload Profile Photo (Strictly for Current Logged-In User with Ownership Validation)
    @PostMapping(value = "/profile/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "13. Upload Profile Photo", description = "Uploads a profile avatar image (JPEG, PNG, WEBP) for the logged-in user")
    public ResponseEntity<ApiResponse<ProfilePhotoUploadResponse>> uploadProfilePhoto(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @RequestParam("photo") MultipartFile photo) {
        ProfilePhotoUploadResponse response = profileService.uploadProfilePhoto(currentUser, photo);
        return ResponseEntity.ok(ApiResponse.success("Photo uploaded successfully", response));
    }

    // 14. Update Cricket Player Bio & Stats (Strictly for Current Logged-In Player with Ownership Validation)
    @PutMapping("/player-profile")
    @Operation(summary = "14. Update Cricket Player Bio & Stats", description = "Updates cricket profile, playing role, CricHeroes link, and bowling/batting stats for the logged-in player with ownership verification")
    public ResponseEntity<ApiResponse<PlayerProfileResponse>> updatePlayerProfile(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @Valid @RequestBody UpdatePlayerProfileRequest request) {
        PlayerProfileResponse response = profileService.updatePlayerProfile(currentUser, request);
        return ResponseEntity.ok(ApiResponse.success("Player cricket profile updated", response));
    }

    // 14b. Get Current Player's Cricket Profile
    @GetMapping("/player-profile")
    @Operation(summary = "14b. Get Cricket Player Bio & Stats", description = "Retrieves cricket profile and stats for the authenticated player")
    public ResponseEntity<ApiResponse<PlayerProfileResponse>> getPlayerProfile(
            @AuthenticationPrincipal UserPrincipal currentUser) {
        PlayerProfileResponse response = profileService.getPlayerProfile(currentUser);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
