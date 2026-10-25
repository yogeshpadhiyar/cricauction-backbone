package com.cricauction.modules.auth.service;

import com.cricauction.modules.auth.dto.response.GoogleUserInfo;

public interface GoogleTokenVerifier {
    /**
     * Verifies the Google ID token and returns authenticated Google user details.
     *
     * @param idToken the Google ID token string
     * @return GoogleUserInfo containing sub, email, name, and profile picture
     */
    GoogleUserInfo verifyToken(String idToken);
}
