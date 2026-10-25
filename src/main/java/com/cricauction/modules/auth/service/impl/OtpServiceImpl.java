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

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class OtpServiceImpl implements OtpService {

    private final OtpVerificationRepository otpRepository;
    private final List<OtpDeliveryStrategy> deliveryStrategies;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${cricauction.otp.expiration-seconds:300}")
    private int otpExpirationSeconds;

    @Value("${cricauction.otp.retry-after-seconds:45}")
    private int retryAfterSeconds;

    private static final int MAX_ATTEMPTS = 5;

    @Override
    @Transactional
    public SendOtpResponse sendOtp(String mobile, String purpose) {
        String normalizedPurpose = purpose != null ? purpose.trim().toLowerCase() : "signup_verification";

        // Check if an existing active OTP was sent recently (cooldown throttle)
        Optional<OtpVerification> existingOpt = otpRepository.findByMobileAndPurpose(mobile, normalizedPurpose);
        if (existingOpt.isPresent()) {
            OtpVerification existing = existingOpt.get();
            long secondsSinceCreation = Duration.between(existing.getCreatedAt(), Instant.now()).getSeconds();
            if (secondsSinceCreation < retryAfterSeconds) {
                long waitTime = retryAfterSeconds - secondsSinceCreation;
                throw new BadRequestException("OTP_COOLDOWN_ACTIVE",
                        "Please wait " + waitTime + " seconds before requesting a new OTP.");
            }
        }

        // Generate 6-digit numeric OTP
        String otpCode = String.format("%06d", secureRandom.nextInt(1_000_000));
        Instant expiresAt = Instant.now().plusSeconds(otpExpirationSeconds);

        OtpVerification otpVerification = existingOpt.orElseGet(() ->
                OtpVerification.builder()
                        .mobile(mobile)
                        .purpose(normalizedPurpose)
                        .build()
        );

        otpVerification.setOtpCode(otpCode);
        otpVerification.setAttempts(0);
        otpVerification.setExpiresAt(expiresAt);
        otpRepository.save(otpVerification);

        // Dispatch via Strategy
        dispatchOtp(mobile, otpCode, normalizedPurpose);

        return SendOtpResponse.builder()
                .retryAfterSeconds(retryAfterSeconds)
                .otpExpiresInSeconds(otpExpirationSeconds)
                .build();
    }

    @Override
    @Transactional
    public boolean verifyOtp(String mobile, String otp, String purpose) {
        String normalizedPurpose = purpose != null ? purpose.trim().toLowerCase() : "signup_verification";

        OtpVerification verification = otpRepository.findByMobileAndPurpose(mobile, normalizedPurpose)
                .orElseThrow(() -> new BadRequestException("INVALID_OTP", "No active OTP found for this mobile number."));

        if (verification.isExpired()) {
            otpRepository.deleteByMobileAndPurpose(mobile, normalizedPurpose);
            throw new BadRequestException("OTP_EXPIRED", "OTP has expired. Please request a new OTP.");
        }

        if (verification.getAttempts() >= MAX_ATTEMPTS) {
            otpRepository.deleteByMobileAndPurpose(mobile, normalizedPurpose);
            throw new BadRequestException("MAX_ATTEMPTS_EXCEEDED", "Maximum verification attempts exceeded. Please request a new OTP.");
        }

        if (!verification.getOtpCode().equals(otp.trim())) {
            verification.setAttempts(verification.getAttempts() + 1);
            otpRepository.save(verification);
            int remaining = MAX_ATTEMPTS - verification.getAttempts();
            throw new BadRequestException("INVALID_OTP", "Invalid OTP entered. " + remaining + " attempts remaining.");
        }

        // OTP verified successfully -> cleanup record
        otpRepository.deleteByMobileAndPurpose(mobile, normalizedPurpose);
        return true;
    }

    @Override
    public int getOtpExpirySeconds() {
        return otpExpirationSeconds;
    }

    private void dispatchOtp(String mobile, String otpCode, String purpose) {
        // Strategy selection: find strategy supporting SMS
        deliveryStrategies.stream()
                .filter(strategy -> strategy.supports("SMS"))
                .findFirst()
                .ifPresentOrElse(
                        strategy -> strategy.sendOtp(mobile, otpCode, purpose),
                        () -> log.warn("No OTP delivery strategy found for SMS")
                );
    }
}
