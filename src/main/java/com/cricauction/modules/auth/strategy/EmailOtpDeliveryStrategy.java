package com.cricauction.modules.auth.strategy;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class EmailOtpDeliveryStrategy implements OtpDeliveryStrategy {

    @Override
    public boolean supports(String channel) {
        return "EMAIL".equalsIgnoreCase(channel);
    }

    @Override
    public void sendOtp(String email, String otpCode, String purpose) {
        // In production, integrate with SendGrid, Amazon SES, or Spring MailSender
        log.info("[EMAIL OTP SERVICE] Dispatched OTP [{}] to email [{}] for purpose [{}]",
                otpCode, email, purpose);
    }
}
