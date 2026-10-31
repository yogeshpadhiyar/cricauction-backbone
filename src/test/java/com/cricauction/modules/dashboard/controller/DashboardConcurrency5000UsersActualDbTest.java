package com.cricauction.modules.dashboard.controller;

import com.cricauction.modules.auth.entity.enums.UserRole;
import com.cricauction.modules.dashboard.dto.response.CaptainDashboardResponse;
import com.cricauction.modules.dashboard.dto.response.OrganizerDashboardResponse;
import com.cricauction.modules.dashboard.dto.response.PlayerDashboardResponse;
import com.cricauction.modules.dashboard.service.DashboardService;
import com.cricauction.modules.tournament.dto.request.CreateTournamentRequest;
import com.cricauction.modules.tournament.dto.request.TournamentSettingsRequest;
import com.cricauction.modules.tournament.dto.response.CreateTournamentResponse;
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

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
@SpringBootTest
@ActiveProfiles("postgres")
public class DashboardConcurrency5000UsersActualDbTest {

    private static final int TOTAL_OPERATIONS = 5000;

    @Autowired
    private DashboardService dashboardService;

    @Autowired
    private TournamentService tournamentService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    record BenchmarkContext(
            UserPrincipal organizer,
            List<UserPrincipal> captains,
            List<UserPrincipal> players,
            String tournamentId,
            Long tournamentNumericId
    ) {}

    @Test
    @DisplayName("BENCHMARK: 5,000 Concurrent Operations Module 3 Dashboard APIs on ACTUAL PostgreSQL DB")
    void execute5000DashboardBenchmarkActualDatabase() throws InterruptedException {
        log.info("================================================================================");
        log.info("STARTING BENCHMARK: 5,000 Concurrent Operations Module 3 APIs on ACTUAL PostgreSQL DB");
        log.info("Target DB: jdbc:postgresql://localhost:5432/cricauction");
        log.info("Concurrency: Virtual Thread Executor | Total Volume: 5,000 Operations");
        log.info("APIs: API 15 (Organizer Dashboard), API 16 (Captain Dashboard), API 17 (Player Dashboard)");
        log.info("Patterns: Strategy 1 Read-Before-Write (Replica Pool) + Single-Flight Coalescing");
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
                        "DashboardUser " + i,
                        "dashuser" + i + "@cricauction-test.internal",
                        "+9193" + String.format("%08d", i),
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

        // 2. Select Organizer
        Map<String, Object> organizerRow = userRows.get(0);
        Long organizerId = ((Number) organizerRow.get("id")).longValue();
        UserPrincipal organizer = UserPrincipal.builder()
                .id(organizerId)
                .fullName((String) organizerRow.get("full_name"))
                .email((String) organizerRow.get("email"))
                .mobile((String) organizerRow.get("mobile"))
                .role(UserRole.ORGANIZER)
                .roles(List.of("ROLE_ORGANIZER"))
                .isVerified(true)
                .isActive(true)
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORGANIZER")))
                .build();

        // 3. Create or find Benchmark Tournament
        String runId = UUID.randomUUID().toString().substring(0, 8);
        CreateTournamentRequest tournReq = CreateTournamentRequest.builder()
                .name("Dashboard Premier League " + runId)
                .location("Surat")
                .ground("Lalbhai Contractor Stadium")
                .startDate(LocalDate.now().plusDays(14))
                .endDate(LocalDate.now().plusDays(28))
                .ballType("Leather")
                .format("T20")
                .entryFee("Free")
                .build();

        CreateTournamentResponse tournRes = tournamentService.createTournament(organizer, tournReq);
        String tournamentId = tournRes.getId();

        TournamentSettingsRequest settingsReq = TournamentSettingsRequest.builder()
                .teams(8)
                .squadMinimum(11)
                .squadMaximum(15)
                .budget(10000000L)
                .basePrice(20000L)
                .bidIncrement(10000L)
                .bidTimer(15)
                .build();
        tournamentService.saveSettings(organizer, tournamentId, settingsReq);

        Long numericTournamentId = jdbcTemplate.queryForObject(
                "SELECT id FROM tournaments WHERE public_id = ?", Long.class, tournamentId);

        // Update auction date on tournament so dashboard timers are populated
        jdbcTemplate.update(
                "UPDATE tournaments SET auction_date = CURRENT_DATE + INTERVAL '5 days', auction_time = '19:00', status = 'PUBLISHED' WHERE id = ?",
                numericTournamentId);

        // 4. Create Captains and Teams
        List<UserPrincipal> captains = new ArrayList<>();
        List<Long> teamIds = new ArrayList<>();
        for (int i = 1; i <= 4; i++) {
            Map<String, Object> capRow = userRows.get(i);
            Long capId = ((Number) capRow.get("id")).longValue();
            UserPrincipal captain = UserPrincipal.builder()
                    .id(capId)
                    .fullName((String) capRow.get("full_name"))
                    .email((String) capRow.get("email"))
                    .mobile((String) capRow.get("mobile"))
                    .role(UserRole.CAPTAIN)
                    .roles(List.of("ROLE_CAPTAIN"))
                    .isVerified(true)
                    .isActive(true)
                    .authorities(List.of(new SimpleGrantedAuthority("ROLE_CAPTAIN")))
                    .build();
            captains.add(captain);

            Long teamId = jdbcTemplate.queryForObject(
                    "INSERT INTO teams (tournament_id, name, short_code, captain_name, captain_mobile, captain_id, initial_budget, remaining_budget, status, invite_code, version, created_at, updated_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, 10000000, 8500000, 'Approved', ?, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) RETURNING id",
                    Long.class,
                    numericTournamentId,
                    "Team " + i + " " + runId,
                    "T" + i,
                    (String) capRow.get("full_name"),
                    (String) capRow.get("mobile"),
                    capId,
                    "INV-DASH-" + i + "-" + runId
            );
            teamIds.add(teamId);
        }

        // 5. Create Players and Registrations
        List<UserPrincipal> players = new ArrayList<>();
        for (int i = 5; i < 50 && i < userRows.size(); i++) {
            Map<String, Object> pRow = userRows.get(i);
            Long pId = ((Number) pRow.get("id")).longValue();
            UserPrincipal player = UserPrincipal.builder()
                    .id(pId)
                    .fullName((String) pRow.get("full_name"))
                    .email((String) pRow.get("email"))
                    .mobile((String) pRow.get("mobile"))
                    .role(UserRole.PLAYER)
                    .roles(List.of("ROLE_PLAYER"))
                    .isVerified(true)
                    .isActive(true)
                    .authorities(List.of(new SimpleGrantedAuthority("ROLE_PLAYER")))
                    .build();
            players.add(player);

            jdbcTemplate.update(
                    "INSERT INTO tournament_players (tournament_id, user_id, player_name, registered_role, batting_style, bowling_style, base_price, status, mobile, version, created_at, updated_at) " +
                    "VALUES (?, ?, ?, 'All-Rounder', 'Right-hand bat', 'Right-arm medium', 5000, 'Registered', ?, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) " +
                    "ON CONFLICT (tournament_id, user_id) DO NOTHING",
                    numericTournamentId,
                    pId,
                    player.getFullName(),
                    player.getMobile()
            );
        }

        // Add 1 squad member so captain has squadSize > 0
        jdbcTemplate.update(
                "INSERT INTO squad_members (team_id, tournament_player_id, player_user_id, player_name, playing_role, purchase_price, is_captain, bought_at) " +
                "VALUES (?, null, ?, ?, 'All-Rounder', 500000, false, CURRENT_TIMESTAMP)",
                teamIds.get(0),
                players.get(0).getId(),
                players.get(0).getFullName()
        );

        BenchmarkContext ctx = new BenchmarkContext(
                organizer,
                captains,
                players,
                tournamentId,
                numericTournamentId
        );

        log.info("Initialized benchmark context: 1 Organizer, {} Captains, {} Players registered.",
                captains.size(), players.size());

        // 6. Execute 5,000 Operations concurrently
        CountDownLatch readyLatch = new CountDownLatch(TOTAL_OPERATIONS);
        CountDownLatch startGun = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(TOTAL_OPERATIONS);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        List<Long> latenciesMs = new CopyOnWriteArrayList<>();

        List<Long> organizerLatencies = new CopyOnWriteArrayList<>();
        List<Long> captainLatencies = new CopyOnWriteArrayList<>();
        List<Long> playerLatencies = new CopyOnWriteArrayList<>();

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
                        int group = opIndex % 3;
                        switch (group) {
                            case 0 -> {
                                // Group 0: Organizer Dashboard Feed (API 15)
                                OrganizerDashboardResponse resp = dashboardService.getOrganizerDashboard(ctx.organizer);
                                assertNotNull(resp);
                                assertNotNull(resp.getStats());
                                long elapsed = (System.nanoTime() - startNano) / 1_000_000;
                                organizerLatencies.add(elapsed);
                                latenciesMs.add(elapsed);
                                successCount.incrementAndGet();
                            }
                            case 1 -> {
                                // Group 1: Captain Dashboard Feed (API 16)
                                UserPrincipal cap = ctx.captains.get(opIndex % ctx.captains.size());
                                CaptainDashboardResponse resp = dashboardService.getCaptainDashboard(cap);
                                assertNotNull(resp);
                                assertNotNull(resp.getTeam());
                                long elapsed = (System.nanoTime() - startNano) / 1_000_000;
                                captainLatencies.add(elapsed);
                                latenciesMs.add(elapsed);
                                successCount.incrementAndGet();
                            }
                            case 2 -> {
                                // Group 2: Player Dashboard Feed (API 17)
                                UserPrincipal ply = ctx.players.get(opIndex % ctx.players.size());
                                PlayerDashboardResponse resp = dashboardService.getPlayerDashboard(ply);
                                assertNotNull(resp);
                                long elapsed = (System.nanoTime() - startNano) / 1_000_000;
                                playerLatencies.add(elapsed);
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
        log.info("MODULE 3 DASHBOARD BENCHMARK RESULTS (5,000 OPERATIONS ON ACTUAL POSTGRESQL)");
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
        log.info("BREAKDOWN BY DASHBOARD FEED:");
        log.info("  API 15 (Organizer Feed): {} ops | Avg Latency: {} ms",
                organizerLatencies.size(),
                String.format("%.2f", organizerLatencies.stream().mapToLong(Long::longValue).average().orElse(0.0)));
        log.info("  API 16 (Captain Feed):   {} ops | Avg Latency: {} ms",
                captainLatencies.size(),
                String.format("%.2f", captainLatencies.stream().mapToLong(Long::longValue).average().orElse(0.0)));
        log.info("  API 17 (Player Feed):    {} ops | Avg Latency: {} ms",
                playerLatencies.size(),
                String.format("%.2f", playerLatencies.stream().mapToLong(Long::longValue).average().orElse(0.0)));
        log.info("================================================================================");

        assertEquals(0, failureCount.get(), "Zero operations should fail in 5,000 benchmark");
        assertEquals(TOTAL_OPERATIONS, successCount.get(), "All 5,000 operations must succeed");
    }
}
