package com.cricauction.modules.auth.service;

import com.cricauction.common.exception.BadRequestException;
import com.cricauction.modules.auth.entity.OtpVerification;
import com.cricauction.modules.auth.repository.OtpVerificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Dedicated Read Service for OTP lookups and throttle checks.
 * Routes directly to Read Replica (CricAuction-Replica-Pool).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OtpQueryService {

    private final OtpVerificationRepository otpRepository;

    public void validateCooldown(String target, String purpose, int retryAfterSeconds) {
        Optional<OtpVerification> existingOpt = otpRepository.findByIdentifierAndPurpose(target, purpose);
        if (existingOpt.isPresent()) {
            OtpVerification existing = existingOpt.get();
            Instant referenceTime = existing.getLastSentAt() != null ? existing.getLastSentAt() : existing.getCreatedAt();
            if (referenceTime != null) {
                long secondsSinceLast = Duration.between(referenceTime, Instant.now()).getSeconds();
                if (secondsSinceLast < retryAfterSeconds) {
                    long waitTime = retryAfterSeconds - secondsSinceLast;
                    throw new BadRequestException("OTP_COOLDOWN_ACTIVE",
                            "Please wait " + waitTime + " seconds before requesting a new OTP.");
                }
            }
        }
    }
}
