package com.cricauction.modules.auth.service;

import com.cricauction.common.exception.BadRequestException;
import com.cricauction.common.exception.ResourceNotFoundException;
import com.cricauction.common.exception.UnauthorizedException;
import com.cricauction.modules.auth.dto.request.RoleSelectionRequest;
import com.cricauction.modules.auth.dto.response.GoogleUserInfo;
import com.cricauction.modules.auth.entity.Role;
import com.cricauction.modules.auth.entity.User;
import com.cricauction.modules.auth.entity.enums.AuthProvider;
import com.cricauction.modules.auth.entity.enums.UserRole;
import com.cricauction.modules.auth.repository.RoleRepository;
import com.cricauction.modules.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Dedicated Persistence Service for Authentication and Identity mutations.
 * All operations execute on Master DB (CricAuction-Master-Pool) with minimal hold duration.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AuthPersistenceService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    public User createLocalUser(String fullName, String email, String mobile, String encodedPassword) {
        User user = User.builder()
                .fullName(fullName)
                .email(email)
                .mobile(mobile)
                .passwordHash(encodedPassword)
                .isMobileVerified(false)
                .isActive(true)
                .build();

        Role roleEntity = roleRepository.findByName(UserRole.PLAYER)
                .orElseGet(() -> roleRepository.save(Role.builder().name(UserRole.PLAYER).build()));
        user.addRole(roleEntity);

        User savedUser = userRepository.save(user);
        log.info("Master micro-write: Created new local user ID [{}] email [{}]", savedUser.getId(), savedUser.getEmail());
        return savedUser;
    }

    public User createOtpUser(String target, UserRole assignedRole, String city) {
        boolean isEmail = target.contains("@");
        User newUser = User.builder()
                .fullName(isEmail ? target.substring(0, target.indexOf("@")) : "User " + target.substring(Math.max(0, target.length() - 4)))
                .email(isEmail ? target : null)
                .mobile(isEmail ? null : target)
                .city(city)
                .authProvider(AuthProvider.LOCAL)
                .isEmailVerified(isEmail)
                .isMobileVerified(!isEmail)
                .isActive(true)
                .build();

        UserRole roleToAssign = assignedRole != null ? assignedRole : UserRole.PLAYER;
        Role roleEntity = roleRepository.findByName(roleToAssign)
                .orElseGet(() -> roleRepository.save(Role.builder().name(roleToAssign).build()));
        newUser.addRole(roleEntity);

        User savedUser = userRepository.save(newUser);
        log.info("Master micro-write: Registered new OTP user ID [{}] identifier [{}]", savedUser.getId(), target);
        return savedUser;
    }

    public User markUserVerified(Long userId, boolean isEmail) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND", "User not found ID: " + userId));

        if (isEmail) {
            user.setEmailVerified(true);
        } else {
            user.setMobileVerified(true);
        }
        User savedUser = userRepository.save(user);
        log.info("Master micro-write: Verified user ID [{}] (isEmail: {})", savedUser.getId(), isEmail);
        return savedUser;
    }

    public User loginExistingOtpUser(Long userId, boolean isEmail) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND", "User not found ID: " + userId));

        if (!user.isActive()) {
            throw new UnauthorizedException("ACCOUNT_SUSPENDED", "Your account has been deactivated. Please contact support.");
        }

        boolean updated = false;
        if (isEmail && !user.isEmailVerified()) {
            user.setEmailVerified(true);
            updated = true;
        } else if (!isEmail && !user.isMobileVerified()) {
            user.setMobileVerified(true);
            updated = true;
        }

        if (updated) {
            user = userRepository.save(user);
            log.info("Master micro-write: Auto-verified OTP login for user ID [{}]", user.getId());
        }
        return user;
    }

    public void updatePassword(Long userId, String encodedPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND", "User not found ID: " + userId));

        user.setPasswordHash(encodedPassword);
        userRepository.save(user);
        log.info("Master micro-write: Updated password for user ID [{}]", userId);
    }

    public User updateUserRoles(Long userId, RoleSelectionRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND", "User not found."));

        if (request.getRoles() != null && !request.getRoles().isEmpty()) {
            Set<Role> newRoles = new HashSet<>();
            for (String rStr : request.getRoles()) {
                UserRole ur = UserRole.fromString(rStr);
                if (ur != null) {
                    final UserRole targetRole = ur;
                    Role roleEntity = roleRepository.findByName(targetRole)
                            .orElseGet(() -> roleRepository.save(Role.builder().name(targetRole).build()));
                    newRoles.add(roleEntity);
                }
            }
            if (newRoles.isEmpty()) {
                throw new BadRequestException("INVALID_ROLE", "No valid roles provided.");
            }
            user.setRoles(newRoles);
        } else if (request.getRole() != null && !request.getRole().isBlank()) {
            final UserRole newRole = UserRole.fromString(request.getRole());
            Role roleEntity = roleRepository.findByName(newRole)
                    .orElseGet(() -> roleRepository.save(Role.builder().name(newRole).build()));
            user.addRole(roleEntity);
        } else {
            throw new BadRequestException("INVALID_ROLE_REQUEST", "Either 'roles' list or 'role' string must be provided.");
        }

        User updated = userRepository.save(user);
        log.info("Master micro-write: Updated roles for user ID [{}]", updated.getId());
        return updated;
    }

    public User saveOrUpdateGoogleUser(GoogleUserInfo googleUser, String city, String mobile, String roleName) {
        Optional<User> existingUserOpt = userRepository.findByGoogleIdOrEmail(googleUser.getSub(), googleUser.getEmail());

        if (existingUserOpt.isPresent()) {
            User user = existingUserOpt.get();
            if (!user.isActive()) {
                throw new UnauthorizedException("ACCOUNT_SUSPENDED", "Your account has been deactivated. Please contact support.");
            }

            boolean modified = false;
            if (user.getGoogleId() == null) {
                user.setGoogleId(googleUser.getSub());
                modified = true;
            }
            if (!user.isEmailVerified() && googleUser.isEmailVerified()) {
                user.setEmailVerified(true);
                modified = true;
            }
            if (user.getProfilePhotoUrl() == null && googleUser.getPicture() != null) {
                user.setProfilePhotoUrl(googleUser.getPicture());
                modified = true;
            }
            if (modified) {
                user = userRepository.save(user);
            }
            log.info("Master micro-write: Google sign-in linked for user ID [{}] email [{}]", user.getId(), user.getEmail());
            return user;
        } else {
            UserRole role = UserRole.PLAYER;
            if (roleName != null && !roleName.isBlank()) {
                try {
                    role = UserRole.fromString(roleName);
                } catch (IllegalArgumentException e) {
                    role = UserRole.PLAYER;
                }
            }

            User user = User.builder()
                    .fullName(googleUser.getName())
                    .email(googleUser.getEmail())
                    .googleId(googleUser.getSub())
                    .authProvider(AuthProvider.GOOGLE)
                    .city(city)
                    .mobile(mobile)
                    .profilePhotoUrl(googleUser.getPicture())
                    .isEmailVerified(googleUser.isEmailVerified())
                    .isMobileVerified(false)
                    .isActive(true)
                    .build();

            UserRole finalRole = role;
            Role roleEntity = roleRepository.findByName(finalRole)
                    .orElseGet(() -> roleRepository.save(Role.builder().name(finalRole).build()));
            user.addRole(roleEntity);

            User savedUser = userRepository.save(user);
            log.info("Master micro-write: New user registered via Google OAuth: ID [{}], email [{}]", savedUser.getId(), savedUser.getEmail());
            return savedUser;
        }
    }
}
