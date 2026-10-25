package com.cricauction.modules.auth.strategy;

public interface OtpDeliveryStrategy {
    boolean supports(String channel);
    void sendOtp(String recipient, String otpCode, String purpose);
}
