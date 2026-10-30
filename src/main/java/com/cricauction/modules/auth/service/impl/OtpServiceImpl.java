package com.cricauction.modules.auth.service.impl;

import com.cricauction.common.exception.BadRequestException;
import com.cricauction.modules.auth.dto.response.SendOtpResponse;
import com.cricauction.modules.auth.service.OtpPersistenceService;
import com.cricauction.modules.auth.service.OtpQueryService;
import com.cricauction.modules.auth.service.OtpService;
import com.cricauction.modules.auth.strategy.OtpDeliveryStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;

/**
 * Orchestrator Service for OTP generation, delivery, and verification implementing Strategy 1 (Read-Before-Write).
 * - Cooldown throttle check routes to Read Replica (OtpQueryService).
 * - OTP generation executes in-memory.
 * - DB write executed via fast micro-transaction on Master DB (OtpPersistenceService).
 * - SMS / Email network delivery executes completely OUTSIDE database transactions (Zero DB connection held).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OtpServiceImpl implements OtpService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final OtpQueryService otpQueryService;
    private final OtpPersistenceService otpPersistenceService;
    private final List<OtpDeliveryStrategy> deliveryStrategies;

    @Value("${cricauction.otp.expiration-seconds:300}")
    private int otpExpirationSeconds;

    @Value("${cricauction.otp.retry-after-seconds:45}")
    private int retryAfterSeconds;

    private static final int MAX_ATTEMPTS = 5;

    @Override
    public SendOtpResponse sendOtp(String identifier, String purpose) {
        if (identifier == null || identifier.isBlank()) {
            throw new BadRequestException("INVALID_IDENTIFIER", "Target mobile number or email is required.");
        }

        String target = identifier.trim().toLowerCase();
        String normalizedPurpose = purpose != null ? purpose.trim().toLowerCase() : "login";

        // Step 1: Read Replica throttle check (Zero Master connection)
        otpQueryService.validateCooldown(target, normalizedPurpose, retryAfterSeconds);

        // Step 2: Generate 6-digit numeric OTP using cryptographically secure SecureRandom
        String otpCode = String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(otpExpirationSeconds);

        // Step 3: Fast micro-write on Master DB (~1 ms connection hold)
        otpPersistenceService.saveOrUpdateOtp(target, normalizedPurpose, otpCode, expiresAt, now);

        // Step 4: Dispatch via external SMS/Email network service OUTSIDE DB transaction (Zero connection held)
        dispatchOtp(target, otpCode, normalizedPurpose);

        return SendOtpResponse.builder()
                .retryAfterSeconds(retryAfterSeconds)
                .otpExpiresInSeconds(otpExpirationSeconds)
                .build();
    }

    @Override
    public boolean verifyOtp(String identifier, String otp, String purpose) {
        if (identifier == null || identifier.isBlank()) {
            throw new BadRequestException("INVALID_IDENTIFIER", "Mobile number or email is required.");
        }
        if (otp == null || otp.isBlank()) {
            throw new BadRequestException("INVALID_OTP", "OTP code cannot be empty.");
        }

        String target = identifier.trim().toLowerCase();
        String normalizedPurpose = purpose != null ? purpose.trim().toLowerCase() : "login";

        return otpPersistenceService.verifyAndConsumeOtp(target, otp, normalizedPurpose, MAX_ATTEMPTS);
    }

    @Override
    public int getOtpExpirySeconds() {
        return otpExpirationSeconds;
    }

    private void dispatchOtp(String recipient, String otpCode, String purpose) {
        String channel = (recipient != null && recipient.contains("@")) ? "EMAIL" : "SMS";
        deliveryStrategies.stream()
                .filter(strategy -> strategy.supports(channel))
                .findFirst()
                .ifPresentOrElse(
                        strategy -> strategy.sendOtp(recipient, otpCode, purpose),
                        () -> log.warn("No OTP delivery strategy found for channel [{}]", channel)
                );
    }
}
