package com.cricauction.modules.profile.service;

import com.cricauction.common.exception.ResourceNotFoundException;
import com.cricauction.modules.auth.entity.User;
import com.cricauction.modules.auth.repository.UserRepository;
import com.cricauction.modules.profile.dto.request.UpdatePlayerProfileRequest;
import com.cricauction.modules.profile.entity.PlayerProfile;
import com.cricauction.modules.profile.repository.PlayerProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dedicated Persistence Service for User and Player Profiles.
 * All operations are micro-write transactions routing directly to Master DB (CricAuction-Master-Pool).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ProfilePersistenceService {

    private final UserRepository userRepository;
    private final PlayerProfileRepository playerProfileRepository;

    public User updateUserProfile(Long userId, String fullName, String mobile, String city, String bio) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND", "User profile not found."));

        if (mobile != null && !mobile.isBlank()) {
            String newMobile = mobile.trim();
            if (!newMobile.equalsIgnoreCase(user.getMobile())) {
                user.setMobile(newMobile);
                user.setMobileVerified(false);
            }
        }

        if (fullName != null && !fullName.isBlank()) {
            user.setFullName(fullName.trim());
        }

        if (city != null) {
            user.setCity(city.trim());
        }

        if (bio != null) {
            user.setBio(bio.trim());
        }

        User updated = userRepository.save(user);
        log.info("Master micro-write: Updated profile for user ID [{}]", updated.getId());
        return updated;
    }

    public void updateProfilePhotoUrl(Long userId, String photoUrl) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND", "User profile not found."));

        user.setProfilePhotoUrl(photoUrl);
        userRepository.save(user);
        log.info("Master micro-write: Updated profile photo for user ID [{}] -> {}", user.getId(), photoUrl);
    }

    public PlayerProfile updatePlayerProfile(Long userId, UpdatePlayerProfileRequest request) {
        PlayerProfile profile = playerProfileRepository.findByUserId(userId)
                .orElseGet(() -> PlayerProfile.builder()
                        .userId(userId)
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
        log.info("Master micro-write: Updated player cricket bio for user ID [{}] with role [{}]", userId, saved.getPlayingRole());
        return saved;
    }
}
