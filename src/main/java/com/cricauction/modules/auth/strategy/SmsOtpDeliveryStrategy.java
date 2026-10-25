package com.cricauction.modules.auth.strategy;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class SmsOtpDeliveryStrategy implements OtpDeliveryStrategy {

    @Override
    public boolean supports(String channel) {
        return "SMS".equalsIgnoreCase(channel) || channel == null;
    }

    @Override
    public void sendOtp(String mobile, String otpCode, String purpose) {
        // In production, integrate with Twilio, AWS SNS, MSG91, or Fast2SMS
        log.info("[SMS OTP SERVICE] Dispatched OTP [{}] to mobile [{}] for purpose [{}]",
                otpCode, mobile, purpose);
    }
}
