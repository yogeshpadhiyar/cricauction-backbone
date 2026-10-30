package com.cricauction.modules.profile.service;

import com.cricauction.common.exception.ConflictException;
import com.cricauction.common.exception.ForbiddenException;
import com.cricauction.common.exception.ResourceNotFoundException;
import com.cricauction.common.exception.UnauthorizedException;
import com.cricauction.modules.auth.dto.response.UserProfileResponse;
import com.cricauction.modules.auth.entity.User;
import com.cricauction.modules.auth.repository.UserRepository;
import com.cricauction.modules.profile.dto.response.PlayerProfileResponse;
import com.cricauction.modules.profile.entity.PlayerProfile;
import com.cricauction.modules.profile.repository.PlayerProfileRepository;
import com.cricauction.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Dedicated Read Service for User and Player Profiles.
 * All queries are executed with readOnly = true routing directly to Read Replica (CricAuction-Replica-Pool).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProfileQueryService {

    private final UserRepository userRepository;
    private final PlayerProfileRepository playerProfileRepository;

    public User findActiveUserById(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND", "User profile not found."));

        if (!user.isActive()) {
            throw new ForbiddenException("ACCOUNT_INACTIVE", "Your account is not active. Profile operation forbidden.");
        }
        return user;
    }

    public void validateMobileAvailability(String newMobile, String currentMobile) {
        if (newMobile != null && !newMobile.isBlank()) {
            String trimmedMobile = newMobile.trim();
            if (!trimmedMobile.equalsIgnoreCase(currentMobile)) {
                if (userRepository.existsByMobile(trimmedMobile)) {
                    throw new ConflictException("MOBILE_ALREADY_EXISTS",
                            "The mobile number " + trimmedMobile + " is already registered by another account.");
                }
            }
        }
    }

    public Optional<PlayerProfile> findPlayerProfileByUserId(Long userId) {
        return playerProfileRepository.findByUserId(userId);
    }

    public UserProfileResponse getUserProfileById(UserPrincipal currentUser, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND", "User profile not found for ID: " + userId));

        PlayerProfile playerProfile = playerProfileRepository.findByUserId(userId).orElse(null);
        boolean isSelfOrAdmin = currentUser != null && (currentUser.getId().equals(userId) || currentUser.hasRole("admin"));
        return UserProfileResponse.fromEntity(user, playerProfile, isSelfOrAdmin);
    }

    public PlayerProfileResponse getPlayerProfile(UserPrincipal currentUser) {
        if (currentUser == null || currentUser.getId() == null) {
            throw new UnauthorizedException("UNAUTHORIZED", "Authentication required to view player profile.");
        }
        PlayerProfile profile = playerProfileRepository.findByUserId(currentUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("PROFILE_NOT_FOUND", "Player profile not found for user ID: " + currentUser.getId()));

        return PlayerProfileResponse.fromEntity(profile);
    }
}
