package com.cricauction.modules.auth.service;

import com.cricauction.modules.auth.dto.request.*;
import com.cricauction.modules.auth.dto.response.*;
import com.cricauction.security.UserPrincipal;

public interface AuthService {
    SignUpResponse signUp(SignUpRequest request);
    AuthResponse signIn(SignInRequest request);
    SendOtpResponse sendOtp(SendOtpRequest request);
    VerifyOtpResponse verifyOtp(VerifyOtpRequest request);
    ForgotPasswordResponse forgotPassword(ForgotPasswordRequest request);
    void resetPassword(ResetPasswordRequest request);
    TokenRefreshResponse refreshToken(RefreshTokenRequest request);
    void logout(UserPrincipal currentUser, LogoutRequest request);
    UserProfileResponse getCurrentUser(UserPrincipal currentUser);
    RoleResponse switchRole(UserPrincipal currentUser, RoleSelectionRequest request);
    AuthResponse authenticateWithGoogle(GoogleAuthRequest request);
    AuthResponse loginWithOtp(OtpLoginRequest request);
}
