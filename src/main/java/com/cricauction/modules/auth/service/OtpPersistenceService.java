package com.cricauction.modules.auth.service;

import com.cricauction.common.exception.BadRequestException;
import com.cricauction.modules.auth.entity.OtpVerification;
import com.cricauction.modules.auth.repository.OtpVerificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * Dedicated Persistence Service for OTP operations.
 * Executes on Master DB (CricAuction-Master-Pool) with minimal hold duration.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class OtpPersistenceService {

    private final OtpVerificationRepository otpRepository;

    public void saveOrUpdateOtp(String target, String purpose, String otpCode, Instant expiresAt, Instant now) {
        boolean isEmail = target.contains("@");
        Optional<OtpVerification> existingOpt = otpRepository.findByIdentifierAndPurpose(target, purpose);

        OtpVerification otpVerification = existingOpt.orElseGet(() ->
                OtpVerification.builder()
                        .identifier(target)
                        .email(isEmail ? target : null)
                        .mobile(isEmail ? null : target)
                        .purpose(purpose)
                        .build()
        );

        if (isEmail) {
            otpVerification.setEmail(target);
        } else {
            otpVerification.setMobile(target);
        }
        otpVerification.setOtpCode(otpCode);
        otpVerification.setAttempts(0);
        otpVerification.setExpiresAt(expiresAt);
        otpVerification.setLastSentAt(now);
        otpRepository.save(otpVerification);
        log.info("Master micro-write: Saved OTP for target [{}] purpose [{}]", target, purpose);
    }

    public boolean verifyAndConsumeOtp(String target, String otp, String purpose, int maxAttempts) {
        OtpVerification verification = otpRepository.findByIdentifierAndPurpose(target, purpose)
                .orElseThrow(() -> new BadRequestException("INVALID_OTP", "No active OTP found for " + target));

        if (verification.isExpired()) {
            otpRepository.deleteByIdentifierAndPurpose(target, purpose);
            throw new BadRequestException("OTP_EXPIRED", "OTP has expired. Please request a new OTP.");
        }

        if (verification.getAttempts() >= maxAttempts) {
            otpRepository.deleteByIdentifierAndPurpose(target, purpose);
            throw new BadRequestException("MAX_ATTEMPTS_EXCEEDED", "Maximum verification attempts exceeded. Please request a new OTP.");
        }

        if (!verification.getOtpCode().equals(otp.trim())) {
            verification.setAttempts(verification.getAttempts() + 1);
            otpRepository.save(verification);
            int remaining = maxAttempts - verification.getAttempts();
            throw new BadRequestException("INVALID_OTP", "Invalid OTP entered. " + remaining + " attempts remaining.");
        }

        // OTP verified successfully -> cleanup record
        otpRepository.deleteByIdentifierAndPurpose(target, purpose);
        log.info("Master micro-write: OTP consumed and deleted for target [{}] purpose [{}]", target, purpose);
        return true;
    }
}
