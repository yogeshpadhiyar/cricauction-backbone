package com.cricauction.modules.auth.service;

import com.cricauction.modules.auth.dto.request.GoogleAuthRequest;
import com.cricauction.modules.auth.dto.response.AuthResponse;

public interface GoogleAuthService {
    /**
     * Authenticates or registers a user via Google ID token.
     * If user exists, signs them in.
     * If user does not exist, registers them and returns tokens.
     *
     * @param request GoogleAuthRequest containing Google ID token and optional role/city
     * @return AuthResponse with JWT accessToken, refreshToken, expiresIn, and user profile
     */
    AuthResponse authenticateWithGoogle(GoogleAuthRequest request);
}
