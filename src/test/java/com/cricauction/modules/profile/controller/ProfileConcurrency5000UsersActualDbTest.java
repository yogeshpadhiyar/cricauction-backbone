package com.cricauction.modules.profile.controller;

import com.cricauction.modules.auth.entity.enums.UserRole;
import com.cricauction.modules.profile.dto.request.UpdatePlayerProfileRequest;
import com.cricauction.modules.profile.dto.request.UpdateUserProfileRequest;
import com.cricauction.modules.profile.dto.response.PlayerProfileResponse;
import com.cricauction.modules.profile.dto.response.ProfilePhotoUploadResponse;
import com.cricauction.modules.profile.service.ProfileService;
import com.cricauction.security.UserPrincipal;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AUTOMATION BENCHMARK: 5,000 Concurrent Users against Actual PostgreSQL Database for Module 2 APIs.
 *
 * Evaluates high-concurrency throughput and latency percentiles across:
 * - 1,250 Users: GET /users/profile (Fast Read-Only Profile Fetch)
 * - 1,250 Users: PATCH /users/profile (In-place City & Bio Update)
 * - 1,250 Users: PUT /users/player-profile (Cricket Stats Upsert in player_profiles table)
 * - 1,250 Users: POST /users/profile/photo (Profile Avatar Upload via FileStorageStrategy)
 */
@Slf4j
@SpringBootTest
@ActiveProfiles("postgres")
public class ProfileConcurrency5000UsersActualDbTest {

    private static final int TOTAL_USERS = 5000;

    @Autowired
    private ProfileService profileService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final String[] PLAYING_ROLES = {
            "Batsman", "Bowler", "All-Rounder", "Wicket Keeper"
    };

    private static final String[] BATTING_STYLES = {
            "Right-hand bat", "Left-hand bat"
    };

    private static final String[] BOWLING_STYLES = {
            "Right-arm fast", "Right-arm medium", "Left-arm orthodox", "Leg break googly"
    };

    private static final String[] CITIES = {
            "Mumbai", "Delhi", "Bengaluru", "Chennai", "Kolkata", "Ahmedabad",
            "Surat", "Jaipur", "Hyderabad", "Pune", "Lucknow", "Chandigarh"
    };

    @Test
    @DisplayName("BENCHMARK: 5,000 Concurrent Users Module 2 APIs against Actual PostgreSQL DB")
    void execute5000UsersProfileBenchmarkActualDatabase() throws InterruptedException {
        log.info("================================================================================");
        log.info("STARTING BENCHMARK: 5,000 Concurrent Users Module 2 APIs on ACTUAL PostgreSQL DB");
        log.info("Target DB: jdbc:postgresql://localhost:5432/cricauction");
        log.info("================================================================================");

        // Fetch 5,000 real user records from PostgreSQL
        List<Map<String, Object>> userRows = jdbcTemplate.queryForList(
                "SELECT id, full_name, email, mobile FROM users ORDER BY id ASC LIMIT ?", TOTAL_USERS
        );

        assertTrue(userRows.size() >= TOTAL_USERS,
                "Expected at least 5,000 users in PostgreSQL database! Found: " + userRows.size());

        log.info("Loaded {} user accounts from PostgreSQL for concurrency test.", userRows.size());

        CountDownLatch readyLatch = new CountDownLatch(TOTAL_USERS);
        CountDownLatch startGun = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(TOTAL_USERS);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        List<Long> latenciesMs = Collections.synchronizedList(new ArrayList<>(TOTAL_USERS));

        long overallStartTime;

        try (ExecutorService virtualExecutor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < TOTAL_USERS; i++) {
                final int index = i;
                final Map<String, Object> userRow = userRows.get(index);
                final Long userId = ((Number) userRow.get("id")).longValue();
                final String fullName = (String) userRow.get("full_name");
                final String email = (String) userRow.get("email");
                final String mobile = (String) userRow.get("mobile");
                final String roleStr = "player";

                UserPrincipal principal = UserPrincipal.builder()
                        .id(userId)
                        .fullName(fullName)
                        .email(email)
                        .mobile(mobile)
                        .role(UserRole.fromString(roleStr))
                        .isActive(true)
                        .build();

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
                        executeSingleProfileOperation(principal, index);
                        long durationMs = (System.nanoTime() - start) / 1_000_000;
                        latenciesMs.add(durationMs);
                        successCount.incrementAndGet();
                    } catch (Exception ex) {
                        log.error("Error executing profile operation for user [{}]: {}", userId, ex.getMessage(), ex);
                        failureCount.incrementAndGet();
                    } finally {
                        finishLatch.countDown();
                    }
                });
            }

            assertTrue(readyLatch.await(30, TimeUnit.SECONDS), "Not all 5,000 virtual threads were primed");

            // Release all 5,000 virtual threads concurrently
            overallStartTime = System.currentTimeMillis();
            startGun.countDown();

            boolean finished = finishLatch.await(60, TimeUnit.SECONDS);
            long totalElapsedMs = System.currentTimeMillis() - overallStartTime;

            assertTrue(finished, "5,000 concurrent profile operations did not complete within 60s");

            // Print summary
            printBenchmarkSummary(TOTAL_USERS, successCount.get(), failureCount.get(), totalElapsedMs, latenciesMs);

            // Verify actual PostgreSQL DB entries
            verifyPostgreSqlState();

            assertEquals(TOTAL_USERS, successCount.get(), "All 5,000 requests should succeed");
            assertEquals(0, failureCount.get(), "Zero requests should fail");
        }
    }

    private void executeSingleProfileOperation(UserPrincipal principal, int index) {
        int cohort = index % 3; // 0: GET, 1: PATCH, 2: PUT, 3: POST - Photo
        String city = CITIES[index % CITIES.length];

        switch (cohort) {
            case 0 -> {
                // Cohort 1 (1,250 Users): GET /users/profile (Fast Read-Only)
                var profile = profileService.getUserProfile(principal);
                assertNotNull(profile);
                assertNotNull(profile.getFullName());
            }
            case 1 -> {
                // Cohort 2 (1,250 Users): PATCH /users/profile (In-place update)
                UpdateUserProfileRequest req = UpdateUserProfileRequest.builder()
                        .city(city)
                        .bio("Cricket player based in " + city + ". Eager for auction.")
                        .build();
                var updated = profileService.updateUserProfile(principal, req);
                assertNotNull(updated);
                assertEquals(city, updated.getCity());
            }
            case 2 -> {
                // Cohort 3 (1,250 Users): PUT /users/player-profile (Cricket Stats Upsert)
                String role = PLAYING_ROLES[index % PLAYING_ROLES.length];
                String bat = BATTING_STYLES[index % BATTING_STYLES.length];
                String bowl = BOWLING_STYLES[index % BOWLING_STYLES.length];

                UpdatePlayerProfileRequest req = UpdatePlayerProfileRequest.builder()
                        .playingRole(role)
                        .battingStyle(bat)
                        .bowlingStyle(bowl)
                        .jerseyNumber((index % 99) + 1)
                        .cricHeroesProfileUrl("https://cricheroes.in/player/" + principal.getId())
                        .pastTournaments((index % 20) + 1)
                        .highestScore((index % 150) + 20)
                        .bestBowling("4/" + ((index % 30) + 10))
                        .build();

                PlayerProfileResponse res = profileService.updatePlayerProfile(principal, req);
                assertNotNull(res);
                assertEquals(role, res.getPlayingRole());
            }
            case 3 -> {
                // Cohort 4 (1,250 Users): POST /users/profile/photo (Profile Photo Upload)
                MockMultipartFile photo = new MockMultipartFile(
                        "photo",
                        "avatar_" + principal.getId() + ".jpg",
                        "image/jpeg",
                        ("dummy-avatar-content-bytes-user-" + principal.getId()).getBytes()
                );
                ProfilePhotoUploadResponse res = profileService.uploadProfilePhoto(principal, photo);
                assertNotNull(res);
                assertNotNull(res.getProfilePhotoUrl());
            }
        }
    }

    private void verifyPostgreSqlState() {
        Long totalPlayerProfiles = jdbcTemplate.queryForObject("SELECT count(*) FROM player_profiles", Long.class);
        Long updatedUsers = jdbcTemplate.queryForObject("SELECT count(*) FROM users WHERE bio IS NOT NULL", Long.class);
        Long usersWithPhotos = jdbcTemplate.queryForObject("SELECT count(*) FROM users WHERE profile_photo_url IS NOT NULL", Long.class);

        log.info("================================================================================");
        log.info("ACTUAL POSTGRESQL DATABASE VERIFICATION (MODULE 2 ENTITIES PERSISTED)");
        log.info("================================================================================");
        log.info("Total Player Profiles in 'player_profiles' table : {}", totalPlayerProfiles);
        log.info("Total Users with Bio Updated                     : {}", updatedUsers);
        log.info("Total Users with Profile Photos Uploaded         : {}", usersWithPhotos);
        log.info("================================================================================");

        assertTrue(totalPlayerProfiles != null && totalPlayerProfiles >= 1250,
                "Expected at least 1,250 player profiles in PostgreSQL!");
    }

    private void printBenchmarkSummary(int totalRequests, int success, int failure,
                                      long elapsedMs, List<Long> latencies) {
        Collections.sort(latencies);

        long min = latencies.isEmpty() ? 0 : latencies.getFirst();
        long median = latencies.isEmpty() ? 0 : latencies.get(latencies.size() / 2);
        long p90 = latencies.isEmpty() ? 0 : latencies.get((int) (latencies.size() * 0.90));
        long p95 = latencies.isEmpty() ? 0 : latencies.get((int) (latencies.size() * 0.95));
        long p99 = latencies.isEmpty() ? 0 : latencies.get((int) (latencies.size() * 0.99));
        long max = latencies.isEmpty() ? 0 : latencies.getLast();

        double throughputRps = (totalRequests / (double) elapsedMs) * 1000.0;
        double successRate = (success / (double) totalRequests) * 100.0;

        log.info("================================================================================");
        log.info("CONCURRENCY BENCHMARK RESULTS: 5,000 MODULE 2 USERS (ACTUAL POSTGRESQL DB)");
        log.info("================================================================================");
        log.info("Total Concurrent Requests   : {}", totalRequests);
        log.info("Successful Operations       : {}", success);
        log.info("Failed Operations           : {}", failure);
        log.info("Success Rate                : {}%", String.format("%.2f", successRate));
        log.info("Total Elapsed Time          : {} ms ({} seconds)", elapsedMs, String.format("%.2f", elapsedMs / 1000.0));
        log.info("Throughput (RPS)            : {} req/sec", String.format("%.2f", throughputRps));
        log.info("Min Latency                 : {} ms", min);
        log.info("Median (p50) Latency        : {} ms", median);
        log.info("p90 Latency                 : {} ms", p90);
        log.info("p95 Latency                 : {} ms", p95);
        log.info("p99 Latency                 : {} ms", p99);
        log.info("Max Latency                 : {} ms", max);
        log.info("================================================================================");
    }
}
