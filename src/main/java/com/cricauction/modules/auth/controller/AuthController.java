package com.cricauction.modules.auth.controller;

import com.cricauction.common.api.ApiResponse;
import com.cricauction.modules.auth.dto.request.*;
import com.cricauction.modules.auth.dto.response.*;
import com.cricauction.modules.auth.service.AuthService;
import com.cricauction.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * 1. User Sign Up
     * POST /api/v1/auth/signup
     */
    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<SignUpResponse>> signUp(@Valid @RequestBody SignUpRequest request) {
        SignUpResponse response = authService.signUp(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("Account created. Please verify your mobile number.", response));
    }

    /**
     * 2. User Sign In (Login)
     * POST /api/v1/auth/signin
     */
    @PostMapping("/signin")
    public ResponseEntity<ApiResponse<AuthResponse>> signIn(@Valid @RequestBody SignInRequest request) {
        AuthResponse response = authService.signIn(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Google Sign-In & Sign-Up (Direct OAuth2 / ID Token)
     * POST /api/v1/auth/google
     * POST /api/v1/auth/google/signin
     * POST /api/v1/auth/google/signup
     */
    @PostMapping(value = {"/google", "/google/signin", "/google/signup"})
    public ResponseEntity<ApiResponse<AuthResponse>> authenticateWithGoogle(
            @Valid @RequestBody GoogleAuthRequest request) {
        AuthResponse response = authService.authenticateWithGoogle(request);
        return ResponseEntity.ok(ApiResponse.success("Authenticated successfully with Google", response));
    }

    /**
     * 3. Send / Resend OTP
     * POST /api/v1/auth/otp/send
     */
    @PostMapping("/otp/send")
    public ResponseEntity<ApiResponse<SendOtpResponse>> sendOtp(@Valid @RequestBody SendOtpRequest request) {
        SendOtpResponse response = authService.sendOtp(request);
        return ResponseEntity.ok(ApiResponse.success(
                "OTP sent successfully to " + request.getMobile(), response));
    }

    /**
     * 4. Verify OTP
     * POST /api/v1/auth/otp/verify
     */
    @PostMapping("/otp/verify")
    public ResponseEntity<ApiResponse<VerifyOtpResponse>> verifyOtp(@Valid @RequestBody VerifyOtpRequest request) {
        VerifyOtpResponse response = authService.verifyOtp(request);
        return ResponseEntity.ok(ApiResponse.success("Verification successful", response));
    }

    /**
     * 5. Forgot Password
     * POST /api/v1/auth/password/forgot
     */
    @PostMapping("/password/forgot")
    public ResponseEntity<ApiResponse<ForgotPasswordResponse>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        ForgotPasswordResponse response = authService.forgotPassword(request);
        return ResponseEntity.ok(ApiResponse.success("Password reset OTP dispatched", response));
    }

    /**
     * 6. Reset Password
     * POST /api/v1/auth/password/reset
     */
    @PostMapping("/password/reset")
    public ResponseEntity<ApiResponse<Void>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.ok(ApiResponse.message("Password updated successfully. Please log in."));
    }

    /**
     * 7. Refresh Access Token
     * POST /api/v1/auth/refresh-token
     */
    @PostMapping("/refresh-token")
    public ResponseEntity<ApiResponse<TokenRefreshResponse>> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        TokenRefreshResponse response = authService.refreshToken(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 8. User Logout
     * POST /api/v1/auth/logout
     */
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @RequestBody(required = false) LogoutRequest request) {
        authService.logout(currentUser, request);
        return ResponseEntity.ok(ApiResponse.message("Logged out successfully"));
    }

    /**
     * 9. Get Current User Profile
     * GET /api/v1/auth/me
     */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getCurrentUser(
            @AuthenticationPrincipal UserPrincipal currentUser) {
        UserProfileResponse response = authService.getCurrentUser(currentUser);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 10. Switch / Set Active Role
     * PATCH /api/v1/auth/role
     */
    @PatchMapping("/role")
    public ResponseEntity<ApiResponse<RoleResponse>> switchRole(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @Valid @RequestBody RoleSelectionRequest request) {
        RoleResponse response = authService.switchRole(currentUser, request);
        return ResponseEntity.ok(ApiResponse.success("Role switched to " + response.getRole(), response));
    }
}
