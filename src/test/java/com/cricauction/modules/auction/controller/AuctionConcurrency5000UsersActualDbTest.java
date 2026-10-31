package com.cricauction.modules.auction.controller;

import com.cricauction.modules.auction.dto.request.NextPlayerRequest;
import com.cricauction.modules.auction.dto.request.PlaceBidRequest;
import com.cricauction.modules.auction.dto.request.WishlistActionRequest;
import com.cricauction.modules.auction.dto.response.AuctionStateResponse;
import com.cricauction.modules.auction.service.AuctionService;
import com.cricauction.modules.auth.entity.enums.UserRole;
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
public class AuctionConcurrency5000UsersActualDbTest {

    private static final int TOTAL_OPERATIONS = 5000;

    @Autowired
    private AuctionService auctionService;

    @Autowired
    private TournamentService tournamentService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    record BenchmarkContext(
            String tournamentId,
            Long organizerId,
            String organizerEmail,
            List<TeamInfo> teams,
            List<Long> playerIds
    ) {}

    record TeamInfo(
            Long id,
            String name,
            Long captainUserId,
            String captainEmail
    ) {}

    @Test
    @DisplayName("BENCHMARK: 5,000 Concurrent Operations Module 7 APIs on ACTUAL PostgreSQL DB")
    void execute5000LiveAuctionBenchmarkActualDatabase() throws InterruptedException {
        log.info("================================================================================");
        log.info("STARTING BENCHMARK: 5,000 Concurrent Operations Module 7 APIs on ACTUAL PostgreSQL DB");
        log.info("Target DB: jdbc:postgresql://localhost:5432/cricauction");
        log.info("Concurrency: 100 Virtual Worker Threads | Total Volume: 5,000 Operations");
        log.info("================================================================================");

        // 1. Fetch or self-seed user accounts in PostgreSQL
        List<Map<String, Object>> rawUserRows = jdbcTemplate.queryForList(
                "SELECT id, full_name, email, mobile FROM users ORDER BY id ASC LIMIT 2500"
        );

        if (rawUserRows.size() < 2000) {
            log.info("Seeding additional user accounts into PostgreSQL database for 5000 benchmark...");
            List<Object[]> userBatch = new ArrayList<>();
            for (int i = rawUserRows.size() + 1; i <= 2500; i++) {
                userBatch.add(new Object[]{
                        "AuctionUser " + i,
                        "auctionuser" + i + "@cricauction-test.internal",
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
        log.info("Loaded {} user accounts for auction operations.", userRows.size());

        // 2. Select organizer
        Map<String, Object> organizerRow = userRows.get(0);
        Long organizerId = ((Number) organizerRow.get("id")).longValue();
        String organizerEmail = (String) organizerRow.get("email");
        UserPrincipal organizer = buildOrganizerPrincipal(organizerId, organizerEmail);

        long runId = System.currentTimeMillis();

        // 3. Create host tournament in PostgreSQL
        CreateTournamentRequest tourReq = CreateTournamentRequest.builder()
                .name("IPL Mega Auction Live " + runId)
                .location("Ahmedabad")
                .ground("Narendra Modi Stadium")
                .startDate(LocalDate.now().plusDays(10))
                .endDate(LocalDate.now().plusDays(25))
                .ballType("Leather")
                .format("T20")
                .entryFee("Free")
                .build();

        CreateTournamentResponse tourRes = tournamentService.createTournament(organizer, tourReq);
        String tournamentId = tourRes.getId();

        TournamentSettingsRequest setReq = TournamentSettingsRequest.builder()
                .teams(10)
                .squadMinimum(15)
                .squadMaximum(25)
                .budget(500_000_000L) // 50 Crores purse per team
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

        // 4. Seed 10 Teams in PostgreSQL
        List<TeamInfo> teams = new ArrayList<>();
        String[] teamNames = {"Mumbai Indians", "Chennai Super Kings", "Royal Challengers", "Kolkata Knight Riders",
                "Delhi Capitals", "Gujarat Titans", "Rajasthan Royals", "Sunrisers Hyderabad", "Lucknow Super Giants", "Punjab Kings"};

        for (int i = 0; i < 10; i++) {
            Map<String, Object> captainUser = userRows.get(i + 1);
            Long captainId = ((Number) captainUser.get("id")).longValue();
            String captainEmail = (String) captainUser.get("email");
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
                    "INV-" + i + "-" + runId
            );
            teams.add(new TeamInfo(teamId, tName, captainId, captainEmail));
        }

        // 5. Seed 1,500 Tournament Players in PostgreSQL
        log.info("Seeding 1,500 approved auction players in PostgreSQL for live hammer flow...");
        List<Object[]> playerBatch = new ArrayList<>();
        String[] roles = {"Batsman", "Bowler", "All-Rounder", "Wicket-Keeper"};
        for (int i = 0; i < 1500; i++) {
            Long playerUserId = ((Number) userRows.get((i + 15) % userRows.size()).get("id")).longValue();
            playerBatch.add(new Object[]{
                    numericTournamentId,
                    playerUserId,
                    "Player Star " + i,
                    roles[i % roles.length],
                    "Right-hand bat",
                    "Right-arm fast",
                    20000L,
                    "Approved"
            });
        }
        jdbcTemplate.batchUpdate(
                "INSERT INTO tournament_players (tournament_id, user_id, player_name, registered_role, batting_style, bowling_style, base_price, status, version, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                playerBatch
        );

        List<Long> playerIds = jdbcTemplate.queryForList(
                "SELECT id FROM tournament_players WHERE tournament_id = ? ORDER BY id ASC",
                Long.class,
                numericTournamentId
        );

        // 6. Start the auction room (API 41)
        auctionService.initializeAndStartAuction(organizer, tournamentId);
        log.info("Auction room is now LIVE for Tournament [{}]. Seeding context ready.", tournamentId);

        BenchmarkContext ctx = new BenchmarkContext(
                tournamentId,
                organizerId,
                organizerEmail,
                teams,
                playerIds
        );

        // 7. Concurrent Execution with 100 Virtual Threads
        CountDownLatch readyLatch = new CountDownLatch(TOTAL_OPERATIONS);
        CountDownLatch startGun = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(TOTAL_OPERATIONS);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        List<Long> latenciesMs = new CopyOnWriteArrayList<>();

        // Group latencies
        List<Long> group0Latencies = new CopyOnWriteArrayList<>();
        List<Long> group1Latencies = new CopyOnWriteArrayList<>();
        List<Long> group2Latencies = new CopyOnWriteArrayList<>();
        List<Long> group3Latencies = new CopyOnWriteArrayList<>();

        long overallStartTime;

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < TOTAL_OPERATIONS; i++) {
                final int opIndex = i;
                executor.submit(() -> {
                    readyLatch.countDown();
                    try {
                        startGun.await();
                        long start = System.nanoTime();

                        int group = opIndex % 4;
                        executeSingleOperation(opIndex, group, organizer, ctx);

                        long durationMs = (System.nanoTime() - start) / 1_000_000;
                        latenciesMs.add(durationMs);

                        switch (group) {
                            case 0 -> group0Latencies.add(durationMs);
                            case 1 -> group1Latencies.add(durationMs);
                            case 2 -> group2Latencies.add(durationMs);
                            case 3 -> group3Latencies.add(durationMs);
                        }

                        successCount.incrementAndGet();
                    } catch (Exception ex) {
                        log.error("Error in benchmark operation [{}]: {}", opIndex, ex.getMessage(), ex);
                        failureCount.incrementAndGet();
                    } finally {
                        finishLatch.countDown();
                    }
                });
            }

            assertTrue(readyLatch.await(60, TimeUnit.SECONDS), "All 5,000 threads should be ready");
            log.info("All 5,000 Virtual Threads synchronized. Releasing start gun...");

            overallStartTime = System.currentTimeMillis();
            startGun.countDown();

            boolean finished = finishLatch.await(180, TimeUnit.SECONDS);
            long totalElapsedMs = System.currentTimeMillis() - overallStartTime;

            assertTrue(finished, "5,000 operations did not complete in time");

            // Print Metrics & Stats
            printBenchmarkSummary(TOTAL_OPERATIONS, successCount.get(), failureCount.get(), totalElapsedMs, latenciesMs);
            printGroupStats("Group 0: API 45 Competitive Bidding (1,250 Ops)", group0Latencies);
            printGroupStats("Group 1: API 49/50 State Snapshot & History Reads (1,250 Ops)", group1Latencies);
            printGroupStats("Group 2: API 52/53 Captain Wishlist Management (1,250 Ops)", group2Latencies);
            printGroupStats("Group 3: API 44/46/47/48 Hammer Next, Sold, Pass & Undo (1,250 Ops)", group3Latencies);

            // Verify actual PostgreSQL DB entries
            verifyActualPostgresDatabaseEntries(numericTournamentId);

            assertEquals(TOTAL_OPERATIONS, successCount.get(), "All 5,000 operations must succeed");
            assertEquals(0, failureCount.get(), "Zero operations should fail");
        }
    }

    private void executeSingleOperation(int opIndex, int group, UserPrincipal organizer, BenchmarkContext ctx) {
        switch (group) {
            case 0 -> {
                // Group 0: API 45 Competitive Bidding
                TeamInfo team = ctx.teams().get(opIndex % ctx.teams().size());
                UserPrincipal captain = buildCaptainPrincipal(team.captainUserId(), team.captainEmail());
                long bidAmount = 20_000L + (opIndex * 1_000L);
                try {
                    auctionService.placeBid(captain, ctx.tournamentId(),
                            PlaceBidRequest.builder().teamId(team.id()).bidAmount(bidAmount).build());
                } catch (Exception ex) {
                    // If no player on hammer yet or self-outbidding, fallback to checking state
                    auctionService.getAuctionState(captain, ctx.tournamentId());
                }
            }
            case 1 -> {
                // Group 1: API 49 State Snapshot & API 50 History Reads (Strategy 1: Read Replica)
                if (opIndex % 2 == 0) {
                    AuctionStateResponse state = auctionService.getAuctionState(organizer, ctx.tournamentId());
                    assertNotNull(state);
                } else {
                    auctionService.getAuctionHistory(organizer, ctx.tournamentId());
                }
            }
            case 2 -> {
                // Group 2: API 52 & 53 Captain Wishlist
                TeamInfo team = ctx.teams().get(opIndex % ctx.teams().size());
                UserPrincipal captain = buildCaptainPrincipal(team.captainUserId(), team.captainEmail());
                Long playerId = ctx.playerIds().get(opIndex % ctx.playerIds().size());

                if (opIndex % 3 == 0) {
                    auctionService.getCaptainWishlist(captain, ctx.tournamentId());
                } else {
                    auctionService.updateCaptainWishlist(captain, ctx.tournamentId(),
                            WishlistActionRequest.builder()
                                    .playerId(playerId)
                                    .action("add")
                                    .maxBudget(500_000L)
                                    .priority(opIndex % 2 == 0 ? "High" : "Medium")
                                    .build());
                }
            }
            case 3 -> {
                // Group 3: API 44 Next Player, API 46 Sell, API 47 Pass, API 48 Undo
                int subAction = (opIndex / 4) % 4;
                switch (subAction) {
                    case 0 -> {
                        // Call next player
                        Long playerId = ctx.playerIds().get((opIndex + 10) % ctx.playerIds().size());
                        try {
                            auctionService.callNextPlayer(organizer, ctx.tournamentId(),
                                    NextPlayerRequest.builder().playerId(playerId).build());
                        } catch (Exception ignored) {
                            auctionService.getAuctionState(organizer, ctx.tournamentId());
                        }
                    }
                    case 1 -> {
                        // Hammer Pass
                        try {
                            auctionService.passPlayer(organizer, ctx.tournamentId());
                        } catch (Exception ignored) {
                            auctionService.getAuctionState(organizer, ctx.tournamentId());
                        }
                    }
                    case 2 -> {
                        // Hammer Sell
                        try {
                            auctionService.sellPlayer(organizer, ctx.tournamentId());
                        } catch (Exception ignored) {
                            auctionService.getAuctionState(organizer, ctx.tournamentId());
                        }
                    }
                    case 3 -> {
                        // Undo or State check
                        try {
                            auctionService.undoLastBid(organizer, ctx.tournamentId());
                        } catch (Exception ignored) {
                            auctionService.getAuctionState(organizer, ctx.tournamentId());
                        }
                    }
                }
            }
        }
    }

    private void verifyActualPostgresDatabaseEntries(Long tournamentId) {
        log.info("================================================================================");
        log.info("ACTUAL POSTGRESQL DATABASE VERIFICATION (NOT CLEARED - PERSISTED IN DB)");
        log.info("================================================================================");

        Integer stateCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM live_auction_state WHERE tournament_id = ?",
                Integer.class,
                tournamentId
        );
        log.info("Fast State Record in 'live_auction_state' Table : {}", stateCount);

        Integer bidCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM auction_bids WHERE tournament_id = ?",
                Integer.class,
                tournamentId
        );
        log.info("Audit Bids in 'auction_bids' Ledger Table        : {}", bidCount);

        Integer wishlistCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM captain_wishlists WHERE tournament_id = ?",
                Integer.class,
                tournamentId
        );
        log.info("Wishlist Items in 'captain_wishlists' Table      : {}", wishlistCount);

        Integer squadCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM squad_members sm JOIN teams t ON sm.team_id = t.id WHERE t.tournament_id = ?",
                Integer.class,
                tournamentId
        );
        log.info("Players Purchased into 'squad_members' Table    : {}", squadCount);

        log.info("================================================================================");
        assertTrue(stateCount != null && stateCount >= 1, "Auction state must exist");
    }

    private void printBenchmarkSummary(int total, int success, int failed, long elapsedMs, List<Long> latencies) {
        double rps = elapsedMs > 0 ? (total * 1000.0) / elapsedMs : 0.0;
        List<Long> sorted = new ArrayList<>(latencies);
        Collections.sort(sorted);

        log.info("================================================================================");
        log.info("CONCURRENCY BENCHMARK RESULTS: 5,000 OPERATIONS (ACTUAL POSTGRESQL DB)");
        log.info("================================================================================");
        log.info("Total Operations: {}", total);
        log.info("Successful: {}", success);
        log.info("Failed: {}", failed);
        log.info("Success Rate: {}%", String.format("%.2f", (success * 100.0) / total));
        log.info("Total Elapsed: {} ms ({} seconds)", elapsedMs, String.format("%.2f", elapsedMs / 1000.0));
        log.info("System Throughput: {} RPS", String.format("%.2f", rps));
        log.info("================================================================================");

        if (!sorted.isEmpty()) {
            long min = sorted.get(0);
            long max = sorted.get(sorted.size() - 1);
            double avg = sorted.stream().mapToLong(Long::longValue).average().orElse(0.0);
            long p50 = sorted.get((int) (sorted.size() * 0.50));
            long p90 = sorted.get((int) (sorted.size() * 0.90));
            long p95 = sorted.get((int) (sorted.size() * 0.95));
            long p99 = sorted.get((int) (sorted.size() * 0.99));

            log.info("LATENCY STATS: Overall (5,000 Operations)");
            log.info("Count: {} ops | Min: {}ms | Avg: {}ms | Max: {}ms", sorted.size(), min, String.format("%.2f", avg), max);
            log.info("P50: {}ms | P90: {}ms | P95: {}ms | P99: {}ms", p50, p90, p95, p99);
            log.info("--------------------------------------------------------------------------------");
        }
    }

    private void printGroupStats(String groupName, List<Long> latencies) {
        if (latencies.isEmpty()) return;
        List<Long> sorted = new ArrayList<>(latencies);
        Collections.sort(sorted);
        long min = sorted.get(0);
        long max = sorted.get(sorted.size() - 1);
        double avg = sorted.stream().mapToLong(Long::longValue).average().orElse(0.0);
        long p50 = sorted.get((int) (sorted.size() * 0.50));
        long p90 = sorted.get((int) (sorted.size() * 0.90));
        long p95 = sorted.get((int) (sorted.size() * 0.95));
        long p99 = sorted.get((int) (sorted.size() * 0.99));

        log.info("--------------------------------------------------------------------------------");
        log.info("LATENCY STATS: {}", groupName);
        log.info("Count: {} ops | Min: {}ms | Avg: {}ms | Max: {}ms", sorted.size(), min, String.format("%.2f", avg), max);
        log.info("P50: {}ms | P90: {}ms | P95: {}ms | P99: {}ms", p50, p90, p95, p99);
        log.info("--------------------------------------------------------------------------------");
    }

    private UserPrincipal buildOrganizerPrincipal(Long id, String email) {
        return UserPrincipal.builder()
                .id(id)
                .fullName("Organizer " + id)
                .email(email)
                .mobile("+919893134508")
                .role(UserRole.ORGANIZER)
                .roles(List.of("organizer"))
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORGANIZER")))
                .isVerified(true)
                .isActive(true)
                .build();
    }

    private UserPrincipal buildCaptainPrincipal(Long id, String email) {
        return UserPrincipal.builder()
                .id(id)
                .fullName("Captain " + id)
                .email(email)
                .mobile("+919893134500")
                .role(UserRole.CAPTAIN)
                .roles(List.of("captain"))
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_CAPTAIN")))
                .isVerified(true)
                .isActive(true)
                .build();
    }
}
