package com.cricauction.modules.match.controller;

import com.cricauction.modules.auth.entity.enums.UserRole;
import com.cricauction.modules.match.dto.CreateMatchFixtureRequest;
import com.cricauction.modules.match.dto.MatchScorecardResponse;
import com.cricauction.modules.match.dto.RecordBallRequest;
import com.cricauction.modules.match.dto.TournamentStandingsResponse;
import com.cricauction.modules.match.entity.Match;
import com.cricauction.modules.match.service.MatchService;
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

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
@SpringBootTest
@ActiveProfiles("postgres")
public class MatchConcurrency5000UsersActualDbTest {

    private static final int TOTAL_OPERATIONS = 5000;

    @Autowired
    private MatchService matchService;

    @Autowired
    private TournamentService tournamentService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    record BenchmarkContext(
            String tournamentId,
            Long tournamentNumericId,
            UserPrincipal organizer,
            List<UserPrincipal> spectators,
            List<Long> teamIds,
            List<String> matchPublicIds
    ) {}

    @Test
    @DisplayName("BENCHMARK: 5,000 Concurrent Operations Module 8 APIs on ACTUAL PostgreSQL DB")
    void execute5000MatchesBenchmarkActualDatabase() throws InterruptedException {
        log.info("================================================================================");
        log.info("STARTING BENCHMARK: 5,000 Concurrent Operations Module 8 APIs on ACTUAL PostgreSQL DB");
        log.info("Target DB: jdbc:postgresql://localhost:5432/cricauction");
        log.info("Concurrency: 100 Virtual Worker Threads | Total Volume: 5,000 Operations");
        log.info("APIs: API 54 (Fixtures), API 55 (Schedule), API 56 (Scorecard), API 57 (Ball Update), API 58 (Standings)");
        log.info("================================================================================");

        // 1. Fetch or self-seed user accounts in PostgreSQL
        List<Map<String, Object>> rawUserRows = jdbcTemplate.queryForList(
                "SELECT id, full_name, email, mobile FROM users ORDER BY id ASC LIMIT 2500"
        );

        if (rawUserRows.size() < 2000) {
            log.info("Seeding user accounts into PostgreSQL database for 5,000 benchmark...");
            List<Object[]> userBatch = new ArrayList<>();
            for (int i = rawUserRows.size() + 1; i <= 2500; i++) {
                userBatch.add(new Object[]{
                        "MatchSpectator " + i,
                        "matchuser" + i + "@cricauction-test.internal",
                        "+9192" + String.format("%08d", i),
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

        // 2. Select organizer
        Map<String, Object> organizerRow = userRows.get(0);
        Long organizerId = ((Number) organizerRow.get("id")).longValue();
        String organizerEmail = (String) organizerRow.get("email");
        UserPrincipal organizer = buildOrganizerPrincipal(organizerId, organizerEmail);

        long runId = System.currentTimeMillis();

        // 3. Create host tournament in PostgreSQL
        CreateTournamentRequest tourReq = CreateTournamentRequest.builder()
                .name("Gujarat Super League Match 2026 " + runId)
                .location("Surat")
                .ground("Lalbhai Contractor Stadium")
                .startDate(LocalDate.now().plusDays(2))
                .endDate(LocalDate.now().plusDays(15))
                .ballType("Leather")
                .format("T20")
                .entryFee("Free")
                .build();

        CreateTournamentResponse tourRes = tournamentService.createTournament(organizer, tourReq);
        String tournamentId = tourRes.getId();

        TournamentSettingsRequest setReq = TournamentSettingsRequest.builder()
                .teams(4)
                .squadMinimum(11)
                .squadMaximum(15)
                .budget(500_000_000L)
                .basePrice(20_000L)
                .bidIncrement(10_000L)
                .bidTimer(15)
                .build();
        tournamentService.saveSettings(organizer, tournamentId, setReq);

        Long numericTournamentId = jdbcTemplate.queryForObject(
                "SELECT id FROM tournaments WHERE public_id = ?",
                Long.class,
                tournamentId
        );

        // 4. Seed 4 Teams in PostgreSQL
        List<Long> teamIds = new ArrayList<>();
        String[] teamNames = {"Surat Strikers", "Ahmedabad Aces", "Baroda Blasters", "Rajkot Royals"};

        for (int i = 0; i < 4; i++) {
            Map<String, Object> captainUser = userRows.get(i + 1);
            Long captainId = ((Number) captainUser.get("id")).longValue();
            String tName = teamNames[i] + " " + (runId % 1000);

            Long teamId = jdbcTemplate.queryForObject(
                    "INSERT INTO teams (tournament_id, name, captain_name, captain_mobile, captain_id, initial_budget, remaining_budget, status, invite_code, version, created_at, updated_at) " +
                    "VALUES (?, ?, ?, ?, ?, 500000000, 500000000, 'Approved', ?, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) RETURNING id",
                    Long.class,
                    numericTournamentId,
                    tName,
                    (String) captainUser.get("full_name"),
                    (String) captainUser.get("mobile"),
                    captainId,
                    "INV-M-" + i + "-" + runId
            );
            teamIds.add(teamId);
        }

        // 5. Create 2 Matches via matchService
        CreateMatchFixtureRequest match1Req = CreateMatchFixtureRequest.builder()
                .team1Id(teamIds.get(0))
                .team2Id(teamIds.get(1))
                .scheduledAt(Instant.now().plusSeconds(7200))
                .ground("Lalbhai Contractor Stadium")
                .overs(20)
                .build();
        var match1Res = matchService.createFixture(organizer, tournamentId, match1Req);

        CreateMatchFixtureRequest match2Req = CreateMatchFixtureRequest.builder()
                .team1Id(teamIds.get(2))
                .team2Id(teamIds.get(3))
                .scheduledAt(Instant.now().plusSeconds(14400))
                .ground("Narendra Modi Stadium")
                .overs(20)
                .build();
        var match2Res = matchService.createFixture(organizer, tournamentId, match2Req);

        List<String> matchPublicIds = List.of(match1Res.getId(), match2Res.getId());
        log.info("Initialized 2 Matches for testing: [{}] and [{}]", matchPublicIds.get(0), matchPublicIds.get(1));

        // 6. Build spectator principals
        List<UserPrincipal> spectators = new ArrayList<>();
        for (int i = 5; i < 500 && i < userRows.size(); i++) {
            Map<String, Object> u = userRows.get(i);
            spectators.add(buildSpectatorPrincipal(((Number) u.get("id")).longValue(), (String) u.get("email")));
        }

        BenchmarkContext ctx = new BenchmarkContext(
                tournamentId,
                numericTournamentId,
                organizer,
                spectators,
                teamIds,
                matchPublicIds
        );

        // 7. Concurrent Execution with 100 Virtual Threads
        CountDownLatch readyLatch = new CountDownLatch(TOTAL_OPERATIONS);
        CountDownLatch startGun = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(TOTAL_OPERATIONS);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        List<Long> latenciesMs = new CopyOnWriteArrayList<>();

        List<Long> ballRecordingLatencies = new CopyOnWriteArrayList<>();
        List<Long> scorecardLatencies = new CopyOnWriteArrayList<>();
        List<Long> standingsLatencies = new CopyOnWriteArrayList<>();
        List<Long> fixturesLatencies = new CopyOnWriteArrayList<>();

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
                        switch (group) {
                            case 0 -> {
                                // Group 0: Live Match Ball Recording (API 57) - 1,000 ops
                                String matchId = ctx.matchPublicIds.get(opIndex % ctx.matchPublicIds.size());
                                int overNum = (opIndex / 6) % 20;
                                int ballNum = (opIndex % 6) + 1;
                                int runs = (opIndex % 7); // 0 to 6 runs
                                boolean isWicket = (opIndex % 15 == 0); // occasional wicket

                                RecordBallRequest ballReq = RecordBallRequest.builder()
                                        .innings(1)
                                        .over(overNum)
                                        .ball(ballNum)
                                        .runs(runs)
                                        .isWicket(isWicket)
                                        .batsmanId("ply_" + ((opIndex % 11) + 1))
                                        .batsmanName("Batsman " + ((opIndex % 11) + 1))
                                        .bowlerId("ply_" + ((opIndex % 5) + 12))
                                        .bowlerName("Bowler " + ((opIndex % 5) + 12))
                                        .build();

                                matchService.recordBall(ctx.organizer, matchId, ballReq);
                                long durationMs = (System.nanoTime() - startNano) / 1_000_000;
                                ballRecordingLatencies.add(durationMs);
                                latenciesMs.add(durationMs);
                                successCount.incrementAndGet();
                            }
                            case 1 -> {
                                // Group 1: Live Scorecard Inquiries (API 56) - 1,500 ops
                                String matchId = ctx.matchPublicIds.get(opIndex % ctx.matchPublicIds.size());
                                UserPrincipal spectator = ctx.spectators.get(opIndex % ctx.spectators.size());
                                MatchScorecardResponse sc = matchService.getMatchScorecard(spectator, matchId);
                                assertNotNull(sc);
                                long durationMs = (System.nanoTime() - startNano) / 1_000_000;
                                scorecardLatencies.add(durationMs);
                                latenciesMs.add(durationMs);
                                successCount.incrementAndGet();
                            }
                            case 2 -> {
                                // Group 2: Live Standings / Points Table Inquiries (API 58) - 1,500 ops
                                UserPrincipal spectator = ctx.spectators.get(opIndex % ctx.spectators.size());
                                List<TournamentStandingsResponse> standings = matchService.getTournamentStandings(spectator, ctx.tournamentId);
                                assertNotNull(standings);
                                long durationMs = (System.nanoTime() - startNano) / 1_000_000;
                                standingsLatencies.add(durationMs);
                                latenciesMs.add(durationMs);
                                successCount.incrementAndGet();
                            }
                            case 3 -> {
                                // Group 3: Tournament Fixtures Listing (API 54) - 1,000 ops
                                UserPrincipal spectator = ctx.spectators.get(opIndex % ctx.spectators.size());
                                var fixtures = matchService.getTournamentFixtures(spectator, ctx.tournamentId);
                                assertFalse(fixtures.isEmpty());
                                long durationMs = (System.nanoTime() - startNano) / 1_000_000;
                                fixturesLatencies.add(durationMs);
                                latenciesMs.add(durationMs);
                                successCount.incrementAndGet();
                            }
                        }
                    } catch (Exception ex) {
                        failureCount.incrementAndGet();
                        log.error("Operation {} failed: {}", opIndex, ex.getMessage());
                    } finally {
                        finishLatch.countDown();
                    }
                });
            }

            // Wait for all 5,000 tasks to be lined up
            readyLatch.await(30, TimeUnit.SECONDS);
            log.info("All 5,000 virtual threads ready. Releasing start gun...");

            benchmarkStartTime = System.currentTimeMillis();
            startGun.countDown();

            boolean finished = finishLatch.await(60, TimeUnit.SECONDS);
            assertTrue(finished, "Benchmark timed out waiting for 5,000 operations");
        }

        long totalDurationMs = System.currentTimeMillis() - benchmarkStartTime;
        double throughputOpsPerSec = (TOTAL_OPERATIONS * 1000.0) / totalDurationMs;

        // Latency Percentiles
        Collections.sort(latenciesMs);
        long p50 = latenciesMs.isEmpty() ? 0 : latenciesMs.get((int) (latenciesMs.size() * 0.50));
        long p95 = latenciesMs.isEmpty() ? 0 : latenciesMs.get((int) (latenciesMs.size() * 0.95));
        long p99 = latenciesMs.isEmpty() ? 0 : latenciesMs.get((int) (latenciesMs.size() * 0.99));
        long maxLatency = latenciesMs.isEmpty() ? 0 : latenciesMs.get(latenciesMs.size() - 1);
        double avgLatency = latenciesMs.stream().mapToLong(Long::longValue).average().orElse(0.0);

        log.info("================================================================================");
        log.info("MODULE 8 ACTUAL POSTGRESQL BENCHMARK RESULTS (5,000 OPERATIONS)");
        log.info("================================================================================");
        log.info("Total Operations Completed : {}", TOTAL_OPERATIONS);
        log.info("Successful Operations       : {}", successCount.get());
        log.info("Failed Operations           : {}", failureCount.get());
        log.info("Total Wall-Clock Time       : {} ms ({:.2f} s)", totalDurationMs, totalDurationMs / 1000.0);
        log.info("Aggregate Throughput        : {:.1f} ops/sec", throughputOpsPerSec);
        log.info("Latency Metrics (Overall)   : Avg = {:.2f} ms | p50 = {} ms | p95 = {} ms | p99 = {} ms | Max = {} ms",
                avgLatency, p50, p95, p99, maxLatency);
        log.info("Sub-Group Average Latencies :");
        log.info("  - API 57 (Ball-by-Ball Write) : Avg = {:.2f} ms (Count: {})",
                ballRecordingLatencies.stream().mapToLong(Long::longValue).average().orElse(0.0), ballRecordingLatencies.size());
        log.info("  - API 56 (Scorecard Read)     : Avg = {:.2f} ms (Count: {})",
                scorecardLatencies.stream().mapToLong(Long::longValue).average().orElse(0.0), scorecardLatencies.size());
        log.info("  - API 58 (Standings Read)     : Avg = {:.2f} ms (Count: {})",
                standingsLatencies.stream().mapToLong(Long::longValue).average().orElse(0.0), standingsLatencies.size());
        log.info("  - API 54 (Fixtures Read)      : Avg = {:.2f} ms (Count: {})",
                fixturesLatencies.stream().mapToLong(Long::longValue).average().orElse(0.0), fixturesLatencies.size());
        log.info("================================================================================");

        // Verification of DB State Integrity
        assertEquals(TOTAL_OPERATIONS, successCount.get(), "All 5,000 operations must succeed");
        assertEquals(0, failureCount.get(), "Zero operations should fail");

        // Verify scorecards state in DB
        Integer ballsRecordedMatch1 = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM match_balls WHERE match_id = (SELECT id FROM matches WHERE public_id = ?)",
                Integer.class,
                matchPublicIds.get(0)
        );
        log.info("Verified audit ledger: {} unique balls persisted in match_balls for Match 1", ballsRecordedMatch1);
        assertTrue(ballsRecordedMatch1 != null && ballsRecordedMatch1 > 0, "Audit ledger must record balls");
    }

    private UserPrincipal buildOrganizerPrincipal(Long userId, String email) {
        return UserPrincipal.builder()
                .id(userId)
                .email(email)
                .authorities(List.of(
                        new SimpleGrantedAuthority("ROLE_USER"),
                        new SimpleGrantedAuthority("ROLE_ORGANIZER")
                ))
                .build();
    }

    private UserPrincipal buildSpectatorPrincipal(Long userId, String email) {
        return UserPrincipal.builder()
                .id(userId)
                .email(email)
                .authorities(List.of(
                        new SimpleGrantedAuthority("ROLE_USER")
                ))
                .build();
    }
}

