package com.cricauction.modules.auth.service.impl;

import com.cricauction.common.exception.BadRequestException;
import com.cricauction.common.exception.ConflictException;
import com.cricauction.common.exception.ResourceNotFoundException;
import com.cricauction.common.exception.UnauthorizedException;
import com.cricauction.modules.auth.dto.request.*;
import com.cricauction.modules.auth.dto.response.*;
import com.cricauction.modules.auth.entity.User;
import com.cricauction.modules.auth.entity.enums.UserRole;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final OtpService otpService;
    private final TokenService tokenService;
    private final JwtTokenProvider jwtTokenProvider;
    private final com.cricauction.modules.auth.service.GoogleAuthService googleAuthService;

    @Override
    @Transactional
    public SignUpResponse signUp(SignUpRequest request) {
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new BadRequestException("PASSWORDS_DO_NOT_MATCH", "Password and confirm password do not match.");
        }

        if (userRepository.existsByEmail(request.getEmail().trim().toLowerCase())) {
            throw new ConflictException("EMAIL_ALREADY_EXISTS", "An account with this email address already exists.");
        }

        if (userRepository.existsByMobile(request.getMobile().trim())) {
            throw new ConflictException("MOBILE_ALREADY_EXISTS", "An account with this mobile number already exists.");
        }

        User user = User.builder()
                .fullName(request.getFullName().trim())
                .email(request.getEmail().trim().toLowerCase())
                .mobile(request.getMobile().trim())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .activeRole(UserRole.PLAYER)
                .isMobileVerified(false)
                .isActive(true)
                .build();

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
    @Transactional
    public AuthResponse signIn(SignInRequest request) {
        String identifier = request.getIdentifier().trim();
        User user = userRepository.findByIdentifier(identifier)
                .orElseThrow(() -> new UnauthorizedException("INVALID_CREDENTIALS", "Invalid email/mobile or password."));

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
        return otpService.sendOtp(request.getMobile().trim(), request.getPurpose());
    }

    @Override
    @Transactional
    public VerifyOtpResponse verifyOtp(VerifyOtpRequest request) {
        String mobile = request.getMobile().trim();
        String purpose = request.getPurpose().trim().toLowerCase();

        // 1. Verify OTP code
        otpService.verifyOtp(mobile, request.getOtp(), purpose);

        // 2. Fetch associated user
        User user = userRepository.findByMobile(mobile)
                .orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND", "No user found with mobile " + mobile));

        if ("password_reset".equalsIgnoreCase(purpose)) {
            String resetToken = tokenService.createPasswordResetToken(user);
            return VerifyOtpResponse.builder()
                    .isVerified(true)
                    .resetToken(resetToken)
                    .build();
        } else {
            // "signup_verification" or general mobile verification
            user.setMobileVerified(true);
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
        UserRole newRole = UserRole.fromString(request.getRole());

        User user = userRepository.findById(currentUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND", "User not found."));

        user.setActiveRole(newRole);
        userRepository.save(user);

        return RoleResponse.builder()
                .role(newRole.getValue())
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
