package com.cricauction.modules.auth.scheduler;

import com.cricauction.modules.auth.repository.OtpVerificationRepository;
import com.cricauction.modules.auth.repository.PasswordResetTokenRepository;
import com.cricauction.modules.auth.repository.RefreshTokenRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthTokenCleanupSchedulerTest {

    @Mock
    private OtpVerificationRepository otpRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @InjectMocks
    private AuthTokenCleanupScheduler cleanupScheduler;

    @Test
    @DisplayName("Issue 7: Scheduled purge calls cleanup methods on all 3 auth repositories")
    void testPurgeExpiredAuthRecordsCallsAllRepositories() {
        cleanupScheduler.purgeExpiredAuthRecords();

        verify(otpRepository, times(1)).deleteExpiredOtps(any(Instant.class));
        verify(refreshTokenRepository, times(1)).deleteExpiredOrRevokedTokens(any(Instant.class));
        verify(passwordResetTokenRepository, times(1)).deleteExpiredOrUsedTokens(any(Instant.class));
    }

    @Test
    @DisplayName("Issue 7: Scheduled purge handles exceptions gracefully without terminating")
    void testPurgeHandlesExceptionGracefully() {
        doThrow(new RuntimeException("Database timeout")).when(otpRepository).deleteExpiredOtps(any(Instant.class));

        // Should not throw or crash
        cleanupScheduler.purgeExpiredAuthRecords();

        verify(otpRepository, times(1)).deleteExpiredOtps(any(Instant.class));
    }
}

