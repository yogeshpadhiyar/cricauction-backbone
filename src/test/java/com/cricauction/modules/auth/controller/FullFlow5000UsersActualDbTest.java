package com.cricauction.modules.auth.controller;

import com.cricauction.modules.auth.dto.request.*;
import com.cricauction.modules.auth.dto.response.AuthResponse;
import com.cricauction.modules.auth.dto.response.RoleResponse;
import com.cricauction.modules.auth.dto.response.TokenRefreshResponse;
import com.cricauction.modules.auth.dto.response.UserProfileResponse;
import com.cricauction.modules.auth.entity.OtpVerification;
import com.cricauction.modules.auth.repository.OtpVerificationRepository;
import com.cricauction.modules.auth.repository.RefreshTokenRepository;
import com.cricauction.modules.auth.repository.UserRepository;
import com.cricauction.modules.auth.service.AuthService;
import com.cricauction.security.UserPrincipal;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AUTOMATION TASK: Full Flow of 5,000 Concurrent Dummy Users against Actual PostgreSQL Database.
 *
 * This test executes real database transactions on PostgreSQL (localhost:5432/cricauction):
 * 1. 1,250 Users: Direct Google OAuth Sign-Up & Sign-In -> Switch Role to CAPTAIN
 * 2. 1,250 Users: Passwordless Mobile + OTP Login -> Fetch Profile (/me)
 * 3. 1,250 Users: Passwordless Email ID + OTP Login -> Switch Role to ORGANIZER
 * 4. 1,250 Users: Standard Sign-Up + Password Sign-In -> Rotate Refresh Token
 *
 * CRITICAL: Database entries are INTENTIONALLY PRESERVED after execution so the user
 * can cross-verify all 5,000 entries directly in PostgreSQL!
 */
@Slf4j
@SpringBootTest
@ActiveProfiles("postgres")
public class FullFlow5000UsersActualDbTest {

    private static final int TOTAL_USERS = 5000;

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private OtpVerificationRepository otpRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final String[] CRICKETER_NAMES = {
            "Rohit Sharma", "Virat Kohli", "Jasprit Bumrah", "Hardik Pandya", "Ravindra Jadeja",
            "Shubman Gill", "Rishabh Pant", "Suryakumar Yadav", "KL Rahul", "Mohammed Shami",
            "Mohammed Siraj", "Axar Patel", "Kuldeep Yadav", "Yashasvi Jaiswal", "Sanju Samson",
            "Shreyas Iyer", "Arshdeep Singh", "Rinku Singh", "Tilak Varma", "Shivam Dube"
    };

    private static final String[] CITIES = {
            "Mumbai", "Delhi", "Bengaluru", "Chennai", "Kolkata", "Ahmedabad",
            "Surat", "Jaipur", "Hyderabad", "Pune", "Lucknow", "Chandigarh"
    };

    @Test
    @DisplayName("AUTOMATION: 5,000 Concurrent Users Full Flow with Actual PostgreSQL DB Persistence")
    void execute5000UsersFullFlowActualDatabase() throws InterruptedException {
        log.info("================================================================================");
        log.info("STARTING AUTOMATION: 5,000 Concurrent Users Full Flow on ACTUAL PostgreSQL DB");
        log.info("Target DB: jdbc:postgresql://localhost:5432/cricauction");
        log.info("================================================================================");

        // Pre-count existing entries
        Long initialUserCount = jdbcTemplate.queryForObject("SELECT count(*) FROM users", Long.class);
        log.info("Initial user count in PostgreSQL: {}", initialUserCount);

        CountDownLatch readyLatch = new CountDownLatch(TOTAL_USERS);
        CountDownLatch startGun = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(TOTAL_USERS);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        List<Long> latenciesMs = Collections.synchronizedList(new ArrayList<>(TOTAL_USERS));

        long overallStartTime;

        // Unique run prefix to guarantee fresh unique records across repeated test runs
        long runPrefix = System.currentTimeMillis() % 1_000_000L;

        // Execute all 5,000 concurrent dummy user journeys using Java 23 Virtual Threads
        try (ExecutorService virtualExecutor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 1; i <= TOTAL_USERS; i++) {
                final int userIndex = i;
                virtualExecutor.submit(() -> {
                    readyLatch.countDown();
                    try {
                        startGun.await(); // Synchronize all 5,000 virtual threads
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }

                    long start = System.nanoTime();
                    try {
                        executeSingleUserJourney(userIndex, runPrefix);
                        long durationMs = (System.nanoTime() - start) / 1_000_000;
                        latenciesMs.add(durationMs);
                        successCount.incrementAndGet();
                    } catch (Exception ex) {
                        log.error("Error executing user journey for user index [{}]: {}", userIndex, ex.getMessage(), ex);
                        failureCount.incrementAndGet();
                    } finally {
                        finishLatch.countDown();
                    }
                });
            }

            assertTrue(readyLatch.await(60, TimeUnit.SECONDS), "Not all 5,000 virtual threads were ready");

            // Release all 5,000 threads simultaneously
            overallStartTime = System.currentTimeMillis();
            startGun.countDown();

            boolean finished = finishLatch.await(180, TimeUnit.SECONDS);
            long totalElapsedMs = System.currentTimeMillis() - overallStartTime;

            assertTrue(finished, "5,000 concurrent flows did not complete within 180s");

            // Generate Benchmark Metrics
            printBenchmarkSummary(TOTAL_USERS, successCount.get(), failureCount.get(), totalElapsedMs, latenciesMs);

            // Verify actual PostgreSQL DB entries without clearing!
            verifyActualPostgresDatabaseEntries();

            assertEquals(TOTAL_USERS, successCount.get(), "All 5,000 user journeys should succeed with 100% rate");
            assertEquals(0, failureCount.get(), "Zero journeys should fail");
        }
    }

    private void executeSingleUserJourney(int index, long runPrefix) {
        String baseName = CRICKETER_NAMES[index % CRICKETER_NAMES.length];
        String fullName = baseName + " #" + index;
        String email = String.format("player_%d_%05d@cricauction.in", runPrefix, index);
        String mobile = String.format("+919%02d%06d", (int)(runPrefix % 90 + 10), index);
        String city = CITIES[index % CITIES.length];

        int journeyType = index % 4;

        switch (journeyType) {
            case 0 -> {
                // Journey 1: Google OAuth Sign-Up & Sign-In -> Switch Role to CAPTAIN
                GoogleAuthRequest googleReq = GoogleAuthRequest.builder()
                        .idToken("test-google-token-" + email)
                        .role("player")
                        .city(city)
                        .build();

                AuthResponse authRes = authService.authenticateWithGoogle(googleReq);
                assertNotNull(authRes.getAccessToken());

                // Perform role switch
                UserPrincipal principal = UserPrincipal.builder()
                        .id(Long.parseLong(authRes.getUser().getId().replace("usr_", "")))
                        .fullName(fullName)
                        .email(email)
                        .role(com.cricauction.modules.auth.entity.enums.UserRole.PLAYER)
                        .isActive(true)
                        .build();

                RoleSelectionRequest roleReq = RoleSelectionRequest.builder().role("captain").build();
                RoleResponse roleRes = authService.switchRole(principal, roleReq);
                assertEquals("captain", roleRes.getRole());
            }
            case 1 -> {
                // Journey 2: Mobile + OTP Passwordless Login -> Get Current Profile
                authService.sendOtp(SendOtpRequest.builder().identifier(mobile).purpose("login").build());

                // Read OTP from database
                OtpVerification otpRecord = otpRepository.findByIdentifierAndPurpose(mobile, "login")
                        .orElseThrow(() -> new IllegalStateException("OTP not found for " + mobile));

                AuthResponse loginRes = authService.loginWithOtp(OtpLoginRequest.builder()
                        .identifier(mobile)
                        .otp(otpRecord.getOtpCode())
                        .role("player")
                        .city(city)
                        .build());

                assertNotNull(loginRes.getAccessToken());

                // Fetch Profile
                UserPrincipal principal = UserPrincipal.builder()
                        .id(Long.parseLong(loginRes.getUser().getId().replace("usr_", "")))
                        .fullName(fullName)
                        .mobile(mobile)
                        .role(com.cricauction.modules.auth.entity.enums.UserRole.PLAYER)
                        .isActive(true)
                        .build();

                UserProfileResponse profile = authService.getCurrentUser(principal);
                assertEquals(mobile, profile.getMobile());
            }
            case 2 -> {
                // Journey 3: Email ID + OTP Passwordless Login -> Switch Role to ORGANIZER
                authService.sendOtp(SendOtpRequest.builder().identifier(email).purpose("login").build());

                OtpVerification otpRecord = otpRepository.findByIdentifierAndPurpose(email, "login")
                        .orElseThrow(() -> new IllegalStateException("OTP not found for " + email));

                AuthResponse loginRes = authService.loginWithOtp(OtpLoginRequest.builder()
                        .identifier(email)
                        .otp(otpRecord.getOtpCode())
                        .role("player")
                        .city(city)
                        .build());

                assertNotNull(loginRes.getAccessToken());

                UserPrincipal principal = UserPrincipal.builder()
                        .id(Long.parseLong(loginRes.getUser().getId().replace("usr_", "")))
                        .fullName(fullName)
                        .email(email)
                        .role(com.cricauction.modules.auth.entity.enums.UserRole.PLAYER)
                        .isActive(true)
                        .build();

                RoleResponse roleRes = authService.switchRole(principal, RoleSelectionRequest.builder().role("organizer").build());
                assertEquals("organizer", roleRes.getRole());
            }
            case 3 -> {
                // Journey 4: Standard Sign-Up + Password Sign-In -> Refresh Token
                String password = "StrongPassword@123";
                SignUpRequest signUpReq = SignUpRequest.builder()
                        .fullName(fullName)
                        .email(email)
                        .mobile(mobile)
                        .password(password)
                        .confirmPassword(password)
                        .agreedToTerms(true)
                        .build();

                authService.signUp(signUpReq);

                // Sign in with credentials
                AuthResponse signInRes = authService.signIn(SignInRequest.builder()
                        .identifier(email)
                        .password(password)
                        .build());

                assertNotNull(signInRes.getAccessToken());
                assertNotNull(signInRes.getRefreshToken());

                // Rotate Refresh Token
                TokenRefreshResponse refreshRes = authService.refreshToken(RefreshTokenRequest.builder()
                        .refreshToken(signInRes.getRefreshToken())
                        .build());
                assertNotNull(refreshRes.getAccessToken());
            }
        }
    }

    private void verifyActualPostgresDatabaseEntries() {
        Long totalUsers = jdbcTemplate.queryForObject("SELECT count(*) FROM users", Long.class);
        Long totalRefreshTokens = jdbcTemplate.queryForObject("SELECT count(*) FROM refresh_tokens", Long.class);

        Long googleUsers = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM users WHERE auth_provider = 'GOOGLE'", Long.class);
        Long localUsers = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM users WHERE auth_provider = 'LOCAL'", Long.class);
        Long captains = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM users WHERE active_role = 'CAPTAIN'", Long.class);
        Long organizers = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM users WHERE active_role = 'ORGANIZER'", Long.class);
        Long players = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM users WHERE active_role = 'PLAYER'", Long.class);

        log.info("================================================================================");
        log.info("ACTUAL POSTGRESQL DATABASE VERIFICATION (NOT CLEARED - PERSISTED IN DB)");
        log.info("================================================================================");
        log.info("Total Users in 'users' Table          : {}", totalUsers);
        log.info("  ├── Google Auth Users (GOOGLE)     : {}", googleUsers);
        log.info("  └── Local Auth Users (LOCAL)        : {}", localUsers);
        log.info("Role Distribution:");
        log.info("  ├── CAPTAINS                        : {}", captains);
        log.info("  ├── ORGANIZERS                      : {}", organizers);
        log.info("  └── PLAYERS                         : {}", players);
        log.info("Total Refresh Tokens in DB            : {}", totalRefreshTokens);
        log.info("================================================================================");

        assertTrue(totalUsers != null && totalUsers >= TOTAL_USERS,
                "Expected at least 5,000 users in PostgreSQL database!");
        assertTrue(totalRefreshTokens != null && totalRefreshTokens > 0,
                "Expected persisted refresh tokens in PostgreSQL database!");
    }

    private void printBenchmarkSummary(int totalRequests, int successCount, int failureCount,
                                       long totalDurationMs, List<Long> latencies) {
        Collections.sort(latencies);

        double throughputRps = (totalRequests / (double) totalDurationMs) * 1000.0;
        long p50 = latencies.get((int) (latencies.size() * 0.50));
        long p90 = latencies.get((int) (latencies.size() * 0.90));
        long p95 = latencies.get((int) (latencies.size() * 0.95));
        long p99 = latencies.get((int) (latencies.size() * 0.99));
        long max = latencies.get(latencies.size() - 1);
        long min = latencies.get(0);

        log.info("--------------------------------------------------------------------------------");
        log.info("CONCURRENCY BENCHMARK RESULTS: 5,000 FULL USER FLOWS (ACTUAL POSTGRESQL DB)");
        log.info("--------------------------------------------------------------------------------");
        log.info("Total Concurrent User Flows : {}", totalRequests);
        log.info("Successful Flows            : {}", successCount);
        log.info("Failed Flows                : {}", failureCount);
        log.info("Success Rate                : {}%", String.format("%.2f", (successCount * 100.0 / totalRequests)));
        log.info("Total Elapsed Time          : {} ms ({} seconds)", totalDurationMs, String.format("%.2f", totalDurationMs / 1000.0));
        log.info("Throughput (RPS)            : {} user flows/sec", String.format("%.2f", throughputRps));
        log.info("Min Latency                 : {} ms", min);
        log.info("Median (p50) Latency        : {} ms", p50);
        log.info("p90 Latency                 : {} ms", p90);
        log.info("p95 Latency                 : {} ms", p95);
        log.info("p99 Latency                 : {} ms", p99);
        log.info("Max Latency                 : {} ms", max);
        log.info("--------------------------------------------------------------------------------");
    }
}
