package com.cricauction.modules.auth.service.impl;

import com.cricauction.common.exception.BadRequestException;
import com.cricauction.common.exception.UnauthorizedException;
import com.cricauction.config.JwtConfig;
import com.cricauction.modules.auth.dto.response.TokenRefreshResponse;
import com.cricauction.modules.auth.entity.PasswordResetToken;
import com.cricauction.modules.auth.entity.RefreshToken;
import com.cricauction.modules.auth.entity.User;
import com.cricauction.modules.auth.repository.PasswordResetTokenRepository;
import com.cricauction.modules.auth.repository.RefreshTokenRepository;
import com.cricauction.modules.auth.service.TokenService;
import com.cricauction.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenServiceImpl implements TokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtConfig jwtConfig;

    @Value("${cricauction.password-reset.token-expiration-minutes:15}")
    private int passwordResetExpiryMinutes;

    @Override
    @Transactional
    public String createRefreshToken(User user) {
        String token = "rft_" + UUID.randomUUID().toString().replace("-", "");
        Instant expiresAt = Instant.now().plusMillis(jwtConfig.getRefreshTokenExpirationMs());

        RefreshToken refreshToken = RefreshToken.builder()
                .token(token)
                .user(user)
                .expiresAt(expiresAt)
                .revoked(false)
                .build();

        refreshTokenRepository.save(refreshToken);
        return token;
    }

    @Override
    @Transactional
    public TokenRefreshResponse refreshToken(String token) {
        RefreshToken refreshToken = refreshTokenRepository.findByToken(token)
                .orElseThrow(() -> new UnauthorizedException("INVALID_REFRESH_TOKEN", "Invalid or unknown refresh token."));

        if (!refreshToken.isValid()) {
            throw new UnauthorizedException("REFRESH_TOKEN_EXPIRED", "Refresh token has expired or been revoked. Please sign in again.");
        }

        User user = refreshToken.getUser();
        String newAccessToken = jwtTokenProvider.generateAccessToken(user);

        return TokenRefreshResponse.builder()
                .accessToken(newAccessToken)
                .expiresIn(jwtTokenProvider.getAccessTokenExpirationSeconds())
                .build();
    }

    @Override
    @Transactional
    public void revokeRefreshToken(String token) {
        if (token != null && !token.isBlank()) {
            refreshTokenRepository.findByToken(token).ifPresent(rt -> {
                rt.setRevoked(true);
                refreshTokenRepository.save(rt);
            });
        }
    }

    @Override
    @Transactional
    public String createPasswordResetToken(User user) {
        String token = "rst_" + UUID.randomUUID().toString().replace("-", "");
        Instant expiresAt = Instant.now().plusSeconds(passwordResetExpiryMinutes * 60L);

        PasswordResetToken resetToken = PasswordResetToken.builder()
                .token(token)
                .user(user)
                .expiresAt(expiresAt)
                .used(false)
                .build();

        passwordResetTokenRepository.save(resetToken);
        return token;
    }

    @Override
    @Transactional
    public User validateAndConsumePasswordResetToken(String token) {
        PasswordResetToken resetToken = passwordResetTokenRepository.findByToken(token)
                .orElseThrow(() -> new BadRequestException("INVALID_RESET_TOKEN", "Invalid or unknown password reset token."));

        if (!resetToken.isValid()) {
            throw new BadRequestException("RESET_TOKEN_EXPIRED", "Password reset token has expired or already been used.");
        }

        resetToken.setUsed(true);
        passwordResetTokenRepository.save(resetToken);

        return resetToken.getUser();
    }
}
