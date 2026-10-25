package com.cricauction.modules.profile.service.impl;

import com.cricauction.common.exception.ConflictException;
import com.cricauction.common.exception.ForbiddenException;
import com.cricauction.common.exception.ResourceNotFoundException;
import com.cricauction.common.exception.UnauthorizedException;
import com.cricauction.modules.auth.dto.response.UserProfileResponse;
import com.cricauction.modules.auth.entity.User;
import com.cricauction.modules.auth.repository.UserRepository;
import com.cricauction.modules.profile.dto.request.UpdatePlayerProfileRequest;
import com.cricauction.modules.profile.dto.request.UpdateUserProfileRequest;
import com.cricauction.modules.profile.dto.response.PlayerProfileResponse;
import com.cricauction.modules.profile.dto.response.ProfilePhotoUploadResponse;
import com.cricauction.modules.profile.entity.PlayerProfile;
import com.cricauction.modules.profile.repository.PlayerProfileRepository;
import com.cricauction.modules.profile.service.ProfileService;
import com.cricauction.modules.profile.storage.FileStorageStrategy;
import com.cricauction.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileServiceImpl implements ProfileService {

    private final UserRepository userRepository;
    private final PlayerProfileRepository playerProfileRepository;
    private final List<FileStorageStrategy> storageStrategies;

    @Value("${cricauction.storage.active-strategy:LOCAL}")
    private String activeStorageStrategy;

    @Override
    @Transactional(readOnly = true)
    public UserProfileResponse getUserProfile(UserPrincipal currentUser) {
        if (currentUser == null || currentUser.getId() == null) {
            throw new UnauthorizedException("UNAUTHORIZED", "Authentication required to view profile.");
        }
        return getUserProfileById(currentUser.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public UserProfileResponse getUserProfileById(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND", "User profile not found for ID: " + userId));

        PlayerProfile playerProfile = playerProfileRepository.findByUserId(userId).orElse(null);
        return UserProfileResponse.fromEntity(user, playerProfile);
    }

    @Override
    @Transactional
    public UserProfileResponse updateUserProfile(UserPrincipal currentUser, UpdateUserProfileRequest request) {
        validateOwnership(currentUser, request.getUserId());

        User user = userRepository.findById(currentUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND", "User profile not found."));

        if (!user.isActive()) {
            throw new ForbiddenException("ACCOUNT_INACTIVE", "Your account is not active. Profile update forbidden.");
        }

        if (request.getMobile() != null && !request.getMobile().isBlank()) {
            String newMobile = request.getMobile().trim();
            if (!newMobile.equalsIgnoreCase(user.getMobile())) {
                if (userRepository.existsByMobile(newMobile)) {
                    throw new ConflictException("MOBILE_ALREADY_EXISTS",
                            "The mobile number " + newMobile + " is already registered by another account.");
                }
                user.setMobile(newMobile);
                user.setMobileVerified(false);
            }
        }

        if (request.getFullName() != null && !request.getFullName().isBlank()) {
            user.setFullName(request.getFullName().trim());
        }

        if (request.getCity() != null) {
            user.setCity(request.getCity().trim());
        }

        if (request.getBio() != null) {
            user.setBio(request.getBio().trim());
        }

        User updated = userRepository.save(user);
        log.info("Ownership validated. Updated profile for user ID [{}]", updated.getId());

        PlayerProfile playerProfile = playerProfileRepository.findByUserId(updated.getId()).orElse(null);
        return UserProfileResponse.fromEntity(updated, playerProfile);
    }

    @Override
    @Transactional
    public ProfilePhotoUploadResponse uploadProfilePhoto(UserPrincipal currentUser, MultipartFile file) {
        validateOwnership(currentUser, null);

        User user = userRepository.findById(currentUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND", "User profile not found."));

        if (!user.isActive()) {
            throw new ForbiddenException("ACCOUNT_INACTIVE", "Your account is not active. Profile photo upload forbidden.");
        }

        FileStorageStrategy storage = storageStrategies.stream()
                .filter(s -> s.supports(activeStorageStrategy))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Storage strategy not configured: " + activeStorageStrategy));

        String photoUrl = storage.uploadFile(file, "avatars");

        // Delete old photo if existed locally
        if (user.getProfilePhotoUrl() != null && user.getProfilePhotoUrl().startsWith("http")) {
            storage.deleteFile(user.getProfilePhotoUrl());
        }

        user.setProfilePhotoUrl(photoUrl);
        userRepository.save(user);

        log.info("Ownership validated. Updated profile photo for user ID [{}] -> {}", user.getId(), photoUrl);
        return ProfilePhotoUploadResponse.builder()
                .profilePhotoUrl(photoUrl)
                .build();
    }

    @Override
    @Transactional
    public PlayerProfileResponse updatePlayerProfile(UserPrincipal currentUser, UpdatePlayerProfileRequest request) {
        validateOwnership(currentUser, request.getUserId());

        User user = userRepository.findById(currentUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND", "User not found."));

        if (!user.isActive()) {
            throw new ForbiddenException("ACCOUNT_INACTIVE", "Your account is not active. Player profile update forbidden.");
        }

        PlayerProfile profile = playerProfileRepository.findByUserId(currentUser.getId())
                .orElseGet(() -> PlayerProfile.builder()
                        .userId(currentUser.getId())
                        .build()
                );

        profile.setPlayingRole(request.getPlayingRole().trim());

        if (request.getBattingStyle() != null) {
            profile.setBattingStyle(request.getBattingStyle().trim());
        }
        if (request.getBowlingStyle() != null) {
            profile.setBowlingStyle(request.getBowlingStyle().trim());
        }
        if (request.getJerseyNumber() != null) {
            profile.setJerseyNumber(request.getJerseyNumber());
        }
        if (request.getCricHeroesProfileUrl() != null) {
            profile.setCricHeroesProfileUrl(request.getCricHeroesProfileUrl().trim());
        }
        if (request.getPastTournaments() != null) {
            profile.setPastTournaments(request.getPastTournaments());
        }
        if (request.getHighestScore() != null) {
            profile.setHighestScore(request.getHighestScore());
        }
        if (request.getBestBowling() != null) {
            profile.setBestBowling(request.getBestBowling().trim());
        }

        PlayerProfile saved = playerProfileRepository.save(profile);
        log.info("Ownership validated. Updated player cricket bio for user ID [{}] with role [{}]", currentUser.getId(), saved.getPlayingRole());

        return PlayerProfileResponse.fromEntity(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public PlayerProfileResponse getPlayerProfile(UserPrincipal currentUser) {
        if (currentUser == null || currentUser.getId() == null) {
            throw new UnauthorizedException("UNAUTHORIZED", "Authentication required to view player profile.");
        }
        PlayerProfile profile = playerProfileRepository.findByUserId(currentUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("PROFILE_NOT_FOUND", "Player profile not found for user ID: " + currentUser.getId()));

        return PlayerProfileResponse.fromEntity(profile);
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
