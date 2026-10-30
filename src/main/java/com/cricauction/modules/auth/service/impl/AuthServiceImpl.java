package com.cricauction.modules.auth.service.impl;

import com.cricauction.common.exception.BadRequestException;
import com.cricauction.common.exception.ResourceNotFoundException;
import com.cricauction.common.exception.UnauthorizedException;
import com.cricauction.modules.auth.dto.request.*;
import com.cricauction.modules.auth.dto.response.*;
import com.cricauction.modules.auth.entity.User;
import com.cricauction.modules.auth.entity.enums.UserRole;
import com.cricauction.modules.auth.service.AuthPersistenceService;
import com.cricauction.modules.auth.service.AuthQueryService;
import com.cricauction.modules.auth.service.AuthService;
import com.cricauction.modules.auth.service.GoogleAuthService;
import com.cricauction.modules.auth.service.OtpService;
import com.cricauction.modules.auth.service.TokenService;
import com.cricauction.security.JwtTokenProvider;
import com.cricauction.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Orchestrator Service for Authentication implementing Strategy 1 (Read-Before-Write).
 * - Preparatory checks and user identity lookups route to Read Replica (AuthQueryService).
 * - Cryptographic operations (BCrypt hashing) and external verification run without holding DB connections.
 * - State mutations route to Master DB via fast micro-transactions (AuthPersistenceService).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final AuthQueryService authQueryService;
    private final AuthPersistenceService authPersistenceService;
    private final PasswordEncoder passwordEncoder;
    private final OtpService otpService;
    private final TokenService tokenService;
    private final JwtTokenProvider jwtTokenProvider;
    private final GoogleAuthService googleAuthService;

    @Override
    public SignUpResponse signUp(SignUpRequest request) {
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new BadRequestException("PASSWORDS_DO_NOT_MATCH", "Password and confirm password do not match.");
        }

        String email = request.getEmail().trim().toLowerCase();
        String mobile = request.getMobile().trim();

        // 1. Duplicate pre-check on Read Replica (Zero Master connection)
        authQueryService.validateNewUserIdentifiers(email, mobile);

        // 2. Compute BCrypt hash OUTSIDE database transaction (Zero connection holding during crypto)
        String encodedPassword = passwordEncoder.encode(request.getPassword());

        // 3. Fast micro-write on Master DB (~1 ms connection hold)
        User savedUser = authPersistenceService.createLocalUser(
                request.getFullName().trim(),
                email,
                mobile,
                encodedPassword
        );

        // 4. Send OTP verification to mobile
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

        // 1. Fetch user from Read Replica
        User user = authQueryService.findByIdentifierForSignIn(identifier);

        // 2. Match password with ZERO database connection held
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
    public VerifyOtpResponse verifyOtp(VerifyOtpRequest request) {
        String target = request.getTargetIdentifier();
        if (target == null || target.isBlank()) {
            throw new BadRequestException("INVALID_IDENTIFIER", "Target mobile number or email is required.");
        }
        String purpose = request.getPurpose() != null ? request.getPurpose().trim().toLowerCase() : "login";

        // 1. Verify OTP code
        otpService.verifyOtp(target, request.getOtp(), purpose);

        // 2. Fetch associated user from Read Replica
        Optional<User> userOpt = authQueryService.findByIdentifierOptional(target);

        if ("login".equalsIgnoreCase(purpose)) {
            User user = userOpt.orElseGet(() ->
                    authPersistenceService.createOtpUser(target, UserRole.PLAYER, null)
            );

            if (!user.isActive()) {
                throw new UnauthorizedException("ACCOUNT_SUSPENDED", "Your account has been deactivated. Please contact support.");
            }

            String accessToken = jwtTokenProvider.generateAccessToken(user);
            return VerifyOtpResponse.builder()
                    .isVerified(true)
                    .accessToken(accessToken)
                    .build();
        }

        User user = userOpt.orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND", "No user found with identifier " + target));

        if (!user.isActive()) {
            throw new UnauthorizedException("ACCOUNT_SUSPENDED", "Your account has been deactivated. Please contact support.");
        }

        if ("password_reset".equalsIgnoreCase(purpose)) {
            String resetToken = tokenService.createPasswordResetToken(user);
            return VerifyOtpResponse.builder()
                    .isVerified(true)
                    .resetToken(resetToken)
                    .build();
        } else {
            // "signup_verification" or general verification -> micro-write on Master DB
            User updated = authPersistenceService.markUserVerified(user.getId(), target.contains("@"));

            String accessToken = jwtTokenProvider.generateAccessToken(updated);
            return VerifyOtpResponse.builder()
                    .isVerified(true)
                    .accessToken(accessToken)
                    .build();
        }
    }

    @Override
    public AuthResponse loginWithOtp(OtpLoginRequest request) {
        String target = request.getIdentifier().trim().toLowerCase();

        // 1. Verify OTP
        otpService.verifyOtp(target, request.getOtp(), "login");

        // 2. Lookup user from Read Replica
        Optional<User> userOpt = authQueryService.findByIdentifierOptional(target);
        User user;

        if (userOpt.isPresent()) {
            User existing = userOpt.get();
            if (!existing.isActive()) {
                throw new UnauthorizedException("ACCOUNT_SUSPENDED", "Your account has been deactivated. Please contact support.");
            }
            // Fast micro-write on Master DB for verification flag updates
            user = authPersistenceService.loginExistingOtpUser(existing.getId(), target.contains("@"));
        } else {
            UserRole role = UserRole.PLAYER;
            if (request.getRole() != null && !request.getRole().isBlank()) {
                try {
                    role = UserRole.fromString(request.getRole());
                } catch (Exception ignored) {}
            }
            // Fast micro-write on Master DB for new user creation
            user = authPersistenceService.createOtpUser(target, role, request.getCity());
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

        // 1. Fetch user from Read Replica
        User user = authQueryService.findByIdentifierForPasswordReset(identifier);

        if (!user.isActive()) {
            throw new UnauthorizedException("ACCOUNT_SUSPENDED", "Your account has been deactivated. Please contact support.");
        }

        String targetContact;
        String deliveryMethod;
        String maskedContact;

        if (user.getMobile() != null && !user.getMobile().isBlank()) {
            targetContact = user.getMobile();
            deliveryMethod = "SMS";
            maskedContact = maskMobile(targetContact);
        } else if (user.getEmail() != null && !user.getEmail().isBlank()) {
            targetContact = user.getEmail();
            deliveryMethod = "EMAIL";
            maskedContact = maskEmail(targetContact);
        } else {
            throw new BadRequestException("INVALID_IDENTIFIER", "User has neither a valid mobile nor email registered.");
        }

        // 2. Dispatch OTP for password reset
        otpService.sendOtp(targetContact, "password_reset");

        return ForgotPasswordResponse.builder()
                .maskedContact(maskedContact)
                .deliveryMethod(deliveryMethod)
                .build();
    }

    @Override
    public void resetPassword(ResetPasswordRequest request) {
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new BadRequestException("PASSWORDS_DO_NOT_MATCH", "New password and confirm password do not match.");
        }

        // 1. Validate and consume token on Master
        User user = tokenService.validateAndConsumePasswordResetToken(request.getResetToken());

        // 2. Hash password OUTSIDE DB transaction (Zero connection held)
        String newPasswordHash = passwordEncoder.encode(request.getNewPassword());

        // 3. Fast micro-write on Master DB
        authPersistenceService.updatePassword(user.getId(), newPasswordHash);

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
    public UserProfileResponse getCurrentUser(UserPrincipal currentUser) {
        return authQueryService.getUserProfile(currentUser.getId());
    }

    @Override
    public RoleResponse switchRole(UserPrincipal currentUser, RoleSelectionRequest request) {
        // Fast micro-write on Master DB
        User updated = authPersistenceService.updateUserRoles(currentUser.getId(), request);

        List<String> roleNames = updated.getRoleNames();
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

    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return "****";
        }
        int atIndex = email.indexOf("@");
        String name = email.substring(0, atIndex);
        String domain = email.substring(atIndex);
        if (name.length() <= 2) {
            return name.charAt(0) + "***" + domain;
        }
        return name.substring(0, 2) + "***" + domain;
    }
}
