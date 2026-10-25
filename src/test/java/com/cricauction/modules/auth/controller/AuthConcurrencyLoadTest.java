package com.cricauction.modules.auth.controller;

import com.cricauction.modules.auth.dto.request.GoogleAuthRequest;
import com.cricauction.modules.auth.dto.request.SignInRequest;
import com.cricauction.modules.auth.entity.User;
import com.cricauction.modules.auth.entity.enums.UserRole;
import com.cricauction.modules.auth.repository.OtpVerificationRepository;
import com.cricauction.modules.auth.repository.PasswordResetTokenRepository;
import com.cricauction.modules.auth.repository.RefreshTokenRepository;
import com.cricauction.modules.auth.repository.UserRepository;
import com.cricauction.security.JwtTokenProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * High-Concurrency Load Testing Suite for CricAuction Authentication APIs.
 * Simulates exactly 2,000 concurrent users hitting endpoints simultaneously
 * using Java 23 Virtual Threads and evaluates throughput and latency percentiles.
 */
@Slf4j
@SpringBootTest
@AutoConfigureMockMvc
public class AuthConcurrencyLoadTest {

    private static final int CONCURRENT_USERS = 2000;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private OtpVerificationRepository otpRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        otpRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        passwordResetTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("CONCURRENCY TEST: 2,000 Concurrent Users Sign-In (POST /api/v1/auth/signin)")
    void test2000ConcurrentUsers_SignIn() throws InterruptedException {
        log.info("================================================================================");
        log.info("STARTING LOAD TEST: 2,000 Concurrent Users Sign-In");
        log.info("================================================================================");

        // Seed benchmark user
        User user = User.builder()
                .fullName("Load Test Player")
                .email("player_loadtest@example.com")
                .mobile("+919999900000")
                .passwordHash(passwordEncoder.encode("SecurePass123!"))
                .activeRole(UserRole.PLAYER)
                .isMobileVerified(true)
                .isActive(true)
                .build();
        userRepository.save(user);

        SignInRequest signInRequest = SignInRequest.builder()
                .identifier("player_loadtest@example.com")
                .password("SecurePass123!")
                .build();

        String requestJson;
        try {
            requestJson = objectMapper.writeValueAsString(signInRequest);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        CountDownLatch readyLatch = new CountDownLatch(CONCURRENT_USERS);
        CountDownLatch startGun = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(CONCURRENT_USERS);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        List<Long> latenciesMs = Collections.synchronizedList(new ArrayList<>(CONCURRENT_USERS));

        long overallStartTime;

        try (ExecutorService virtualExecutor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < CONCURRENT_USERS; i++) {
                virtualExecutor.submit(() -> {
                    readyLatch.countDown();
                    try {
                        startGun.await(); // Hold all 2,000 virtual threads until start signal
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }

                    long start = System.nanoTime();
                    try {
                        MvcResult result = mockMvc.perform(post("/api/v1/auth/signin")
                                        .header("X-Bypass-Rate-Limit", "true")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(requestJson))
                                .andReturn();

                        long durationMs = (System.nanoTime() - start) / 1_000_000;
                        latenciesMs.add(durationMs);

                        if (result.getResponse().getStatus() == 200) {
                            successCount.incrementAndGet();
                        } else {
                            failureCount.incrementAndGet();
                        }
                    } catch (Exception e) {
                        failureCount.incrementAndGet();
                    } finally {
                        finishLatch.countDown();
                    }
                });
            }

            // Wait for all 2,000 virtual threads to be initialized and primed
            assertTrue(readyLatch.await(30, TimeUnit.SECONDS), "Not all virtual threads were ready in time");

            // FIRE START GUN: All 2,000 virtual threads execute in the exact same millisecond
            overallStartTime = System.currentTimeMillis();
            startGun.countDown();

            // Wait for all 2,000 requests to complete
            boolean finishedInTime = finishLatch.await(60, TimeUnit.SECONDS);
            long totalElapsedMs = System.currentTimeMillis() - overallStartTime;

            assertTrue(finishedInTime, "2,000 concurrent requests did not complete within 60s");

            printLoadTestReport("2,000 Concurrent Sign-In Requests", CONCURRENT_USERS, successCount.get(),
                    failureCount.get(), totalElapsedMs, latenciesMs);

            assertEquals(CONCURRENT_USERS, successCount.get(), "All 2,000 concurrent requests should succeed (200 OK)");
            assertEquals(0, failureCount.get(), "Zero requests should fail");
        }
    }

    @Test
    @DisplayName("CONCURRENCY TEST: 2,000 Concurrent Users Get Profile (GET /api/v1/auth/me)")
    void test2000ConcurrentUsers_GetProfile() throws InterruptedException {
        log.info("================================================================================");
        log.info("STARTING LOAD TEST: 2,000 Concurrent Users Get Current Profile (JWT Auth)");
        log.info("================================================================================");

        User user = User.builder()
                .fullName("Auction User")
                .email("auction_loadtest@example.com")
                .mobile("+919999900001")
                .passwordHash(passwordEncoder.encode("Pass123!"))
                .activeRole(UserRole.CAPTAIN)
                .isMobileVerified(true)
                .isActive(true)
                .build();
        user = userRepository.save(user);

        String accessToken = jwtTokenProvider.generateAccessToken(user);

        CountDownLatch readyLatch = new CountDownLatch(CONCURRENT_USERS);
        CountDownLatch startGun = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(CONCURRENT_USERS);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        List<Long> latenciesMs = Collections.synchronizedList(new ArrayList<>(CONCURRENT_USERS));

        try (ExecutorService virtualExecutor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < CONCURRENT_USERS; i++) {
                virtualExecutor.submit(() -> {
                    readyLatch.countDown();
                    try {
                        startGun.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }

                    long start = System.nanoTime();
                    try {
                        MvcResult result = mockMvc.perform(get("/api/v1/auth/me")
                                        .header("X-Bypass-Rate-Limit", "true")
                                        .header("Authorization", "Bearer " + accessToken))
                                .andReturn();

                        long durationMs = (System.nanoTime() - start) / 1_000_000;
                        latenciesMs.add(durationMs);

                        if (result.getResponse().getStatus() == 200) {
                            successCount.incrementAndGet();
                        } else {
                            failureCount.incrementAndGet();
                        }
                    } catch (Exception e) {
                        failureCount.incrementAndGet();
                    } finally {
                        finishLatch.countDown();
                    }
                });
            }

            assertTrue(readyLatch.await(30, TimeUnit.SECONDS));

            long overallStartTime = System.currentTimeMillis();
            startGun.countDown();

            boolean finishedInTime = finishLatch.await(60, TimeUnit.SECONDS);
            long totalElapsedMs = System.currentTimeMillis() - overallStartTime;

            assertTrue(finishedInTime, "2,000 concurrent profile requests did not complete within 60s");

            printLoadTestReport("2,000 Concurrent Profile Requests", CONCURRENT_USERS, successCount.get(),
                    failureCount.get(), totalElapsedMs, latenciesMs);

            assertEquals(CONCURRENT_USERS, successCount.get(), "All 2,000 requests should succeed");
            assertEquals(0, failureCount.get(), "Zero requests should fail");
        }
    }

    @Test
    @DisplayName("CONCURRENCY TEST: 2,000 Concurrent Users Google Sign-In (POST /api/v1/auth/google)")
    void test2000ConcurrentUsers_GoogleAuth() throws InterruptedException {
        log.info("================================================================================");
        log.info("STARTING LOAD TEST: 2,000 Concurrent Users Google Sign-In");
        log.info("================================================================================");

        // Pre-create user for Google sign-in
        User user = User.builder()
                .fullName("Google User")
                .email("google_loadtest@example.com")
                .googleId("google_sub_loadtest_123")
                .authProvider(com.cricauction.modules.auth.entity.enums.AuthProvider.GOOGLE)
                .activeRole(UserRole.PLAYER)
                .isEmailVerified(true)
                .isActive(true)
                .build();
        userRepository.save(user);

        GoogleAuthRequest googleRequest = GoogleAuthRequest.builder()
                .idToken("test-google-token-google_loadtest@example.com")
                .build();

        String requestJson;
        try {
            requestJson = objectMapper.writeValueAsString(googleRequest);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        CountDownLatch readyLatch = new CountDownLatch(CONCURRENT_USERS);
        CountDownLatch startGun = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(CONCURRENT_USERS);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        List<Long> latenciesMs = Collections.synchronizedList(new ArrayList<>(CONCURRENT_USERS));

        try (ExecutorService virtualExecutor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < CONCURRENT_USERS; i++) {
                virtualExecutor.submit(() -> {
                    readyLatch.countDown();
                    try {
                        startGun.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }

                    long start = System.nanoTime();
                    try {
                        MvcResult result = mockMvc.perform(post("/api/v1/auth/google")
                                        .header("X-Bypass-Rate-Limit", "true")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(requestJson))
                                .andReturn();

                        long durationMs = (System.nanoTime() - start) / 1_000_000;
                        latenciesMs.add(durationMs);

                        if (result.getResponse().getStatus() == 200) {
                            successCount.incrementAndGet();
                        } else {
                            failureCount.incrementAndGet();
                        }
                    } catch (Exception e) {
                        failureCount.incrementAndGet();
                    } finally {
                        finishLatch.countDown();
                    }
                });
            }

            assertTrue(readyLatch.await(30, TimeUnit.SECONDS));

            long overallStartTime = System.currentTimeMillis();
            startGun.countDown();

            boolean finishedInTime = finishLatch.await(60, TimeUnit.SECONDS);
            long totalElapsedMs = System.currentTimeMillis() - overallStartTime;

            assertTrue(finishedInTime, "2,000 concurrent Google auth requests did not complete within 60s");

            printLoadTestReport("2,000 Concurrent Google Auth Requests", CONCURRENT_USERS, successCount.get(),
                    failureCount.get(), totalElapsedMs, latenciesMs);

            assertEquals(CONCURRENT_USERS, successCount.get(), "All 2,000 Google auth requests should succeed");
            assertEquals(0, failureCount.get(), "Zero requests should fail");
        }
    }

    private void printLoadTestReport(String testName, int totalRequests, int successCount, int failureCount,
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
        log.info("CONCURRENCY BENCHMARK RESULTS: [{}]", testName);
        log.info("--------------------------------------------------------------------------------");
        log.info("Total Concurrent Requests : {}", totalRequests);
        log.info("Successful (HTTP 200 OK)  : {}", successCount);
        log.info("Failed Requests           : {}", failureCount);
        log.info("Success Rate              : {}%", String.format("%.2f", (successCount * 100.0 / totalRequests)));
        log.info("Total Elapsed Time        : {} ms", totalDurationMs);
        log.info("Throughput (RPS)          : {} req/sec", String.format("%.2f", throughputRps));
        log.info("Min Latency               : {} ms", min);
        log.info("Median (p50) Latency      : {} ms", p50);
        log.info("p90 Latency               : {} ms", p90);
        log.info("p95 Latency               : {} ms", p95);
        log.info("p99 Latency               : {} ms", p99);
        log.info("Max Latency               : {} ms", max);
        log.info("--------------------------------------------------------------------------------");
    }
}
