package com.cricauction.modules.tournament.controller;

import com.cricauction.modules.auth.entity.enums.UserRole;
import com.cricauction.modules.tournament.dto.request.CreateTournamentRequest;
import com.cricauction.modules.tournament.dto.request.TournamentSettingsRequest;
import com.cricauction.modules.tournament.dto.response.CreateTournamentResponse;
import com.cricauction.modules.tournament.dto.response.PublicTournamentResponse;
import com.cricauction.modules.tournament.dto.response.PublishTournamentResponse;
import com.cricauction.modules.tournament.dto.response.TournamentSettingsResponse;
import com.cricauction.modules.tournament.service.TournamentService;
import com.cricauction.security.UserPrincipal;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AUTOMATION BENCHMARK: 5,000 Concurrent Users against Actual PostgreSQL Database for Module 4 APIs.
 *
 * Evaluates high-concurrency throughput and latency percentiles across:
 * - 1,250 Users: POST /tournaments (Create Tournament Draft - Wizard Step 1)
 * - 1,250 Users: PUT /tournaments/{id}/settings (Configure Rules, Purse & Timer - Wizard Step 2)
 * - 1,250 Users: POST /tournaments/{id}/publish (Publish & Generate Registration / Captain Invite Links)
 * - 1,250 Users: GET /tournaments/public/{slug} (High-Speed L1 Cache Public Landing Page Read)
 */
@Slf4j
@SpringBootTest
@ActiveProfiles("postgres")
public class TournamentConcurrency5000UsersActualDbTest {

    private static final int TOTAL_OPERATIONS = 5000;

    @Autowired
    private TournamentService tournamentService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final String[] FORMATS = {"T20", "T10", "100-Ball", "One-Day"};
    private static final String[] BALL_TYPES = {"Tennis", "Leather", "Tape-Ball"};
    private static final String[] CITIES = {
            "Surat", "Ahmedabad", "Vadodara", "Rajkot", "Mumbai", "Pune", "Delhi", "Bengaluru"
    };

    record SeedTournament(String id, String slug, Long organizerId, String organizerEmail) {}

    @Test
    @DisplayName("BENCHMARK: 5,000 Concurrent Operations Module 4 APIs on ACTUAL PostgreSQL DB")
    void execute5000TournamentBenchmarkActualDatabase() throws InterruptedException {
        log.info("================================================================================");
        log.info("STARTING BENCHMARK: 5,000 Concurrent Operations Module 4 APIs on ACTUAL PostgreSQL DB");
        log.info("Target DB: jdbc:postgresql://localhost:5432/cricauction");
        log.info("================================================================================");

        // Fetch user records from PostgreSQL
        List<Map<String, Object>> rawUserRows = jdbcTemplate.queryForList(
                "SELECT id, full_name, email, mobile FROM users ORDER BY id ASC LIMIT 1250"
        );

        if (rawUserRows.size() < 500) {
            log.info("Self-seeding organizer accounts into PostgreSQL database for benchmark...");
            List<Object[]> userBatch = new ArrayList<>();
            for (int i = 1; i <= 500; i++) {
                userBatch.add(new Object[]{
                        "Organizer User " + i,
                        "organizer" + i + "@cricauction-test.internal",
                        "+9198765" + String.format("%05d", i),
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

            // Assign ORGANIZER role (role_id = 1)
            jdbcTemplate.execute(
                    "INSERT INTO user_roles (user_id, role_id) " +
                    "SELECT u.id, 1 FROM users u WHERE NOT EXISTS (" +
                    "    SELECT 1 FROM user_roles ur WHERE ur.user_id = u.id AND ur.role_id = 1" +
                    ")"
            );

            rawUserRows = jdbcTemplate.queryForList(
                    "SELECT id, full_name, email, mobile FROM users ORDER BY id ASC LIMIT 1250"
            );
        }

        final List<Map<String, Object>> userRows = Collections.unmodifiableList(rawUserRows);

        assertTrue(!userRows.isEmpty(), "Expected user accounts in PostgreSQL database!");
        log.info("Loaded {} user accounts for organizer operations.", userRows.size());

        // Pre-create 2,500 tournaments for settings and publish operations
        List<SeedTournament> seedTournaments = new CopyOnWriteArrayList<>();

        log.info("Pre-creating seed tournaments in PostgreSQL for concurrent settings and publish benchmark...");
        for (int i = 0; i < 2500; i++) {
            Map<String, Object> userRow = userRows.get(i % userRows.size());
            Long userId = ((Number) userRow.get("id")).longValue();
            String email = (String) userRow.get("email");

            UserPrincipal organizer = buildOrganizerPrincipal(userId, email);

            String name = "Seed League " + System.currentTimeMillis() + " #" + i;
            CreateTournamentRequest req = CreateTournamentRequest.builder()
                    .name(name)
                    .location("Surat, Gujarat")
                    .ground("Stadium " + (i % 10))
                    .startDate(LocalDate.now().plusDays(10))
                    .endDate(LocalDate.now().plusDays(20))
                    .auctionDate(LocalDate.now().plusDays(5))
                    .auctionTime("19:00")
                    .ballType("Tennis")
                    .format("T20")
                    .build();

            CreateTournamentResponse res = tournamentService.createTournament(organizer, req);
            seedTournaments.add(new SeedTournament(res.getId(), res.getSlug(), userId, email));
        }

        log.info("Pre-creation complete. {} tournaments ready for concurrent operations.", seedTournaments.size());

        CountDownLatch readyLatch = new CountDownLatch(TOTAL_OPERATIONS);
        CountDownLatch startGun = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(TOTAL_OPERATIONS);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        List<Long> latenciesMs = new CopyOnWriteArrayList<>();

        long runId = System.currentTimeMillis() % 100000;
        long overallStartTime;

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < TOTAL_OPERATIONS; i++) {
                final int opIndex = i;
                executor.submit(() -> {
                    readyLatch.countDown();
                    try {
                        startGun.await();
                        long start = System.nanoTime();

                        executeSingleOperation(opIndex, runId, userRows, seedTournaments);

                        long durationMs = (System.nanoTime() - start) / 1_000_000;
                        latenciesMs.add(durationMs);
                        successCount.incrementAndGet();
                    } catch (Exception ex) {
                        log.error("Error executing operation [{}]: {}", opIndex, ex.getMessage(), ex);
                        failureCount.incrementAndGet();
                    } finally {
                        finishLatch.countDown();
                    }
                });
            }

            assertTrue(readyLatch.await(60, TimeUnit.SECONDS), "Not all 5,000 virtual threads became ready");

            overallStartTime = System.currentTimeMillis();
            startGun.countDown();

            boolean finished = finishLatch.await(180, TimeUnit.SECONDS);
            long totalElapsedMs = System.currentTimeMillis() - overallStartTime;

            assertTrue(finished, "5,000 concurrent operations did not complete within 180s");

            printBenchmarkSummary(TOTAL_OPERATIONS, successCount.get(), failureCount.get(), totalElapsedMs, latenciesMs);
            verifyActualPostgresDatabaseEntries();

            assertEquals(TOTAL_OPERATIONS, successCount.get(), "All 5,000 operations should succeed");
            assertEquals(0, failureCount.get(), "Zero operations should fail");
        }
    }

    private void executeSingleOperation(int index, long runId, List<Map<String, Object>> userRows,
                                        List<SeedTournament> seedTournaments) {
        int group = index % 4;

        switch (group) {
            case 0 -> {
                // Group 0 (1,250 ops): POST /tournaments (Create Tournament Draft - Screen 16)
                Map<String, Object> userRow = userRows.get((index / 4) % userRows.size());
                Long userId = ((Number) userRow.get("id")).longValue();
                String email = (String) userRow.get("email");
                UserPrincipal organizer = buildOrganizerPrincipal(userId, email);

                String name = String.format("Premier League %d-%04d", runId, index);
                CreateTournamentRequest req = CreateTournamentRequest.builder()
                        .name(name)
                        .location(CITIES[index % CITIES.length] + ", India")
                        .ground("Cricket Stadium #" + (index % 12))
                        .startDate(LocalDate.now().plusDays(15))
                        .endDate(LocalDate.now().plusDays(25))
                        .auctionDate(LocalDate.now().plusDays(7))
                        .auctionTime("18:30")
                        .ballType(BALL_TYPES[index % BALL_TYPES.length])
                        .format(FORMATS[index % FORMATS.length])
                        .entryFee("Free")
                        .description("Concurrent tournament test payload for index: " + index)
                        .build();

                CreateTournamentResponse res = tournamentService.createTournament(organizer, req);
                assertTrue(res.getId() != null && !res.getId().isBlank());
                assertEquals(1, res.getStep());
            }
            case 1 -> {
                // Group 1 (1,250 ops): PUT /tournaments/{id}/settings (Configure Rules & Budget - Screen 17)
                int targetIndex = (index / 4) % seedTournaments.size();
                SeedTournament target = seedTournaments.get(targetIndex);
                UserPrincipal organizer = buildOrganizerPrincipal(target.organizerId(), target.organizerEmail());

                TournamentSettingsRequest settingsReq = TournamentSettingsRequest.builder()
                        .teams(10 + (index % 6))
                        .squadMinimum(11)
                        .squadMaximum(15)
                        .budget(1000000L + (index % 5) * 100000L)
                        .basePrice(20000L)
                        .bidIncrement(10000L)
                        .bidTimer(10)
                        .autoStartNext(true)
                        .matches(20)
                        .matchDuration(3)
                        .allowPlayersToWithdraw(true)
                        .build();

                TournamentSettingsResponse res = tournamentService.saveSettings(organizer, target.id(), settingsReq);
                assertEquals(2, res.getStep());
            }
            case 2 -> {
                // Group 2 (1,250 ops): POST /tournaments/{id}/publish (Publish & Generate Share Links)
                int targetIndex = (1250 + (index / 4)) % seedTournaments.size();
                SeedTournament target = seedTournaments.get(targetIndex);
                UserPrincipal organizer = buildOrganizerPrincipal(target.organizerId(), target.organizerEmail());

                PublishTournamentResponse res = tournamentService.publishTournament(organizer, target.id());
                assertEquals("published", res.getStatus());
                assertTrue(res.getRegistrationLink() != null && res.getRegistrationLink().contains("register"));
            }
            case 3 -> {
                // Group 3 (1,250 ops): GET /tournaments/public/{slug} (Public Landing Page - High-Speed Read)
                int targetIndex = (index / 4) % seedTournaments.size();
                String targetSlug = seedTournaments.get(targetIndex).slug();

                PublicTournamentResponse res = tournamentService.getPublicTournamentBySlug(targetSlug);
                assertTrue(res.getName() != null && !res.getName().isBlank());
                assertTrue(res.getCapacity() > 0);
            }
        }
    }

    private UserPrincipal buildOrganizerPrincipal(Long userId, String email) {
        return UserPrincipal.builder()
                .id(userId)
                .fullName("Organizer " + userId)
                .email(email)
                .role(UserRole.ORGANIZER)
                .roles(List.of("organizer", "player"))
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORGANIZER")))
                .isVerified(true)
                .isActive(true)
                .build();
    }

    private void verifyActualPostgresDatabaseEntries() {
        Long totalTournaments = jdbcTemplate.queryForObject("SELECT count(*) FROM tournaments", Long.class);
        Long totalSettings = jdbcTemplate.queryForObject("SELECT count(*) FROM tournament_settings", Long.class);
        Long draftCount = jdbcTemplate.queryForObject("SELECT count(*) FROM tournaments WHERE status = 'DRAFT'", Long.class);
        Long publishedCount = jdbcTemplate.queryForObject("SELECT count(*) FROM tournaments WHERE status = 'PUBLISHED'", Long.class);

        log.info("================================================================================");
        log.info("ACTUAL POSTGRESQL DATABASE VERIFICATION (MODULE 4 ENTITIES PERSISTED)");
        log.info("================================================================================");
        log.info("Total Tournaments in 'tournaments' Table       : {}", totalTournaments);
        log.info("Total Settings in 'tournament_settings' Table   : {}", totalSettings);
        log.info("Status Breakdown:");
        log.info("  ├── DRAFT Tournaments                         : {}", draftCount);
        log.info("  └── PUBLISHED Tournaments                     : {}", publishedCount);
        log.info("================================================================================");

        assertTrue(totalTournaments != null && totalTournaments >= 3750,
                "Expected at least 3,750 tournaments in PostgreSQL database!");
        assertTrue(totalSettings != null && totalSettings >= 3750,
                "Expected at least 3,750 tournament settings rows in PostgreSQL database!");
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
        log.info("CONCURRENCY BENCHMARK RESULTS: 5,000 MODULE 4 OPERATIONS (ACTUAL POSTGRESQL DB)");
        log.info("--------------------------------------------------------------------------------");
        log.info("Total Concurrent Operations : {}", totalRequests);
        log.info("Successful Operations       : {}", successCount);
        log.info("Failed Operations           : {}", failureCount);
        log.info("Success Rate                : {}%", String.format("%.2f", (successCount * 100.0 / totalRequests)));
        log.info("Total Elapsed Time          : {} ms ({} seconds)", totalDurationMs, String.format("%.2f", totalDurationMs / 1000.0));
        log.info("Throughput (RPS)            : {} req/sec", String.format("%.2f", throughputRps));
        log.info("Min Latency                 : {} ms", min);
        log.info("Median (p50) Latency        : {} ms", p50);
        log.info("p90 Latency                 : {} ms", p90);
        log.info("p95 Latency                 : {} ms", p95);
        log.info("p99 Latency                 : {} ms", p99);
        log.info("Max Latency                 : {} ms", max);
        log.info("--------------------------------------------------------------------------------");
    }
}
