package com.cricauction.modules.auth.service;

import com.cricauction.modules.auth.dto.response.TokenRefreshResponse;
import com.cricauction.modules.auth.entity.User;

public interface TokenService {
    String createRefreshToken(User user);
    TokenRefreshResponse refreshToken(String refreshToken);
    void revokeRefreshToken(String refreshToken);
    String createPasswordResetToken(User user);
    User validateAndConsumePasswordResetToken(String token);
}
