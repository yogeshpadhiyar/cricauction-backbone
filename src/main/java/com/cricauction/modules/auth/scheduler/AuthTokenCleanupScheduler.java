package com.cricauction.modules.auth.scheduler;

import com.cricauction.modules.auth.repository.OtpVerificationRepository;
import com.cricauction.modules.auth.repository.PasswordResetTokenRepository;
import com.cricauction.modules.auth.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Scheduled cleanup worker to purge expired and dead records from authentication tables.
 * Prevents indefinite table bloat and keeps database indexes compact.
 */
@Slf4j
@Component
@EnableScheduling
@RequiredArgsConstructor
public class AuthTokenCleanupScheduler {

    private final OtpVerificationRepository otpRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;

    /**
     * Executes periodically to purge abandoned/expired OTPs, expired/revoked refresh tokens,
     * and used/expired reset tokens.
     */
    @Scheduled(cron = "${cricauction.auth.cleanup-cron:0 0 * * * *}")
    @Transactional
    public void purgeExpiredAuthRecords() {
        Instant now = Instant.now();
        log.info("[AUTH-CLEANUP] Starting scheduled purge of expired authentication records at {}", now);

        try {
            otpRepository.deleteExpiredOtps(now);
            refreshTokenRepository.deleteExpiredOrRevokedTokens(now);
            passwordResetTokenRepository.deleteExpiredOrUsedTokens(now);
            log.info("[AUTH-CLEANUP] Successfully purged expired OTPs, refresh tokens, and password reset tokens.");
        } catch (Exception ex) {
            log.error("[AUTH-CLEANUP] Failed to complete scheduled auth records purge: {}", ex.getMessage(), ex);
        }
    }
}

