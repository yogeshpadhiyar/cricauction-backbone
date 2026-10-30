package com.cricauction.modules.auth.controller;

import com.cricauction.modules.auth.dto.request.*;
import com.cricauction.modules.auth.dto.response.*;
import com.cricauction.modules.auth.entity.OtpVerification;
import com.cricauction.modules.auth.entity.User;
import com.cricauction.modules.auth.entity.enums.UserRole;
import com.cricauction.modules.auth.repository.OtpVerificationRepository;
import com.cricauction.modules.auth.repository.PasswordResetTokenRepository;
import com.cricauction.modules.auth.repository.RefreshTokenRepository;
import com.cricauction.modules.auth.repository.RoleRepository;
import com.cricauction.modules.auth.repository.UserRepository;
import com.cricauction.modules.auth.service.TokenService;
import com.cricauction.security.JwtTokenProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private OtpVerificationRepository otpRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private TokenService tokenService;

    @BeforeEach
    void setUp() {
        otpRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        passwordResetTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("1. POST /api/v1/auth/signup - Valid registration sends OTP and creates user")
    void signUp_Success() throws Exception {
        SignUpRequest request = SignUpRequest.builder()
                .fullName("Hardik Patel")
                .email("hardik@example.com")
                .mobile("+919876543210")
                .password("Password123!")
                .confirmPassword("Password123!")
                .agreedToTerms(true)
                .build();

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.userId", notNullValue()))
                .andExpect(jsonPath("$.data.mobile", is("+919876543210")))
                .andExpect(jsonPath("$.data.email", is("hardik@example.com")))
                .andExpect(jsonPath("$.data.isVerified", is(false)));
    }

    @Test
    @DisplayName("1. POST /api/v1/auth/signup - Password mismatch throws 400")
    void signUp_PasswordMismatch() throws Exception {
        SignUpRequest request = SignUpRequest.builder()
                .fullName("Hardik Patel")
                .email("hardik@example.com")
                .mobile("+919876543210")
                .password("Password123!")
                .confirmPassword("DifferentPassword123!")
                .agreedToTerms(true)
                .build();

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.error.code", is("PASSWORDS_DO_NOT_MATCH")));
    }

    @Test
    @DisplayName("1. POST /api/v1/auth/signup - Duplicate email throws 409 Conflict")
    void signUp_DuplicateEmail() throws Exception {
        createTestUser("Hardik Patel", "hardik@example.com", "+919876543210", "Password123!", UserRole.PLAYER);

        SignUpRequest request = SignUpRequest.builder()
                .fullName("Hardik Patel 2")
                .email("hardik@example.com")
                .mobile("+919876543211")
                .password("Password123!")
                .confirmPassword("Password123!")
                .agreedToTerms(true)
                .build();

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.error.code", is("EMAIL_ALREADY_EXISTS")));
    }

    @Test
    @DisplayName("2. POST /api/v1/auth/signin - Successful login via email")
    void signIn_Success_Email() throws Exception {
        createTestUser("Hardik Patel", "hardik@example.com", "+919876543210", "StrongPassword123!", UserRole.ORGANIZER);

        SignInRequest request = SignInRequest.builder()
                .identifier("hardik@example.com")
                .password("StrongPassword123!")
                .rememberMe(true)
                .build();

        mockMvc.perform(post("/api/v1/auth/signin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.accessToken", notNullValue()))
                .andExpect(jsonPath("$.data.refreshToken", notNullValue()))
                .andExpect(jsonPath("$.data.expiresIn", is(86400)))
                .andExpect(jsonPath("$.data.user.email", is("hardik@example.com")))
                .andExpect(jsonPath("$.data.user.roles", hasItem("organizer")));
    }

    @Test
    @DisplayName("2. POST /api/v1/auth/signin - Bad credentials returns 401")
    void signIn_InvalidCredentials() throws Exception {
        createTestUser("Hardik Patel", "hardik@example.com", "+919876543210", "StrongPassword123!", UserRole.PLAYER);

        SignInRequest request = SignInRequest.builder()
                .identifier("hardik@example.com")
                .password("WrongPassword999!")
                .build();

        mockMvc.perform(post("/api/v1/auth/signin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.error.code", is("INVALID_CREDENTIALS")));
    }

    @Test
    @DisplayName("3. POST /api/v1/auth/otp/send - Send OTP successfully")
    void sendOtp_Success() throws Exception {
        SendOtpRequest request = SendOtpRequest.builder()
                .mobile("+919876543210")
                .purpose("signup_verification")
                .build();

        mockMvc.perform(post("/api/v1/auth/otp/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.retryAfterSeconds", is(45)))
                .andExpect(jsonPath("$.data.otpExpiresInSeconds", is(300)));
    }

    @Test
    @DisplayName("3b. POST /api/v1/auth/otp/send - Cooldown enforced on rapid resends and refreshed on subsequent dispatches (FPR-AUTH-OTP-COOLDOWN-BYPASS)")
    void sendOtp_CooldownEnforcedOnSubsequentRequests() throws Exception {
        SendOtpRequest request = SendOtpRequest.builder()
                .mobile("+919876543299")
                .purpose("signup_verification")
                .build();

        // 1. Initial OTP dispatch succeeds
        mockMvc.perform(post("/api/v1/auth/otp/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)));

        // 2. Immediate 2nd request is blocked by cooldown throttle
        mockMvc.perform(post("/api/v1/auth/otp/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.error.code", is("OTP_COOLDOWN_ACTIVE")));

        // 3. Simulate passage of 50 seconds by aging lastSentAt and createdAt
        OtpVerification otp = otpRepository.findByIdentifierAndPurpose("+919876543299", "signup_verification")
                .orElseThrow();
        otp.setLastSentAt(Instant.now().minusSeconds(50));
        otpRepository.save(otp);

        // 4. After 50 seconds, 2nd dispatch succeeds
        mockMvc.perform(post("/api/v1/auth/otp/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)));

        // 5. CRITICAL REGRESSION CHECK: Immediate 3rd request MUST be blocked by cooldown throttle
        // (Previously bypassed because createdAt was updatable=false and remained permanently aged >45s)
        mockMvc.perform(post("/api/v1/auth/otp/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.error.code", is("OTP_COOLDOWN_ACTIVE")));
    }

    @Test
    @DisplayName("4. POST /api/v1/auth/otp/verify - Verify OTP for signup verification")
    void verifyOtp_SignupVerification_Success() throws Exception {
        createTestUser("Hardik Patel", "hardik@example.com", "+919876543210", "Pass123456!", UserRole.PLAYER);

        // Seed OTP
        otpRepository.save(OtpVerification.builder()
                .identifier("+919876543210")
                .mobile("+919876543210")
                .purpose("signup_verification")
                .otpCode("482910")
                .attempts(0)
                .expiresAt(Instant.now().plusSeconds(300))
                .build());

        VerifyOtpRequest request = VerifyOtpRequest.builder()
                .mobile("+919876543210")
                .otp("482910")
                .purpose("signup_verification")
                .build();

        mockMvc.perform(post("/api/v1/auth/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.isVerified", is(true)))
                .andExpect(jsonPath("$.data.accessToken", notNullValue()));
    }

    @Test
    @DisplayName("5. POST /api/v1/auth/password/forgot - Forgot password dispatches OTP")
    void forgotPassword_Success() throws Exception {
        createTestUser("Hardik Patel", "hardik@example.com", "+919876543210", "Pass123456!", UserRole.PLAYER);

        ForgotPasswordRequest request = ForgotPasswordRequest.builder()
                .identifier("hardik@example.com")
                .build();

        mockMvc.perform(post("/api/v1/auth/password/forgot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.maskedContact", notNullValue()))
                .andExpect(jsonPath("$.data.deliveryMethod", is("SMS")));
    }

    @Test
    @DisplayName("6. POST /api/v1/auth/password/reset - Reset password with reset token")
    void resetPassword_Success() throws Exception {
        User user = createTestUser("Hardik Patel", "hardik@example.com", "+919876543210", "OldPassword123!", UserRole.PLAYER);
        String resetToken = tokenService.createPasswordResetToken(user);

        ResetPasswordRequest request = ResetPasswordRequest.builder()
                .resetToken(resetToken)
                .newPassword("NewStrongPassword456!")
                .confirmPassword("NewStrongPassword456!")
                .build();

        mockMvc.perform(post("/api/v1/auth/password/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.message", containsString("Password updated successfully")));
    }

    @Test
    @DisplayName("7. POST /api/v1/auth/refresh-token - Refresh token rotates access token")
    void refreshToken_Success() throws Exception {
        User user = createTestUser("Hardik Patel", "hardik@example.com", "+919876543210", "Password123!", UserRole.PLAYER);
        String refreshToken = tokenService.createRefreshToken(user);

        RefreshTokenRequest request = RefreshTokenRequest.builder()
                .refreshToken(refreshToken)
                .build();

        mockMvc.perform(post("/api/v1/auth/refresh-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.accessToken", notNullValue()))
                .andExpect(jsonPath("$.data.expiresIn", is(86400)));
    }

    @Test
    @DisplayName("8. POST /api/v1/auth/logout - Logout authenticated user")
    void logout_Success() throws Exception {
        User user = createTestUser("Hardik Patel", "hardik@example.com", "+919876543210", "Password123!", UserRole.PLAYER);
        String accessToken = jwtTokenProvider.generateAccessToken(user);
        String refreshToken = tokenService.createRefreshToken(user);

        LogoutRequest request = LogoutRequest.builder()
                .refreshToken(refreshToken)
                .build();

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.message", is("Logged out successfully")));
    }

    @Test
    @DisplayName("9. GET /api/v1/auth/me - Retrieve authenticated user profile")
    void getCurrentUser_Success() throws Exception {
        User user = createTestUser("Hardik Patel", "hardik@example.com", "+919876543210", "Password123!", UserRole.ORGANIZER);
        user.setCity("Surat");
        user.setProfilePhotoUrl("https://cdn.cricauction.app/avatars/usr_1.jpg");
        user = userRepository.save(user);

        String accessToken = jwtTokenProvider.generateAccessToken(user);

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.id", is(user.getPublicUserId())))
                .andExpect(jsonPath("$.data.fullName", is("Hardik Patel")))
                .andExpect(jsonPath("$.data.email", is("hardik@example.com")))
                .andExpect(jsonPath("$.data.city", is("Surat")))
                .andExpect(jsonPath("$.data.roles", hasItem("organizer")));
    }

    @Test
    @DisplayName("10. PATCH /api/v1/auth/role - Switch active role")
    void switchRole_Success() throws Exception {
        User user = createTestUser("Hardik Patel", "hardik@example.com", "+919876543210", "Password123!", UserRole.PLAYER);
        String accessToken = jwtTokenProvider.generateAccessToken(user);

        RoleSelectionRequest request = RoleSelectionRequest.builder()
                .role("organizer")
                .build();

        mockMvc.perform(patch("/api/v1/auth/role")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.roles", hasItem("organizer")));
    }

    @Test
    @DisplayName("10b. PUT /api/v1/auth/roles - Assign multiple roles simultaneously")
    void assignMultipleRoles_Success() throws Exception {
        User user = createTestUser("Hardik Multi", "hardik.multi@example.com", "+919876543290", "Password123!", UserRole.PLAYER);
        String accessToken = jwtTokenProvider.generateAccessToken(user);

        RoleSelectionRequest request = RoleSelectionRequest.builder()
                .roles(List.of("CAPTAIN", "PLAYER"))
                .build();

        mockMvc.perform(put("/api/v1/auth/roles")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.roles", hasItem("captain")))
                .andExpect(jsonPath("$.data.roles", hasItem("player")));
    }

    @Test
    @DisplayName("11. POST /api/v1/auth/google - New user Google Sign-Up creates user and returns tokens")
    void googleAuth_SignUp_Success() throws Exception {
        GoogleAuthRequest request = GoogleAuthRequest.builder()
                .idToken("test-google-token-virat@example.com")
                .role("captain")
                .city("Delhi")
                .build();

        mockMvc.perform(post("/api/v1/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.accessToken", notNullValue()))
                .andExpect(jsonPath("$.data.refreshToken", notNullValue()))
                .andExpect(jsonPath("$.data.user.email", is("virat@example.com")))
                .andExpect(jsonPath("$.data.user.roles", hasItem("captain")))
                .andExpect(jsonPath("$.data.user.city", is("Delhi")))
                .andExpect(jsonPath("$.data.user.authProvider", is("google")))
                .andExpect(jsonPath("$.data.user.isVerified", is(true)));
    }

    @Test
    @DisplayName("12. POST /api/v1/auth/google/signin - Existing user Google Sign-In returns tokens")
    void googleAuth_SignIn_ExistingUser_Success() throws Exception {
        // Pre-create user with same email
        createTestUser("Virat Kohli", "virat@example.com", "+919876543299", "Pass123456!", UserRole.ORGANIZER);

        GoogleAuthRequest request = GoogleAuthRequest.builder()
                .idToken("test-google-token-virat@example.com")
                .build();

        mockMvc.perform(post("/api/v1/auth/google/signin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.accessToken", notNullValue()))
                .andExpect(jsonPath("$.data.user.email", is("virat@example.com")))
                .andExpect(jsonPath("$.data.user.roles", hasItem("organizer")));
    }

    private User createTestUser(String fullName, String email, String mobile, String rawPassword, UserRole role) {
        User user = User.builder()
                .fullName(fullName)
                .email(email)
                .mobile(mobile)
                .passwordHash(passwordEncoder.encode(rawPassword))
                .isMobileVerified(true)
                .isActive(true)
                .build();
        roleRepository.findByName(role).ifPresent(user::addRole);
        return userRepository.save(user);
    }
}
