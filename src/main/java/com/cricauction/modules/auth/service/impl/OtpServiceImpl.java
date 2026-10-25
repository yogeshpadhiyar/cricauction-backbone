package com.cricauction.modules.auth.service.impl;

import com.cricauction.common.exception.BadRequestException;
import com.cricauction.modules.auth.dto.response.SendOtpResponse;
import com.cricauction.modules.auth.entity.OtpVerification;
import com.cricauction.modules.auth.repository.OtpVerificationRepository;
import com.cricauction.modules.auth.service.OtpService;
import com.cricauction.modules.auth.strategy.OtpDeliveryStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
@RequiredArgsConstructor
public class OtpServiceImpl implements OtpService {

    private final OtpVerificationRepository otpRepository;
    private final List<OtpDeliveryStrategy> deliveryStrategies;

    @Value("${cricauction.otp.expiration-seconds:300}")
    private int otpExpirationSeconds;

    @Value("${cricauction.otp.retry-after-seconds:45}")
    private int retryAfterSeconds;

    private static final int MAX_ATTEMPTS = 5;

    @Override
    @Transactional
    public SendOtpResponse sendOtp(String identifier, String purpose) {
        if (identifier == null || identifier.isBlank()) {
            throw new BadRequestException("INVALID_IDENTIFIER", "Target mobile number or email is required.");
        }

        String target = identifier.trim().toLowerCase();
        String normalizedPurpose = purpose != null ? purpose.trim().toLowerCase() : "login";

        // Check cooldown throttle
        Optional<OtpVerification> existingOpt = otpRepository.findByIdentifierAndPurpose(target, normalizedPurpose);
        if (existingOpt.isPresent()) {
            OtpVerification existing = existingOpt.get();
            long secondsSinceCreation = Duration.between(existing.getCreatedAt(), Instant.now()).getSeconds();
            if (secondsSinceCreation < retryAfterSeconds) {
                long waitTime = retryAfterSeconds - secondsSinceCreation;
                throw new BadRequestException("OTP_COOLDOWN_ACTIVE",
                        "Please wait " + waitTime + " seconds before requesting a new OTP.");
            }
        }

        // Generate 6-digit numeric OTP using lock-free ThreadLocalRandom
        String otpCode = String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
        Instant expiresAt = Instant.now().plusSeconds(otpExpirationSeconds);

        OtpVerification otpVerification = existingOpt.orElseGet(() ->
                OtpVerification.builder()
                        .identifier(target)
                        .mobile(target.contains("@") ? null : target)
                        .purpose(normalizedPurpose)
                        .build()
        );

        otpVerification.setOtpCode(otpCode);
        otpVerification.setAttempts(0);
        otpVerification.setExpiresAt(expiresAt);
        otpRepository.save(otpVerification);

        // Dispatch via Strategy (SMS or EMAIL depending on identifier format)
        dispatchOtp(target, otpCode, normalizedPurpose);

        return SendOtpResponse.builder()
                .retryAfterSeconds(retryAfterSeconds)
                .otpExpiresInSeconds(otpExpirationSeconds)
                .build();
    }

    @Override
    @Transactional
    public boolean verifyOtp(String identifier, String otp, String purpose) {
        if (identifier == null || identifier.isBlank()) {
            throw new BadRequestException("INVALID_IDENTIFIER", "Mobile number or email is required.");
        }
        if (otp == null || otp.isBlank()) {
            throw new BadRequestException("INVALID_OTP", "OTP code cannot be empty.");
        }

        String target = identifier.trim().toLowerCase();
        String normalizedPurpose = purpose != null ? purpose.trim().toLowerCase() : "login";

        OtpVerification verification = otpRepository.findByIdentifierAndPurpose(target, normalizedPurpose)
                .orElseThrow(() -> new BadRequestException("INVALID_OTP", "No active OTP found for " + target));

        if (verification.isExpired()) {
            otpRepository.deleteByIdentifierAndPurpose(target, normalizedPurpose);
            throw new BadRequestException("OTP_EXPIRED", "OTP has expired. Please request a new OTP.");
        }

        if (verification.getAttempts() >= MAX_ATTEMPTS) {
            otpRepository.deleteByIdentifierAndPurpose(target, normalizedPurpose);
            throw new BadRequestException("MAX_ATTEMPTS_EXCEEDED", "Maximum verification attempts exceeded. Please request a new OTP.");
        }

        if (!verification.getOtpCode().equals(otp.trim())) {
            verification.setAttempts(verification.getAttempts() + 1);
            otpRepository.save(verification);
            int remaining = MAX_ATTEMPTS - verification.getAttempts();
            throw new BadRequestException("INVALID_OTP", "Invalid OTP entered. " + remaining + " attempts remaining.");
        }

        // OTP verified successfully -> cleanup record
        otpRepository.deleteByIdentifierAndPurpose(target, normalizedPurpose);
        return true;
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
