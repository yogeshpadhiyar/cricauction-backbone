package com.cricauction.modules.player.controller;

import com.cricauction.modules.auth.entity.enums.UserRole;
import com.cricauction.modules.player.dto.request.PlayerRegistrationRequest;
import com.cricauction.modules.player.dto.request.UpdatePlayerBasePriceRequest;
import com.cricauction.modules.player.dto.request.UpdatePlayerStatusRequest;
import com.cricauction.modules.player.dto.response.*;
import com.cricauction.modules.player.service.PlayerRegistrationService;
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
public class PlayerConcurrency5000UsersActualDbTest {

    private static final int TOTAL_OPERATIONS = 5000;
    private static final int THREAD_POOL_SIZE = 100;

    @Autowired
    private PlayerRegistrationService playerRegistrationService;

    @Autowired
    private TournamentService tournamentService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    record BenchmarkContext(
            String tournament0Id,
            String tournament1Id,
            Long organizerId,
            String organizerEmail,
            List<Long> seededPlayerIdsForReview
    ) {}

    @Test
    @DisplayName("BENCHMARK: 5,000 Concurrent Operations Module 6 APIs on ACTUAL PostgreSQL DB")
    void execute5000PlayerRegistrationBenchmarkActualDatabase() throws InterruptedException {
        log.info("================================================================================");
        log.info("STARTING BENCHMARK: 5,000 Concurrent Operations Module 6 APIs on ACTUAL PostgreSQL DB");
        log.info("Target DB: jdbc:postgresql://localhost:5432/cricauction");
        log.info("Concurrency: 100 Worker Threads | Total Volume: 5,000 Operations");
        log.info("================================================================================");

        // Fetch or seed user records from PostgreSQL
        List<Map<String, Object>> rawUserRows = jdbcTemplate.queryForList(
                "SELECT id, full_name, email, mobile FROM users ORDER BY id ASC LIMIT 3000"
        );

        if (rawUserRows.size() < 2500) {
            log.info("Self-seeding additional player accounts into PostgreSQL database for 5000 benchmark...");
            List<Object[]> userBatch = new ArrayList<>();
            for (int i = rawUserRows.size() + 1; i <= 3000; i++) {
                userBatch.add(new Object[]{
                        "CricPlayer " + i,
                        "cricplayer" + i + "@cricauction-test.internal",
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

            // Assign PLAYER role (role_id = 2)
            jdbcTemplate.execute(
                    "INSERT INTO user_roles (user_id, role_id) " +
                    "SELECT u.id, 2 FROM users u WHERE NOT EXISTS (" +
                    "    SELECT 1 FROM user_roles ur WHERE ur.user_id = u.id AND ur.role_id = 2" +
                    ")"
            );

            rawUserRows = jdbcTemplate.queryForList(
                    "SELECT id, full_name, email, mobile FROM users ORDER BY id ASC LIMIT 3000"
            );
        }

        final List<Map<String, Object>> userRows = Collections.unmodifiableList(rawUserRows);
        log.info("Loaded {} user accounts for player and organizer operations.", userRows.size());

        // Select organizer
        Map<String, Object> organizerRow = userRows.get(0);
        Long organizerId = ((Number) organizerRow.get("id")).longValue();
        String organizerEmail = (String) organizerRow.get("email");
        UserPrincipal organizer = buildOrganizerPrincipal(organizerId, organizerEmail);

        long runId = System.currentTimeMillis();

        // 1. Pre-create Tournament 0: Large Capacity (5,000 slots) for Concurrent Registration (Group 0)
        CreateTournamentRequest tourn0Req = CreateTournamentRequest.builder()
                .name("Gujarat Super League " + runId)
                .location("Surat")
                .ground("Lalbhai Stadium")
                .startDate(LocalDate.now().plusDays(15))
                .endDate(LocalDate.now().plusDays(25))
                .ballType("Leather")
                .format("T20")
                .entryFee("Free")
                .build();
        CreateTournamentResponse t0Res = tournamentService.createTournament(organizer, tourn0Req);
        String tournament0Id = t0Res.getId();

        TournamentSettingsRequest s0Req = TournamentSettingsRequest.builder()
                .teams(250)
                .squadMinimum(15)
                .squadMaximum(20) // capacity = 250 * 20 = 5,000 players
                .budget(10_000_000L)
                .basePrice(5000L)
                .bidIncrement(1000L)
                .bidTimer(15)
                .allowPlayersToWithdraw(true)
                .build();
        tournamentService.saveSettings(organizer, tournament0Id, s0Req);

        // 2. Pre-create Tournament 1: Seeded with 500 players for Status Tracking, Stats & Reviews (Groups 1, 2, 3)
        CreateTournamentRequest tourn1Req = CreateTournamentRequest.builder()
                .name("Mumbai Champions Trophy " + runId)
                .location("Mumbai")
                .ground("Wankhede Stadium")
                .startDate(LocalDate.now().plusDays(10))
                .endDate(LocalDate.now().plusDays(20))
                .ballType("Leather")
                .format("T20")
                .entryFee("Free")
                .build();
        CreateTournamentResponse t1Res = tournamentService.createTournament(organizer, tourn1Req);
        String tournament1Id = t1Res.getId();

        TournamentSettingsRequest s1Req = TournamentSettingsRequest.builder()
                .teams(50)
                .squadMinimum(10)
                .squadMaximum(20) // capacity = 1,000 players
                .budget(50_000_000L)
                .basePrice(10000L)
                .bidIncrement(2000L)
                .bidTimer(20)
                .allowPlayersToWithdraw(true)
                .build();
        tournamentService.saveSettings(organizer, tournament1Id, s1Req);

        log.info("Pre-seeding 500 player registrations into Tournament 1 for read, stats, and review operations...");
        List<Long> seededPlayerIds = new ArrayList<>();
        String[] roles = {"Batsman", "Bowler", "All-Rounder", "Wicket Keeper"};

        for (int i = 0; i < 500; i++) {
            Map<String, Object> pRow = userRows.get(500 + i);
            Long pId = ((Number) pRow.get("id")).longValue();
            String pEmail = (String) pRow.get("email");
            UserPrincipal playerPrincipal = buildPlayerPrincipal(pId, pEmail);

            PlayerRegistrationRequest regReq = PlayerRegistrationRequest.builder()
                    .playingRole(roles[i % roles.length])
                    .basePrice(5000L + (i * 100L))
                    .city("Surat")
                    .build();

            PlayerRegistrationResponse res = playerRegistrationService.registerPlayer(playerPrincipal, tournament1Id, regReq);
            String rawIdStr = res.getRegistrationId().replace("reg_", "");
            seededPlayerIds.add(Long.valueOf(rawIdStr));
        }

        BenchmarkContext ctx = new BenchmarkContext(
                tournament0Id,
                tournament1Id,
                organizerId,
                organizerEmail,
                seededPlayerIds
        );

        log.info("Tournament setup and pre-seeding complete. Beginning 5,000 concurrent execution...");

        // Concurrent Execution Harness
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(TOTAL_OPERATIONS);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        List<Long> latencies = new CopyOnWriteArrayList<>();
        List<Long> group0Latencies = new CopyOnWriteArrayList<>();
        List<Long> group1Latencies = new CopyOnWriteArrayList<>();
        List<Long> group2Latencies = new CopyOnWriteArrayList<>();
        List<Long> group3Latencies = new CopyOnWriteArrayList<>();

        long benchmarkStartTime = System.currentTimeMillis();

        for (int i = 0; i < TOTAL_OPERATIONS; i++) {
            final int opIndex = i;
            executor.submit(() -> {
                try {
                    startGate.await();
                    long opStart = System.nanoTime();

                    executeSingleOperation(opIndex, runId, userRows, ctx);

                    long opDurationMs = (System.nanoTime() - opStart) / 1_000_000;
                    latencies.add(opDurationMs);
                    successCount.incrementAndGet();

                    int group = opIndex % 4;
                    switch (group) {
                        case 0 -> group0Latencies.add(opDurationMs);
                        case 1 -> group1Latencies.add(opDurationMs);
                        case 2 -> group2Latencies.add(opDurationMs);
                        case 3 -> group3Latencies.add(opDurationMs);
                    }
                } catch (Exception ex) {
                    errorCount.incrementAndGet();
                    log.error("Op #{} failed: {}", opIndex, ex.getMessage());
                } finally {
                    endGate.countDown();
                }
            });
        }

        // Fire all threads simultaneously
        startGate.countDown();
        boolean completed = endGate.await(180, TimeUnit.SECONDS);
        long benchmarkEndTime = System.currentTimeMillis();

        executor.shutdown();

        assertTrue(completed, "Benchmark timed out waiting for 5,000 operations!");

        double totalSeconds = (benchmarkEndTime - benchmarkStartTime) / 1000.0;
        double throughputRps = TOTAL_OPERATIONS / totalSeconds;

        log.info("================================================================================");
        log.info("BENCHMARK COMPLETED: 5,000 Operations against PostgreSQL in {}s", String.format(Locale.US, "%.2f", totalSeconds));
        log.info("Total Operations: {}", TOTAL_OPERATIONS);
        log.info("Successful: {}", successCount.get());
        log.info("Failed: {}", errorCount.get());
        log.info("Success Rate: {}%", String.format(Locale.US, "%.2f", (successCount.get() * 100.0) / TOTAL_OPERATIONS));
        log.info("System Throughput: {} RPS", String.format(Locale.US, "%.2f", throughputRps));
        log.info("================================================================================");

        printLatencyMetrics("Overall (5,000 Operations)", latencies);
        printLatencyMetrics("Group 0: API 34 Player Register (1,250 Ops)", group0Latencies);
        printLatencyMetrics("Group 1: API 35 Player Status Tracking (1,250 Ops)", group1Latencies);
        printLatencyMetrics("Group 2: API 37/38 List & Capacity Stats (1,250 Ops)", group2Latencies);
        printLatencyMetrics("Group 3: API 39/40 Organizer Review & Base Price (1,250 Ops)", group3Latencies);

        assertEquals(0, errorCount.get(), "Expected zero errors during concurrent benchmark!");
        assertEquals(TOTAL_OPERATIONS, successCount.get());

        // Verify live database state
        verifyPostgreSQLRecords(tournament0Id, tournament1Id);
    }

    private void executeSingleOperation(int index, long runId, List<Map<String, Object>> userRows, BenchmarkContext ctx) {
        int group = index % 4;

        switch (group) {
            case 0 -> {
                // Group 0 (1,250 ops): POST /tournaments/{id}/players/register (API 34)
                // Use unique user from pool (offset 1500 to 2750)
                int userIndex = 1500 + (index / 4);
                Map<String, Object> playerRow = userRows.get(userIndex % userRows.size());
                Long pId = ((Number) playerRow.get("id")).longValue();
                String pEmail = (String) playerRow.get("email");
                UserPrincipal player = buildPlayerPrincipal(pId, pEmail);

                String[] roles = {"Batsman", "Bowler", "All-Rounder", "Wicket Keeper"};
                PlayerRegistrationRequest req = PlayerRegistrationRequest.builder()
                        .playingRole(roles[index % roles.length])
                        .battingStyle(index % 2 == 0 ? "Right-hand bat" : "Left-hand bat")
                        .bowlingStyle(index % 2 == 0 ? "Right-arm fast" : "Left-arm spin")
                        .basePrice(5000L + (index * 10L))
                        .city("Surat")
                        .jerseySize("L")
                        .cricHeroesLink("https://cricheroes.in/player/" + pId)
                        .build();

                PlayerRegistrationResponse res = playerRegistrationService.registerPlayer(player, ctx.tournament0Id(), req);
                assertNotNull(res.getRegistrationId());
                assertEquals("Registered", res.getStatus());
            }
            case 1 -> {
                // Group 1 (1,250 ops): GET /tournaments/{id}/players/my-status (API 35 - Read Replica)
                int seededIndex = (index / 4) % 500;
                Map<String, Object> pRow = userRows.get(500 + seededIndex);
                Long pId = ((Number) pRow.get("id")).longValue();
                String pEmail = (String) pRow.get("email");
                UserPrincipal player = buildPlayerPrincipal(pId, pEmail);

                PlayerRegistrationStatusResponse res = playerRegistrationService.getMyRegistrationStatus(player, ctx.tournament1Id());
                assertNotNull(res.getTournamentId());
                assertNotNull(res.getStatus());
            }
            case 2 -> {
                // Group 2 (1,250 ops): GET /tournaments/{id}/players/stats (API 38) & GET /tournaments/{id}/players (API 37)
                UserPrincipal organizer = buildOrganizerPrincipal(ctx.organizerId(), ctx.organizerEmail());
                if (index % 2 == 0) {
                    PlayerCapacityStatsResponse res = playerRegistrationService.getRegistrationCapacityStats(organizer, ctx.tournament1Id());
                    assertTrue(res.getCapacity() > 0);
                    assertTrue(res.getRegistered() > 0);
                    assertNotNull(res.getRoleBreakdown());
                } else {
                    String[] filters = {"All", "Batsman", "Bowler", "All-Rounder", "Wicket Keeper"};
                    String filter = filters[(index / 2) % filters.length];
                    PlayerListResponse res = playerRegistrationService.listRegisteredPlayers(organizer, ctx.tournament1Id(), filter, null, 1, 20);
                    assertNotNull(res.getPlayers());
                    assertTrue(res.getTotal() > 0);
                }
            }
            case 3 -> {
                // Group 3 (1,250 ops): PATCH status (API 39) or base-price (API 40)
                UserPrincipal organizer = buildOrganizerPrincipal(ctx.organizerId(), ctx.organizerEmail());
                int targetIndex = (index / 4) % ctx.seededPlayerIdsForReview().size();
                Long targetPlayerId = ctx.seededPlayerIdsForReview().get(targetIndex);

                if (index % 2 == 0) {
                    String[] statuses = {"Approved", "Registered", "Pending"};
                    String newStatus = statuses[(index / 2) % statuses.length];
                    UpdatePlayerStatusRequest statusReq = UpdatePlayerStatusRequest.builder()
                            .status(newStatus)
                            .reason("Batch verified #" + index)
                            .build();
                    playerRegistrationService.updatePlayerStatus(organizer, ctx.tournament1Id(), targetPlayerId, statusReq);
                } else {
                    long newPrice = 10000L + (index * 50L);
                    UpdatePlayerBasePriceRequest priceReq = UpdatePlayerBasePriceRequest.builder()
                            .basePrice(newPrice)
                            .build();
                    playerRegistrationService.updatePlayerBasePrice(organizer, ctx.tournament1Id(), targetPlayerId, priceReq);
                }
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

    private UserPrincipal buildPlayerPrincipal(Long userId, String email) {
        return UserPrincipal.builder()
                .id(userId)
                .fullName("Player " + userId)
                .email(email)
                .role(UserRole.PLAYER)
                .roles(List.of("player"))
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_PLAYER")))
                .isVerified(true)
                .isActive(true)
                .build();
    }

    private void printLatencyMetrics(String label, List<Long> list) {
        if (list.isEmpty()) return;
        List<Long> sorted = new ArrayList<>(list);
        Collections.sort(sorted);

        long min = sorted.get(0);
        long max = sorted.get(sorted.size() - 1);
        long p50 = sorted.get((int) (sorted.size() * 0.50));
        long p90 = sorted.get((int) (sorted.size() * 0.90));
        long p95 = sorted.get((int) (sorted.size() * 0.95));
        long p99 = sorted.get((int) (sorted.size() * 0.99));
        double avg = sorted.stream().mapToLong(Long::longValue).average().orElse(0.0);

        log.info("--------------------------------------------------------------------------------");
        log.info("LATENCY STATS: {}", label);
        log.info("Count: {} ops | Min: {}ms | Avg: {}ms | Max: {}ms", sorted.size(), min, String.format(Locale.US, "%.2f", avg), max);
        log.info("P50: {}ms | P90: {}ms | P95: {}ms | P99: {}ms", p50, p90, p95, p99);
        log.info("--------------------------------------------------------------------------------");
    }

    private void verifyPostgreSQLRecords(String tournament0Id, String tournament1Id) {
        Long t0PlayerCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM tournament_players tp JOIN tournaments t ON tp.tournament_id = t.id WHERE t.public_id = ?",
                Long.class, tournament0Id
        );
        Long t1PlayerCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM tournament_players tp JOIN tournaments t ON tp.tournament_id = t.id WHERE t.public_id = ?",
                Long.class, tournament1Id
        );
        Integer t0SettingsCounter = jdbcTemplate.queryForObject(
                "SELECT ts.registered_players_count FROM tournament_settings ts JOIN tournaments t ON ts.tournament_id = t.id WHERE t.public_id = ?",
                Integer.class, tournament0Id
        );

        log.info("VERIFICATION: Tournament 0 Registered Players in DB: {}", t0PlayerCount);
        log.info("VERIFICATION: Tournament 0 Settings Counter in DB: {}", t0SettingsCounter);
        log.info("VERIFICATION: Tournament 1 Seeded Players in DB: {}", t1PlayerCount);

        assertEquals(1250L, t0PlayerCount.longValue(), "Expected exactly 1,250 registered players in Tournament 0!");
        assertEquals(1250, t0SettingsCounter.intValue(), "Expected atomic reservation counter in Tournament 0 to match exactly 1,250!");
        assertEquals(500L, t1PlayerCount.longValue(), "Expected 500 players in Tournament 1!");
    }
}
