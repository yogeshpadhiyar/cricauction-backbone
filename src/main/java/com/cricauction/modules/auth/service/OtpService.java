package com.cricauction.modules.auth.service;

import com.cricauction.modules.auth.dto.response.SendOtpResponse;

public interface OtpService {
    SendOtpResponse sendOtp(String mobile, String purpose);
    boolean verifyOtp(String mobile, String otp, String purpose);
    int getOtpExpirySeconds();
}
