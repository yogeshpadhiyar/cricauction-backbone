package com.cricauction.modules.auth.service.impl;

import com.cricauction.common.exception.UnauthorizedException;
import com.cricauction.modules.auth.dto.request.GoogleAuthRequest;
import com.cricauction.modules.auth.dto.response.AuthResponse;
import com.cricauction.modules.auth.dto.response.GoogleUserInfo;
import com.cricauction.modules.auth.dto.response.UserProfileResponse;
import com.cricauction.modules.auth.entity.User;
import com.cricauction.modules.auth.service.AuthPersistenceService;
import com.cricauction.modules.auth.service.AuthQueryService;
import com.cricauction.modules.auth.service.GoogleAuthService;
import com.cricauction.modules.auth.service.GoogleTokenVerifier;
import com.cricauction.modules.auth.service.TokenService;
import com.cricauction.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Orchestrator Service for Google OAuth2 authentication implementing Strategy 1 (Read-Before-Write).
 * - Google ID token network verification executes outside DB transactions (Zero DB connection held).
 * - User pre-check routes to Read Replica (AuthQueryService).
 * - User creation/linkage executes on Master DB via fast micro-write (AuthPersistenceService).
 * - JWT token issuance executes without blocking DB connections.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GoogleAuthServiceImpl implements GoogleAuthService {

    private final GoogleTokenVerifier googleTokenVerifier;
    private final AuthQueryService authQueryService;
    private final AuthPersistenceService authPersistenceService;
    private final TokenService tokenService;
    private final JwtTokenProvider jwtTokenProvider;

    @Override
    public AuthResponse authenticateWithGoogle(GoogleAuthRequest request) {
        // Step 1: External network verification OUTSIDE DB transaction (Zero DB connection held)
        GoogleUserInfo googleUser = googleTokenVerifier.verifyToken(request.getIdToken());

        // Step 2: Read Replica lookup to check if user already exists
        Optional<User> existingUserOpt = authQueryService.findByGoogleIdOrEmail(googleUser.getSub(), googleUser.getEmail());
        if (existingUserOpt.isPresent() && !existingUserOpt.get().isActive()) {
            throw new UnauthorizedException("ACCOUNT_SUSPENDED", "Your account has been deactivated. Please contact support.");
        }

        // Step 3: Fast micro-write on Master DB
        User user = authPersistenceService.saveOrUpdateGoogleUser(
                googleUser,
                request.getCity(),
                request.getMobile(),
                request.getRole()
        );

        // Step 4: Issue tokens
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
