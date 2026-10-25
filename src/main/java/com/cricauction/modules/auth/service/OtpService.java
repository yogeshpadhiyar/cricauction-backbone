package com.cricauction.modules.auth.service;

import com.cricauction.modules.auth.dto.response.SendOtpResponse;

public interface OtpService {
    SendOtpResponse sendOtp(String identifier, String purpose);
    boolean verifyOtp(String identifier, String otp, String purpose);
    int getOtpExpirySeconds();
}
