package com.cricauction.modules.auth.controller;

import com.cricauction.common.resilience.CircuitBreaker;
import com.cricauction.common.resilience.RateLimiter;
import com.cricauction.common.resilience.ResilienceRetry;
import com.cricauction.modules.auth.dto.request.*;
import com.cricauction.modules.auth.entity.OtpVerification;
import com.cricauction.modules.auth.entity.User;
import com.cricauction.modules.auth.entity.enums.UserRole;
import com.cricauction.modules.auth.repository.OtpVerificationRepository;
import com.cricauction.modules.auth.repository.PasswordResetTokenRepository;
import com.cricauction.modules.auth.repository.RefreshTokenRepository;
import com.cricauction.modules.auth.repository.UserRepository;
import com.cricauction.security.JwtTokenProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Real-Time User Journey Test Suite.
 * Validates complete end-to-end user lifecycles:
 * - Mobile + OTP passwordless login
 * - Email ID + OTP passwordless login
 * - Google direct sign-in & sign-up
 * - DDoS defense / Rate Limiter HTTP 429
 * - Resilience Circuit Breaker & Retry mechanisms
 */
@SpringBootTest
@AutoConfigureMockMvc
public class RealTimeUserJourneyTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OtpVerificationRepository otpRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private RateLimiter rateLimiter;

    @Autowired
    private CircuitBreaker circuitBreaker;

    @Autowired
    private ResilienceRetry resilienceRetry;

    @BeforeEach
    void setUp() {
        otpRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        passwordResetTokenRepository.deleteAll();
        userRepository.deleteAll();
        rateLimiter.reset("127.0.0.1");
        circuitBreaker.reset();
    }

    @Test
    @DisplayName("SCENARIO 1: Real-Time Mobile + OTP Passwordless Login Flow")
    void scenario_MobileOtp_Login_Flow() throws Exception {
        String mobile = "+919876543210";

        // Step 1: User requests OTP for Mobile login
        SendOtpRequest sendOtpRequest = SendOtpRequest.builder()
                .identifier(mobile)
                .purpose("login")
                .build();

        mockMvc.perform(post("/api/v1/auth/otp/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sendOtpRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.retryAfterSeconds", is(45)));

        // Retrieve generated OTP from repository
        OtpVerification otpVerification = otpRepository.findByIdentifierAndPurpose(mobile, "login")
                .orElseThrow(() -> new AssertionError("OTP was not saved in repository"));
        String otpCode = otpVerification.getOtpCode();

        // Step 2: User submits Mobile + OTP login
        OtpLoginRequest loginRequest = OtpLoginRequest.builder()
                .identifier(mobile)
                .otp(otpCode)
                .role("player")
                .city("Ahmedabad")
                .build();

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/otp/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.accessToken", notNullValue()))
                .andExpect(jsonPath("$.data.refreshToken", notNullValue()))
                .andExpect(jsonPath("$.data.user.mobile", is(mobile)))
                .andExpect(jsonPath("$.data.user.isVerified", is(true)))
                .andReturn();

        String responseBody = loginResult.getResponse().getContentAsString();
        String accessToken = objectMapper.readTree(responseBody).path("data").path("accessToken").asText();

        // Step 3: Access protected Profile API with JWT
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mobile", is(mobile)))
                .andExpect(jsonPath("$.data.roles", hasItem("player")));
    }

    @Test
    @DisplayName("SCENARIO 2: Real-Time Email ID + OTP Passwordless Login Flow")
    void scenario_EmailOtp_Login_Flow() throws Exception {
        String email = "shubman@cricauction.test";

        // Step 1: Send OTP to Email
        SendOtpRequest sendOtpRequest = SendOtpRequest.builder()
                .identifier(email)
                .purpose("login")
                .build();

        mockMvc.perform(post("/api/v1/auth/otp/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sendOtpRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)));

        // Retrieve generated OTP
        OtpVerification otpVerification = otpRepository.findByIdentifierAndPurpose(email, "login")
                .orElseThrow(() -> new AssertionError("Email OTP was not saved"));
        String otpCode = otpVerification.getOtpCode();

        // Step 2: User logs in using Email + OTP
        OtpLoginRequest loginRequest = OtpLoginRequest.builder()
                .identifier(email)
                .otp(otpCode)
                .role("captain")
                .city("Chandigarh")
                .build();

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/otp/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.accessToken", notNullValue()))
                .andExpect(jsonPath("$.data.user.email", is(email)))
                .andExpect(jsonPath("$.data.user.roles", hasItem("captain")))
                .andExpect(jsonPath("$.data.user.isVerified", is(true)))
                .andReturn();

        String responseBody = loginResult.getResponse().getContentAsString();
        String accessToken = objectMapper.readTree(responseBody).path("data").path("accessToken").asText();

        // Step 3: Switch Active Role to Organizer
        RoleSelectionRequest roleRequest = RoleSelectionRequest.builder()
                .role("organizer")
                .build();

        mockMvc.perform(patch("/api/v1/auth/role")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(roleRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roles", hasItem("organizer")));
    }

    @Test
    @DisplayName("SCENARIO 3: Real-Time Google Direct OAuth Sign-In & Sign-Up Flow")
    void scenario_GoogleOAuth_Flow() throws Exception {
        GoogleAuthRequest googleRequest = GoogleAuthRequest.builder()
                .idToken("test-google-token-jasprit.bumrah@gmail.com")
                .role("player")
                .city("Ahmedabad")
                .build();

        // Step 1: Sign up & login directly via Google
        MvcResult googleResult = mockMvc.perform(post("/api/v1/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(googleRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.accessToken", notNullValue()))
                .andExpect(jsonPath("$.data.user.email", is("jasprit.bumrah@gmail.com")))
                .andExpect(jsonPath("$.data.user.authProvider", is("google")))
                .andExpect(jsonPath("$.data.user.isVerified", is(true)))
                .andReturn();

        String responseBody = googleResult.getResponse().getContentAsString();
        String refreshToken = objectMapper.readTree(responseBody).path("data").path("refreshToken").asText();

        // Step 2: Rotate Refresh Token
        RefreshTokenRequest refreshReq = RefreshTokenRequest.builder()
                .refreshToken(refreshToken)
                .build();

        mockMvc.perform(post("/api/v1/auth/refresh-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.accessToken", notNullValue()));
    }

    @Test
    @DisplayName("SCENARIO 4: DDoS Defense - Rate Limiter intercepts flood with HTTP 429")
    void scenario_RateLimiter_DDoS_Protection() throws Exception {
        String testIp = "198.51.100.42";
        SignInRequest signInRequest = SignInRequest.builder()
                .identifier("attacker@cricauction.test")
                .password("RandomPass123!")
                .build();

        String body = objectMapper.writeValueAsString(signInRequest);

        // Send 60 requests up to limit
        for (int i = 0; i < 60; i++) {
            rateLimiter.tryAcquire(testIp, "/api/v1/auth/signin");
        }

        // 61st request should be rejected by rate limiter
        mockMvc.perform(post("/api/v1/auth/signin")
                        .header("X-Forwarded-For", testIp)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(header().string("X-Rate-Limit-Remaining", "0"))
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.error.code", is("RATE_LIMIT_EXCEEDED")));
    }

    @Test
    @DisplayName("SCENARIO 5: Resilience Fundamentals - Circuit Breaker and Exponential Retry")
    void scenario_Resilience_CircuitBreaker_And_Retry() {
        // Test Retry: Recovers on 3rd attempt
        AtomicInteger attemptCounter = new AtomicInteger(0);
        String retryResult = resilienceRetry.executeWithRetry("ExternalSmsGateway", () -> {
            int attempt = attemptCounter.incrementAndGet();
            if (attempt < 3) {
                throw new RuntimeException("Transient Network Glitch on attempt " + attempt);
            }
            return "SMS_DELIVERED_SUCCESSFULLY";
        }, 3, 20L, 2.0, ex -> true);

        assertEquals("SMS_DELIVERED_SUCCESSFULLY", retryResult);
        assertEquals(3, attemptCounter.get());

        // Test Circuit Breaker: Trip to OPEN on repeated failures, execute fallback immediately
        circuitBreaker.reset();
        assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker.getState());

        // Trigger 5 failures to trip the circuit breaker
        for (int i = 0; i < 5; i++) {
            circuitBreaker.execute("ExternalGoogleAPI", () -> {
                throw new RuntimeException("Simulated 500 downstream error");
            }, () -> "FALLBACK_CALLED");
        }

        // Breaker should now be OPEN
        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.getState());

        // Call executes fallback without calling failing supplier
        String fallbackResponse = circuitBreaker.execute("ExternalGoogleAPI",
                () -> { fail("Supplier should not be called when breaker is OPEN"); return "FAIL"; },
                () -> "FALLBACK_CIRCUIT_OPEN"
        );
        assertEquals("FALLBACK_CIRCUIT_OPEN", fallbackResponse);
    }
}
