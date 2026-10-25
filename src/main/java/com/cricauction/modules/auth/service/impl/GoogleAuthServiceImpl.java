package com.cricauction.modules.auth.service.impl;

import com.cricauction.common.exception.UnauthorizedException;
import com.cricauction.modules.auth.dto.request.GoogleAuthRequest;
import com.cricauction.modules.auth.dto.response.AuthResponse;
import com.cricauction.modules.auth.dto.response.GoogleUserInfo;
import com.cricauction.modules.auth.dto.response.UserProfileResponse;
import com.cricauction.modules.auth.entity.User;
import com.cricauction.modules.auth.entity.enums.AuthProvider;
import com.cricauction.modules.auth.entity.enums.UserRole;
import com.cricauction.modules.auth.repository.UserRepository;
import com.cricauction.modules.auth.service.GoogleAuthService;
import com.cricauction.modules.auth.service.GoogleTokenVerifier;
import com.cricauction.modules.auth.service.TokenService;
import com.cricauction.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class GoogleAuthServiceImpl implements GoogleAuthService {

    private final GoogleTokenVerifier googleTokenVerifier;
    private final UserRepository userRepository;
    private final TokenService tokenService;
    private final JwtTokenProvider jwtTokenProvider;

    @Override
    @Transactional
    public AuthResponse authenticateWithGoogle(GoogleAuthRequest request) {
        // 1. Verify Google ID token
        GoogleUserInfo googleUser = googleTokenVerifier.verifyToken(request.getIdToken());

        // 2. Check if user already exists by Google ID or Email in a single consolidated query
        Optional<User> existingUserOpt = userRepository.findByGoogleIdOrEmail(googleUser.getSub(), googleUser.getEmail());

        User user;
        if (existingUserOpt.isPresent()) {
            user = existingUserOpt.get();

            if (!user.isActive()) {
                throw new UnauthorizedException("ACCOUNT_SUSPENDED", "Your account has been deactivated. Please contact support.");
            }

            // Link Google ID if not already linked
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
            log.info("Google sign-in successful for user ID [{}] email [{}]", user.getId(), user.getEmail());
        } else {
            // New user registration via Google
            UserRole role = UserRole.PLAYER;
            if (request.getRole() != null && !request.getRole().isBlank()) {
                try {
                    role = UserRole.fromString(request.getRole());
                } catch (IllegalArgumentException e) {
                    role = UserRole.PLAYER;
                }
            }

            user = User.builder()
                    .fullName(googleUser.getName())
                    .email(googleUser.getEmail())
                    .googleId(googleUser.getSub())
                    .authProvider(AuthProvider.GOOGLE)
                    .activeRole(role)
                    .city(request.getCity())
                    .mobile(request.getMobile())
                    .profilePhotoUrl(googleUser.getPicture())
                    .isEmailVerified(googleUser.isEmailVerified())
                    .isMobileVerified(false)
                    .isActive(true)
                    .build();

            user = userRepository.save(user);
            log.info("New user registered via Google OAuth: ID [{}], email [{}]", user.getId(), user.getEmail());
        }

        // 3. Issue CricAuction JWT tokens
        String accessToken = jwtTokenProvider.generateAccessToken(user);
        String refreshToken = tokenService.createRefreshToken(user);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(jwtTokenProvider.getAccessTokenExpirationSeconds())
                .user(UserProfileResponse.fromEntity(user))
                .build();
    }
}
