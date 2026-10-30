package com.cricauction.modules.auth.service;

import com.cricauction.common.exception.ConflictException;
import com.cricauction.common.exception.ResourceNotFoundException;
import com.cricauction.common.exception.UnauthorizedException;
import com.cricauction.modules.auth.dto.response.UserProfileResponse;
import com.cricauction.modules.auth.entity.User;
import com.cricauction.modules.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Dedicated Read Service for Authentication and Identity lookups.
 * All queries are executed with readOnly = true routing directly to Read Replica (CricAuction-Replica-Pool).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthQueryService {

    private final UserRepository userRepository;

    public void validateNewUserIdentifiers(String email, String mobile) {
        List<Object[]> existing = userRepository.findExistingIdentifiers(email, mobile);
        if (!existing.isEmpty()) {
            for (Object[] row : existing) {
                if (email.equalsIgnoreCase((String) row[0])) {
                    throw new ConflictException("EMAIL_ALREADY_EXISTS", "An account with this email address already exists.");
                }
                if (mobile.equalsIgnoreCase((String) row[1])) {
                    throw new ConflictException("MOBILE_ALREADY_EXISTS", "An account with this mobile number already exists.");
                }
            }
        }
    }

    public User findByIdentifierForSignIn(String identifier) {
        return userRepository.findByIdentifier(identifier)
                .orElseThrow(() -> new UnauthorizedException("INVALID_CREDENTIALS", "Invalid email/mobile or password."));
    }

    public User findByIdentifierForPasswordReset(String identifier) {
        return userRepository.findByIdentifier(identifier)
                .orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND", "No registered account found with " + identifier));
    }

    public Optional<User> findByIdentifierOptional(String identifier) {
        return userRepository.findByIdentifier(identifier);
    }

    public Optional<User> findByGoogleIdOrEmail(String googleId, String email) {
        return userRepository.findByGoogleIdOrEmail(googleId, email);
    }

    public User findById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND", "User profile not found."));
    }

    public UserProfileResponse getUserProfile(Long userId) {
        User user = findById(userId);
        return UserProfileResponse.fromEntity(user);
    }
}
