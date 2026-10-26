package com.cricauction.modules.auth.service.impl;

import com.cricauction.common.exception.BadRequestException;
import com.cricauction.common.exception.ConflictException;
import com.cricauction.common.exception.ResourceNotFoundException;
import com.cricauction.common.exception.UnauthorizedException;
import com.cricauction.modules.auth.dto.request.*;
import com.cricauction.modules.auth.dto.response.*;
import com.cricauction.modules.auth.entity.Role;
import com.cricauction.modules.auth.entity.User;
import com.cricauction.modules.auth.entity.enums.UserRole;
import com.cricauction.modules.auth.repository.RoleRepository;
import com.cricauction.modules.auth.repository.UserRepository;
import com.cricauction.modules.auth.service.AuthService;
import com.cricauction.modules.auth.service.OtpService;
import com.cricauction.modules.auth.service.TokenService;
import com.cricauction.security.JwtTokenProvider;
import com.cricauction.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final OtpService otpService;
    private final TokenService tokenService;
    private final JwtTokenProvider jwtTokenProvider;
    private final com.cricauction.modules.auth.service.GoogleAuthService googleAuthService;

    @Override
    public SignUpResponse signUp(SignUpRequest request) {
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new BadRequestException("PASSWORDS_DO_NOT_MATCH", "Password and confirm password do not match.");
        }

        String email = request.getEmail().trim().toLowerCase();
        String mobile = request.getMobile().trim();

        // 1. Single consolidated duplicate pre-check
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

        // 2. Compute BCrypt hash OUTSIDE database transaction (Zero connection holding during crypto)
        String encodedPassword = passwordEncoder.encode(request.getPassword());

        // 3. Persist user and trigger OTP
        return persistNewUserAndSendOtp(request, email, mobile, encodedPassword);
    }

    @Transactional
    public SignUpResponse persistNewUserAndSendOtp(SignUpRequest request, String email, String mobile, String encodedPassword) {
        User user = User.builder()
                .fullName(request.getFullName().trim())
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

        // Send OTP verification to mobile
        otpService.sendOtp(savedUser.getMobile(), "signup_verification");

        return SignUpResponse.builder()
                .userId(savedUser.getPublicUserId())
                .mobile(savedUser.getMobile())
                .email(savedUser.getEmail())
                .isVerified(savedUser.isMobileVerified())
                .otpExpirySeconds(otpService.getOtpExpirySeconds())
                .build();
    }

    @Override
    public AuthResponse signIn(SignInRequest request) {
        String identifier = request.getIdentifier().trim();
        User user = userRepository.findByIdentifier(identifier)
                .orElseThrow(() -> new UnauthorizedException("INVALID_CREDENTIALS", "Invalid email/mobile or password."));

        // Match password with ZERO database connection held
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new UnauthorizedException("INVALID_CREDENTIALS", "Invalid email/mobile or password.");
        }

        if (!user.isActive()) {
            throw new UnauthorizedException("ACCOUNT_SUSPENDED", "Your account has been deactivated. Please contact support.");
        }

        String accessToken = jwtTokenProvider.generateAccessToken(user);
        String refreshToken = tokenService.createRefreshToken(user);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(jwtTokenProvider.getAccessTokenExpirationSeconds())
                .user(UserProfileResponse.fromEntity(user))
                .build();
    }

    @Override
    public SendOtpResponse sendOtp(SendOtpRequest request) {
        String target = request.getTargetIdentifier();
        if (target == null || target.isBlank()) {
            throw new BadRequestException("INVALID_IDENTIFIER", "Target mobile number or email is required.");
        }
        return otpService.sendOtp(target, request.getPurpose());
    }

    @Override
    @Transactional
    public VerifyOtpResponse verifyOtp(VerifyOtpRequest request) {
        String target = request.getTargetIdentifier();
        if (target == null || target.isBlank()) {
            throw new BadRequestException("INVALID_IDENTIFIER", "Target mobile number or email is required.");
        }
        String purpose = request.getPurpose() != null ? request.getPurpose().trim().toLowerCase() : "login";

        // 1. Verify OTP code
        otpService.verifyOtp(target, request.getOtp(), purpose);

        // 2. Fetch associated user
        Optional<User> userOpt = userRepository.findByIdentifier(target);

        if ("login".equalsIgnoreCase(purpose)) {
            User user = userOpt.orElseGet(() -> {
                boolean isEmail = target.contains("@");
                User newUser = User.builder()
                        .fullName(isEmail ? target.substring(0, target.indexOf("@")) : "User " + target.substring(Math.max(0, target.length() - 4)))
                        .email(isEmail ? target : null)
                        .mobile(isEmail ? null : target)
                        .isEmailVerified(isEmail)
                        .isMobileVerified(!isEmail)
                        .isActive(true)
                        .build();
                Role roleEntity = roleRepository.findByName(UserRole.PLAYER)
                        .orElseGet(() -> roleRepository.save(Role.builder().name(UserRole.PLAYER).build()));
                newUser.addRole(roleEntity);
                return userRepository.save(newUser);
            });

            String accessToken = jwtTokenProvider.generateAccessToken(user);
            return VerifyOtpResponse.builder()
                    .isVerified(true)
                    .accessToken(accessToken)
                    .build();
        }

        User user = userOpt.orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND", "No user found with identifier " + target));

        if ("password_reset".equalsIgnoreCase(purpose)) {
            String resetToken = tokenService.createPasswordResetToken(user);
            return VerifyOtpResponse.builder()
                    .isVerified(true)
                    .resetToken(resetToken)
                    .build();
        } else {
            // "signup_verification" or general verification
            if (target.contains("@")) {
                user.setEmailVerified(true);
            } else {
                user.setMobileVerified(true);
            }
            userRepository.save(user);

            String accessToken = jwtTokenProvider.generateAccessToken(user);
            return VerifyOtpResponse.builder()
                    .isVerified(true)
                    .accessToken(accessToken)
                    .build();
        }
    }

    @Override
    @Transactional
    public AuthResponse loginWithOtp(OtpLoginRequest request) {
        String target = request.getIdentifier().trim().toLowerCase();
        otpService.verifyOtp(target, request.getOtp(), "login");

        Optional<User> userOpt = userRepository.findByIdentifier(target);
        User user;
        if (userOpt.isPresent()) {
            user = userOpt.get();
            if (!user.isActive()) {
                throw new UnauthorizedException("ACCOUNT_SUSPENDED", "Your account has been deactivated. Please contact support.");
            }
            if (target.contains("@") && !user.isEmailVerified()) {
                user.setEmailVerified(true);
                user = userRepository.save(user);
            } else if (!target.contains("@") && !user.isMobileVerified()) {
                user.setMobileVerified(true);
                user = userRepository.save(user);
            }
        } else {
            UserRole role = UserRole.PLAYER;
            if (request.getRole() != null && !request.getRole().isBlank()) {
                try {
                    role = UserRole.fromString(request.getRole());
                } catch (Exception ignored) {}
            }

            final UserRole assignedRole = role;
            boolean isEmail = target.contains("@");
            user = User.builder()
                    .fullName(isEmail ? target.substring(0, target.indexOf("@")) : "User " + target.substring(Math.max(0, target.length() - 4)))
                    .email(isEmail ? target : null)
                    .mobile(isEmail ? null : target)
                    .city(request.getCity())
                    .authProvider(com.cricauction.modules.auth.entity.enums.AuthProvider.LOCAL)
                    .isEmailVerified(isEmail)
                    .isMobileVerified(!isEmail)
                    .isActive(true)
                    .build();
            Role roleEntity = roleRepository.findByName(assignedRole)
                    .orElseGet(() -> roleRepository.save(Role.builder().name(assignedRole).build()));
            user.addRole(roleEntity);
            user = userRepository.save(user);
        }

        String accessToken = jwtTokenProvider.generateAccessToken(user);
        String refreshToken = tokenService.createRefreshToken(user);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(jwtTokenProvider.getAccessTokenExpirationSeconds())
                .user(UserProfileResponse.fromEntity(user))
                .build();
    }

    @Override
    public ForgotPasswordResponse forgotPassword(ForgotPasswordRequest request) {
        String identifier = request.getIdentifier().trim();
        User user = userRepository.findByIdentifier(identifier)
                .orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND", "No registered account found with " + identifier));

        // Dispatch OTP for password reset
        otpService.sendOtp(user.getMobile(), "password_reset");

        return ForgotPasswordResponse.builder()
                .maskedContact(maskMobile(user.getMobile()))
                .deliveryMethod("SMS")
                .build();
    }

    @Override
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new BadRequestException("PASSWORDS_DO_NOT_MATCH", "New password and confirm password do not match.");
        }

        User user = tokenService.validateAndConsumePasswordResetToken(request.getResetToken());
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        log.info("Password reset successfully for user ID [{}]", user.getId());
    }

    @Override
    public TokenRefreshResponse refreshToken(RefreshTokenRequest request) {
        return tokenService.refreshToken(request.getRefreshToken());
    }

    @Override
    public void logout(UserPrincipal currentUser, LogoutRequest request) {
        if (request != null && request.getRefreshToken() != null) {
            tokenService.revokeRefreshToken(request.getRefreshToken());
        }
        log.info("User ID [{}] logged out successfully", currentUser != null ? currentUser.getId() : "anonymous");
    }

    @Override
    @Transactional(readOnly = true)
    public UserProfileResponse getCurrentUser(UserPrincipal currentUser) {
        User user = userRepository.findById(currentUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND", "User profile not found."));

        return UserProfileResponse.fromEntity(user);
    }

    @Override
    @Transactional
    public RoleResponse switchRole(UserPrincipal currentUser, RoleSelectionRequest request) {
        User user = userRepository.findById(currentUser.getId())
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

        userRepository.save(user);

        List<String> roleNames = user.getRoleNames();
        return RoleResponse.builder()
                .roles(roleNames)
                .build();
    }

    @Override
    public AuthResponse authenticateWithGoogle(GoogleAuthRequest request) {
        return googleAuthService.authenticateWithGoogle(request);
    }

    private String maskMobile(String mobile) {
        if (mobile == null || mobile.length() < 7) {
            return "****";
        }
        int len = mobile.length();
        String prefix = mobile.substring(0, Math.min(len - 5, 8));
        return prefix + " *****";
    }
}
