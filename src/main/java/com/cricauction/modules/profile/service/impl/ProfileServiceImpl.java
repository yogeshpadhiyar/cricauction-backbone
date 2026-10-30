package com.cricauction.modules.profile.service.impl;

import com.cricauction.common.exception.ForbiddenException;
import com.cricauction.common.exception.UnauthorizedException;
import com.cricauction.modules.auth.dto.response.UserProfileResponse;
import com.cricauction.modules.auth.entity.User;
import com.cricauction.modules.profile.dto.request.UpdatePlayerProfileRequest;
import com.cricauction.modules.profile.dto.request.UpdateUserProfileRequest;
import com.cricauction.modules.profile.dto.response.PlayerProfileResponse;
import com.cricauction.modules.profile.dto.response.ProfilePhotoUploadResponse;
import com.cricauction.modules.profile.entity.PlayerProfile;
import com.cricauction.modules.profile.service.ProfilePersistenceService;
import com.cricauction.modules.profile.service.ProfileQueryService;
import com.cricauction.modules.profile.service.ProfileService;
import com.cricauction.modules.profile.storage.FileStorageStrategy;
import com.cricauction.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Orchestrator Service for User and Player Profiles implementing Strategy 1 (Read-Before-Write).
 * - Preparatory read operations route to Read Replica (ProfileQueryService).
 * - Storage file I/O operations execute outside DB transactions (Zero connection held).
 * - Targeted write operations execute on Master DB via fast micro-transactions (ProfilePersistenceService).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileServiceImpl implements ProfileService {

    private final ProfileQueryService profileQueryService;
    private final ProfilePersistenceService profilePersistenceService;
    private final List<FileStorageStrategy> storageStrategies;

    @Value("${cricauction.storage.active-strategy:LOCAL}")
    private String activeStorageStrategy;

    @Override
    public UserProfileResponse getUserProfile(UserPrincipal currentUser) {
        if (currentUser == null || currentUser.getId() == null) {
            throw new UnauthorizedException("UNAUTHORIZED", "Authentication required to view profile.");
        }
        return profileQueryService.getUserProfileById(currentUser, currentUser.getId());
    }

    @Override
    public UserProfileResponse getUserProfileById(Long userId) {
        return profileQueryService.getUserProfileById(null, userId);
    }

    @Override
    public UserProfileResponse getUserProfileById(UserPrincipal currentUser, Long userId) {
        return profileQueryService.getUserProfileById(currentUser, userId);
    }

    @Override
    public UserProfileResponse updateUserProfile(UserPrincipal currentUser, UpdateUserProfileRequest request) {
        validateOwnership(currentUser, request.getUserId());

        // Step 1: Preparatory Reads on Read Replica
        User user = profileQueryService.findActiveUserById(currentUser.getId());
        profileQueryService.validateMobileAvailability(request.getMobile(), user.getMobile());

        // Step 2: Micro-write on Master DB
        User updated = profilePersistenceService.updateUserProfile(
                currentUser.getId(),
                request.getFullName(),
                request.getMobile(),
                request.getCity(),
                request.getBio()
        );

        // Step 3: Fetch linked PlayerProfile from Replica for response construction
        PlayerProfile playerProfile = profileQueryService.findPlayerProfileByUserId(updated.getId()).orElse(null);
        return UserProfileResponse.fromEntity(updated, playerProfile);
    }

    @Override
    public ProfilePhotoUploadResponse uploadProfilePhoto(UserPrincipal currentUser, MultipartFile file) {
        validateOwnership(currentUser, null);

        // Step 1: Preparatory Read on Read Replica (Verify user exists and is active)
        User user = profileQueryService.findActiveUserById(currentUser.getId());

        // Step 2: Storage upload I/O execution completely OUTSIDE DB transaction (Zero DB connection held)
        FileStorageStrategy storage = storageStrategies.stream()
                .filter(s -> s.supports(activeStorageStrategy))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Storage strategy not configured: " + activeStorageStrategy));

        String photoUrl = storage.uploadFile(file, "avatars");

        // Delete old photo if existed locally
        if (user.getProfilePhotoUrl() != null && user.getProfilePhotoUrl().startsWith("http")) {
            try {
                storage.deleteFile(user.getProfilePhotoUrl());
            } catch (Exception e) {
                log.warn("Failed to delete old profile photo: {}", e.getMessage());
            }
        }

        // Step 3: Micro-write on Master DB (~1 ms connection hold)
        profilePersistenceService.updateProfilePhotoUrl(user.getId(), photoUrl);

        return ProfilePhotoUploadResponse.builder()
                .profilePhotoUrl(photoUrl)
                .build();
    }

    @Override
    public PlayerProfileResponse updatePlayerProfile(UserPrincipal currentUser, UpdatePlayerProfileRequest request) {
        validateOwnership(currentUser, request.getUserId());

        // Step 1: Preparatory Read on Read Replica (Validate user is active)
        profileQueryService.findActiveUserById(currentUser.getId());

        // Step 2: Micro-write on Master DB
        PlayerProfile saved = profilePersistenceService.updatePlayerProfile(currentUser.getId(), request);

        return PlayerProfileResponse.fromEntity(saved);
    }

    @Override
    public PlayerProfileResponse getPlayerProfile(UserPrincipal currentUser) {
        return profileQueryService.getPlayerProfile(currentUser);
    }

    /**
     * Enforces that updates are only executed for the authenticated user owning the token.
     */
    private void validateOwnership(UserPrincipal currentUser, Long requestedUserId) {
        if (currentUser == null || currentUser.getId() == null) {
            throw new UnauthorizedException("UNAUTHORIZED", "Authentication required to perform this action.");
        }
        if (requestedUserId != null && !requestedUserId.equals(currentUser.getId())) {
            log.warn("Security Alert: User [{}] attempted to update profile belonging to user [{}]",
                    currentUser.getId(), requestedUserId);
            throw new ForbiddenException("USER_UPDATE_FORBIDDEN",
                    "Ownership check failed: You are not authorized to update another user's profile.");
        }
    }
}
