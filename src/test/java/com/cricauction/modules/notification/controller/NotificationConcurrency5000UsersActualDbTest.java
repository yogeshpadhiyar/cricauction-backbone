package com.cricauction.modules.notification.controller;

import com.cricauction.modules.auth.entity.enums.UserRole;
import com.cricauction.modules.notification.dto.request.RegisterDeviceTokenRequest;
import com.cricauction.modules.notification.dto.response.NotificationResponse;
import com.cricauction.modules.notification.service.NotificationService;
import com.cricauction.security.UserPrincipal;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
@SpringBootTest
@ActiveProfiles("postgres")
public class NotificationConcurrency5000UsersActualDbTest {

    private static final int TOTAL_OPERATIONS = 5000;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    record BenchmarkContext(
            List<UserPrincipal> testUsers,
            List<String> notificationPublicIds
    ) {}

    @Test
    @DisplayName("BENCHMARK: 5,000 Concurrent Operations Module 9 Notification APIs on ACTUAL PostgreSQL DB")
    void execute5000NotificationBenchmarkActualDatabase() throws InterruptedException {
        log.info("================================================================================");
        log.info("STARTING BENCHMARK: 5,000 Concurrent Operations Module 9 APIs on ACTUAL PostgreSQL DB");
        log.info("Target DB: jdbc:postgresql://localhost:5432/cricauction");
        log.info("Concurrency: Virtual Thread Executor | Total Volume: 5,000 Operations");
        log.info("APIs: API 59 (List Notifications), API 60 (Mark Read), API 61 (Mark All Read), API 62 (Device Token)");
        log.info("Patterns: Strategy 1 Read-Before-Write (Replica Pool) + Micro-Write Master Pool + Idempotent Token UPSERT");
        log.info("================================================================================");

        // 1. Fetch or self-seed user accounts in PostgreSQL
        List<Map<String, Object>> rawUserRows = jdbcTemplate.queryForList(
                "SELECT id, full_name, email, mobile FROM users ORDER BY id ASC LIMIT 2500"
        );

        if (rawUserRows.size() < 100) {
            log.info("Seeding user accounts into PostgreSQL database for 5,000 benchmark...");
            List<Object[]> userBatch = new ArrayList<>();
            for (int i = rawUserRows.size() + 1; i <= 200; i++) {
                userBatch.add(new Object[]{
                        "NotifUser " + i,
                        "notifuser" + i + "@cricauction-test.internal",
                        "+9194" + String.format("%08d", i),
                        "$2a$10$abcdefghijklmnopqrstuvwxyz1234567890",
                        true,
                        true
                });
            }
            jdbcTemplate.batchUpdate(
                    "INSERT INTO users (full_name, email, mobile, password_hash, is_email_verified, is_active) " +
                    "VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT (email) DO NOTHING",
                    userBatch
            );

            rawUserRows = jdbcTemplate.queryForList(
                    "SELECT id, full_name, email, mobile FROM users ORDER BY id ASC LIMIT 2500"
            );
        }

        final List<Map<String, Object>> userRows = Collections.unmodifiableList(rawUserRows);
        log.info("Loaded {} user accounts for benchmark.", userRows.size());

        // 2. Build UserPrincipal objects for a pool of 200 benchmark users
        List<UserPrincipal> testUsers = new ArrayList<>();
        int poolSize = Math.min(200, userRows.size());
        for (int i = 0; i < poolSize; i++) {
            Map<String, Object> u = userRows.get(i);
            Long userId = ((Number) u.get("id")).longValue();
            testUsers.add(UserPrincipal.builder()
                    .id(userId)
                    .fullName((String) u.get("full_name"))
                    .email((String) u.get("email"))
                    .mobile((String) u.get("mobile"))
                    .role(UserRole.PLAYER)
                    .roles(List.of("ROLE_PLAYER"))
                    .isVerified(true)
                    .isActive(true)
                    .authorities(List.of(new SimpleGrantedAuthority("ROLE_PLAYER")))
                    .build());
        }

        // 3. Pre-seed notifications for benchmark users in batch
        log.info("Pre-seeding notification items into PostgreSQL for {} users...", testUsers.size());
        List<String> notificationPublicIds = new CopyOnWriteArrayList<>();
        List<Object[]> notifBatch = new ArrayList<>();
        String runId = UUID.randomUUID().toString().substring(0, 8);

        for (int i = 0; i < testUsers.size(); i++) {
            UserPrincipal user = testUsers.get(i);
            for (int j = 1; j <= 5; j++) {
                String publicId = "notif_bench_" + runId + "_" + i + "_" + j;
                notificationPublicIds.add(publicId);
                notifBatch.add(new Object[]{
                        publicId,
                        user.getId(),
                        "Auction Starting Soon #" + j,
                        "Surat Premier League auction room opens in 15 minutes.",
                        "AUCTION_STARTING",
                        "tourn_spl_" + runId,
                        false
                });
            }
        }

        jdbcTemplate.batchUpdate(
                "INSERT INTO notifications (public_id, user_id, title, body, type, reference_id, is_read, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) ON CONFLICT (public_id) DO NOTHING",
                notifBatch
        );

        BenchmarkContext ctx = new BenchmarkContext(testUsers, notificationPublicIds);
        log.info("Initialized benchmark context: {} Users, {} Notifications seeded.",
                ctx.testUsers.size(), ctx.notificationPublicIds.size());

        // 4. Concurrent Execution of 5,000 Operations
        CountDownLatch readyLatch = new CountDownLatch(TOTAL_OPERATIONS);
        CountDownLatch startGun = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(TOTAL_OPERATIONS);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        List<Long> latenciesMs = new CopyOnWriteArrayList<>();

        List<Long> listLatencies = new CopyOnWriteArrayList<>();
        List<Long> markReadLatencies = new CopyOnWriteArrayList<>();
        List<Long> markAllReadLatencies = new CopyOnWriteArrayList<>();
        List<Long> deviceTokenLatencies = new CopyOnWriteArrayList<>();

        long benchmarkStartTime;

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < TOTAL_OPERATIONS; i++) {
                final int opIndex = i;
                executor.submit(() -> {
                    readyLatch.countDown();
                    try {
                        startGun.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }

                    long startNano = System.nanoTime();
                    try {
                        int group = opIndex % 4;
                        UserPrincipal user = ctx.testUsers.get(opIndex % ctx.testUsers.size());

                        switch (group) {
                            case 0 -> {
                                // Group 0: API 59 - List Notifications (1,250 ops)
                                List<NotificationResponse> list = notificationService.listNotifications(user);
                                assertNotNull(list);
                                long elapsed = (System.nanoTime() - startNano) / 1_000_000;
                                listLatencies.add(elapsed);
                                latenciesMs.add(elapsed);
                                successCount.incrementAndGet();
                            }
                            case 1 -> {
                                // Group 1: API 60 - Mark Notification as Read (1,250 ops)
                                int notifIdx = (opIndex) % ctx.notificationPublicIds.size();
                                String notifId = ctx.notificationPublicIds.get(notifIdx);
                                // The notification belongs to user at (notifIdx / 5)
                                UserPrincipal owner = ctx.testUsers.get((notifIdx / 5) % ctx.testUsers.size());
                                notificationService.markAsRead(owner, notifId);
                                long elapsed = (System.nanoTime() - startNano) / 1_000_000;
                                markReadLatencies.add(elapsed);
                                latenciesMs.add(elapsed);
                                successCount.incrementAndGet();
                            }
                            case 2 -> {
                                // Group 2: API 61 - Mark All Notifications as Read (1,250 ops)
                                notificationService.markAllAsRead(user);
                                long elapsed = (System.nanoTime() - startNano) / 1_000_000;
                                markAllReadLatencies.add(elapsed);
                                latenciesMs.add(elapsed);
                                successCount.incrementAndGet();
                            }
                            case 3 -> {
                                // Group 3: API 62 - Register Device Push Token (1,250 ops)
                                String platform = (opIndex % 2 == 0) ? "ios" : "android";
                                RegisterDeviceTokenRequest tokenReq = RegisterDeviceTokenRequest.builder()
                                        .deviceToken("fcm_device_token_" + runId + "_" + (opIndex % 300))
                                        .platform(platform)
                                        .build();
                                notificationService.registerDeviceToken(user, tokenReq);
                                long elapsed = (System.nanoTime() - startNano) / 1_000_000;
                                deviceTokenLatencies.add(elapsed);
                                latenciesMs.add(elapsed);
                                successCount.incrementAndGet();
                            }
                        }
                    } catch (Exception e) {
                        log.error("Op {} failed: {}", opIndex, e.getMessage(), e);
                        failureCount.incrementAndGet();
                    } finally {
                        finishLatch.countDown();
                    }
                });
            }

            readyLatch.await(30, TimeUnit.SECONDS);
            log.info("All 5,000 threads primed and ready. Firing start gun...");
            benchmarkStartTime = System.currentTimeMillis();
            startGun.countDown();

            boolean completed = finishLatch.await(60, TimeUnit.SECONDS);
            assertTrue(completed, "Benchmark should complete within 60 seconds");
        }

        long totalElapsedMs = System.currentTimeMillis() - benchmarkStartTime;
        double throughputOpsPerSec = (successCount.get() * 1000.0) / totalElapsedMs;

        List<Long> sortedLatencies = new ArrayList<>(latenciesMs);
        Collections.sort(sortedLatencies);

        long p50 = sortedLatencies.isEmpty() ? 0 : sortedLatencies.get((int) (sortedLatencies.size() * 0.50));
        long p95 = sortedLatencies.isEmpty() ? 0 : sortedLatencies.get((int) (sortedLatencies.size() * 0.95));
        long p99 = sortedLatencies.isEmpty() ? 0 : sortedLatencies.get((int) (sortedLatencies.size() * 0.99));
        long min = sortedLatencies.isEmpty() ? 0 : sortedLatencies.get(0);
        long max = sortedLatencies.isEmpty() ? 0 : sortedLatencies.get(sortedLatencies.size() - 1);
        double avg = sortedLatencies.stream().mapToLong(Long::longValue).average().orElse(0.0);

        log.info("================================================================================");
        log.info("MODULE 9 NOTIFICATION BENCHMARK RESULTS (5,000 OPERATIONS ON ACTUAL POSTGRESQL)");
        log.info("================================================================================");
        log.info("Total Operations:         {}", TOTAL_OPERATIONS);
        log.info("Successful Operations:    {}", successCount.get());
        log.info("Failed Operations:        {}", failureCount.get());
        log.info("Success Rate:             {}%", String.format("%.2f", (successCount.get() * 100.0) / TOTAL_OPERATIONS));
        log.info("Total Duration:           {} ms", totalElapsedMs);
        log.info("System Throughput:        {} ops/sec", String.format("%.2f", throughputOpsPerSec));
        log.info("--------------------------------------------------------------------------------");
        log.info("LATENCY METRICS (Aggregated):");
        log.info("  Min Latency:            {} ms", min);
        log.info("  Avg Latency:            {} ms", String.format("%.2f", avg));
        log.info("  p50 (Median):           {} ms", p50);
        log.info("  p95:                    {} ms", p95);
        log.info("  p99:                    {} ms", p99);
        log.info("  Max Latency:            {} ms", max);
        log.info("--------------------------------------------------------------------------------");
        log.info("BREAKDOWN BY NOTIFICATION ENDPOINT:");
        log.info("  API 59 (List Notifications):    {} ops | Avg Latency: {} ms",
                listLatencies.size(),
                String.format("%.2f", listLatencies.stream().mapToLong(Long::longValue).average().orElse(0.0)));
        log.info("  API 60 (Mark Item Read):        {} ops | Avg Latency: {} ms",
                markReadLatencies.size(),
                String.format("%.2f", markReadLatencies.stream().mapToLong(Long::longValue).average().orElse(0.0)));
        log.info("  API 61 (Mark All Read):         {} ops | Avg Latency: {} ms",
                markAllReadLatencies.size(),
                String.format("%.2f", markAllReadLatencies.stream().mapToLong(Long::longValue).average().orElse(0.0)));
        log.info("  API 62 (Device Push Token):     {} ops | Avg Latency: {} ms",
                deviceTokenLatencies.size(),
                String.format("%.2f", deviceTokenLatencies.stream().mapToLong(Long::longValue).average().orElse(0.0)));
        log.info("================================================================================");

        assertEquals(0, failureCount.get(), "Zero operations should fail in 5,000 benchmark");
        assertEquals(TOTAL_OPERATIONS, successCount.get(), "All 5,000 operations must succeed");
    }
}
