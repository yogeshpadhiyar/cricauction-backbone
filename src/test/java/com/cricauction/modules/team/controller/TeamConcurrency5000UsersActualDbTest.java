package com.cricauction.modules.team.controller;

import com.cricauction.modules.auth.entity.enums.UserRole;
import com.cricauction.modules.team.dto.request.AddTeamRequest;
import com.cricauction.modules.team.dto.request.InviteCaptainRequest;
import com.cricauction.modules.team.dto.request.JoinTeamRequest;
import com.cricauction.modules.team.dto.response.*;
import com.cricauction.modules.team.service.TeamService;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AUTOMATION BENCHMARK: 5,000 Concurrent Users against Actual PostgreSQL Database for Module 5 APIs.
 *
 * Evaluates high-concurrency throughput and latency percentiles across:
 * - 1,250 Users: POST /tournaments/{id}/teams (Add Team within tournament capacity limits - Screen 18)
 * - 1,250 Users: POST /tournaments/{id}/teams/{teamId}/invite (Dispatch WhatsApp / SMS Captain Invites - Screen 19)
 * - 1,250 Users: POST /teams/join (Concurrent Captain Claiming - Optimistic Lock, Multi-Role & Squad Injection - Screen 20)
 * - 1,250 Users: GET /teams/{teamId}/squad & GET /tournaments/{id}/teams (High-Speed Squad Roster & Purse Reads - Screen 21)
 */
@Slf4j
@SpringBootTest
@ActiveProfiles("postgres")
public class TeamConcurrency5000UsersActualDbTest {

    private static final int TOTAL_OPERATIONS = 5000;

    @Autowired
    private TeamService teamService;

    @Autowired
    private TournamentService tournamentService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    record BenchmarkContext(
            String tournament0Id,
            String tournament1Id,
            Long organizerId,
            String organizerEmail,
            List<Long> teamsForInvite,
            List<String> claimInviteCodes,
            List<Long> teamsForSquadRead
    ) {}

    @Test
    @DisplayName("BENCHMARK: 5,000 Concurrent Operations Module 5 APIs on ACTUAL PostgreSQL DB")
    void execute5000TeamBenchmarkActualDatabase() throws InterruptedException {
        log.info("================================================================================");
        log.info("STARTING BENCHMARK: 5,000 Concurrent Operations Module 5 APIs on ACTUAL PostgreSQL DB");
        log.info("Target DB: jdbc:postgresql://localhost:5432/cricauction");
        log.info("================================================================================");

        // Fetch or seed user records from PostgreSQL
        List<Map<String, Object>> rawUserRows = jdbcTemplate.queryForList(
                "SELECT id, full_name, email, mobile FROM users ORDER BY id ASC LIMIT 2500"
        );

        if (rawUserRows.size() < 2500) {
            log.info("Self-seeding additional user accounts into PostgreSQL database for 5000 benchmark...");
            List<Object[]> userBatch = new ArrayList<>();
            for (int i = rawUserRows.size() + 1; i <= 2600; i++) {
                userBatch.add(new Object[]{
                        "CricUser " + i,
                        "cricuser" + i + "@cricauction-test.internal",
                        "+9198" + String.format("%08d", i),
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

            // Assign ORGANIZER and PLAYER roles
            jdbcTemplate.execute(
                    "INSERT INTO user_roles (user_id, role_id) " +
                    "SELECT u.id, 1 FROM users u WHERE NOT EXISTS (" +
                    "    SELECT 1 FROM user_roles ur WHERE ur.user_id = u.id AND ur.role_id = 1" +
                    ")"
            );

            rawUserRows = jdbcTemplate.queryForList(
                    "SELECT id, full_name, email, mobile FROM users ORDER BY id ASC LIMIT 2500"
            );
        }

        final List<Map<String, Object>> userRows = Collections.unmodifiableList(rawUserRows);
        log.info("Loaded {} user accounts for concurrent operations.", userRows.size());

        // Pre-create host tournaments and seed teams for benchmark operations
        log.info("Preparing host tournaments and team fixtures in PostgreSQL...");
        Map<String, Object> hostOrganizerRow = userRows.get(0);
        Long hostOrganizerId = ((Number) hostOrganizerRow.get("id")).longValue();
        String hostOrganizerEmail = (String) hostOrganizerRow.get("email");
        UserPrincipal hostOrganizer = buildOrganizerPrincipal(hostOrganizerId, hostOrganizerEmail);

        // Tournament for Group 0 (Capacity 2,000 teams)
        CreateTournamentRequest tourReq0 = CreateTournamentRequest.builder()
                .name("Grand Premier League " + System.currentTimeMillis())
                .location("Narendra Modi Stadium, Ahmedabad")
                .ground("Main Ground")
                .startDate(LocalDate.now().plusDays(20))
                .endDate(LocalDate.now().plusDays(30))
                .auctionDate(LocalDate.now().plusDays(10))
                .auctionTime("18:00")
                .ballType("Leather")
                .format("T20")
                .build();
        CreateTournamentResponse hostTour0 = tournamentService.createTournament(hostOrganizer, tourReq0);
        tournamentService.saveSettings(hostOrganizer, hostTour0.getId(), TournamentSettingsRequest.builder()
                .teams(2000)
                .budget(100_000_000L)
                .build());

        // Tournament for Group 1 (Invite dispatch) & Group 2 (Claim invites) & Group 3 (Squad reads)
        CreateTournamentRequest tourReq1 = CreateTournamentRequest.builder()
                .name("Championship Arena " + System.currentTimeMillis())
                .location("Wankhede Stadium, Mumbai")
                .ground("Wankhede Ground")
                .startDate(LocalDate.now().plusDays(20))
                .endDate(LocalDate.now().plusDays(30))
                .auctionDate(LocalDate.now().plusDays(10))
                .auctionTime("19:00")
                .ballType("Leather")
                .format("T20")
                .build();
        CreateTournamentResponse hostTour1 = tournamentService.createTournament(hostOrganizer, tourReq1);
        tournamentService.saveSettings(hostOrganizer, hostTour1.getId(), TournamentSettingsRequest.builder()
                .teams(4000)
                .budget(100_000_000L)
                .build());

        // Pre-create 1,250 teams for Group 1 (Invites)
        List<Long> teamsForInvite = new CopyOnWriteArrayList<>();
        log.info("Pre-creating 1,250 teams in PostgreSQL for concurrent captain invitation benchmark...");
        for (int i = 1; i <= 1250; i++) {
            AddTeamRequest req = AddTeamRequest.builder()
                    .name("Team Invite Target " + i)
                    .captainName("Captain Candidate " + i)
                    .captainMobile("+9191000" + String.format("%05d", i))
                    .build();
            AddTeamResponse res = teamService.addTeam(hostOrganizer, hostTour1.getId(), req);
            teamsForInvite.add(res.getId());
        }

        // Pre-create 1,250 teams for Group 2 (Claims)
        List<String> claimInviteCodes = new CopyOnWriteArrayList<>();
        log.info("Pre-creating 1,250 teams with invite codes in PostgreSQL for concurrent claim benchmark...");
        for (int i = 1; i <= 1250; i++) {
            AddTeamRequest req = AddTeamRequest.builder()
                    .name("Team Claim Target " + i)
                    .captainName("Claim Candidate " + i)
                    .captainMobile("+9192000" + String.format("%05d", i))
                    .build();
            AddTeamResponse res = teamService.addTeam(hostOrganizer, hostTour1.getId(), req);
            // Trigger invite to set status to 'Invited'
            teamService.sendCaptainInvite(hostOrganizer, hostTour1.getId(), res.getId(),
                    InviteCaptainRequest.builder().channel("whatsapp").build());
            claimInviteCodes.add(res.getInviteCode());
        }

        // Group 3 teams for squad reads
        List<Long> teamsForSquadRead = Collections.unmodifiableList(teamsForInvite);

        BenchmarkContext ctx = new BenchmarkContext(
                hostTour0.getId(),
                hostTour1.getId(),
                hostOrganizerId,
                hostOrganizerEmail,
                teamsForInvite,
                claimInviteCodes,
                teamsForSquadRead
        );

        log.info("Host tournament setup complete. Proceeding with 5,000 concurrent Virtual Thread benchmark...");

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

                        executeSingleOperation(opIndex, runId, userRows, ctx);

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
                                        BenchmarkContext ctx) {
        int group = index % 4;

        switch (group) {
            case 0 -> {
                // Group 0 (1,250 ops): POST /tournaments/{id}/teams (Add Team within capacity - Screen 18)
                UserPrincipal organizer = buildOrganizerPrincipal(ctx.organizerId(), ctx.organizerEmail());
                AddTeamRequest req = AddTeamRequest.builder()
                        .name(String.format("Titans %d-%04d", runId, index))
                        .captainName("Captain " + index)
                        .captainMobile("+9193000" + String.format("%05d", index % 100000))
                        .build();

                AddTeamResponse res = teamService.addTeam(organizer, ctx.tournament0Id(), req);
                assertTrue(res.getId() != null);
                assertEquals("Pending", res.getStatus());
            }
            case 1 -> {
                // Group 1 (1,250 ops): POST /tournaments/{id}/teams/{teamId}/invite (Dispatch Captain Invites - Screen 19)
                UserPrincipal organizer = buildOrganizerPrincipal(ctx.organizerId(), ctx.organizerEmail());
                int targetIndex = index / 4;
                Long targetTeamId = ctx.teamsForInvite().get(targetIndex % ctx.teamsForInvite().size());

                InviteCaptainResponse res = teamService.sendCaptainInvite(organizer, ctx.tournament1Id(), targetTeamId,
                        InviteCaptainRequest.builder().channel("whatsapp").build());
                assertEquals("Invited", res.getStatus());
                assertTrue(res.getInviteLink() != null && !res.getInviteLink().isBlank());
            }
            case 2 -> {
                // Group 2 (1,250 ops): POST /teams/join (Captain Claims Invite - Optimistic Locking & Squad Injection - Screen 20)
                int targetIndex = index / 4;
                String inviteCode = ctx.claimInviteCodes().get(targetIndex % ctx.claimInviteCodes().size());

                // Unique user claiming this specific team
                Map<String, Object> captainUserRow = userRows.get((1000 + targetIndex) % userRows.size());
                Long captainUserId = ((Number) captainUserRow.get("id")).longValue();
                String captainEmail = (String) captainUserRow.get("email");
                UserPrincipal captainPrincipal = buildPlayerPrincipal(captainUserId, captainEmail);

                JoinTeamRequest req = JoinTeamRequest.builder()
                        .inviteCode(inviteCode)
                        .build();

                JoinTeamResponse res = teamService.joinTeam(captainPrincipal, req);
                assertEquals("captain", res.getRole());
                assertTrue(res.getTeamId() != null);
            }
            case 3 -> {
                // Group 3 (1,250 ops): GET /teams/{teamId}/squad & GET /tournaments/{id}/teams (High-Speed Read - Screen 21)
                int targetIndex = index / 4;
                Long targetTeamId = ctx.teamsForSquadRead().get(targetIndex % ctx.teamsForSquadRead().size());
                UserPrincipal viewer = buildOrganizerPrincipal(ctx.organizerId(), ctx.organizerEmail());

                if (index % 2 == 0) {
                    TeamSquadResponse res = teamService.getTeamSquad(viewer, targetTeamId);
                    assertTrue(res.getTeamId() != null);
                    assertTrue(res.getTotalPurse() > 0);
                } else {
                    TournamentTeamsResponse res = teamService.getTournamentTeams(viewer, ctx.tournament1Id());
                    assertTrue(res.getTotalTeams() > 0);
                    assertTrue(res.getTeams() != null);
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

    private void verifyActualPostgresDatabaseEntries() {
        Long totalTeams = jdbcTemplate.queryForObject("SELECT count(*) FROM teams", Long.class);
        Long totalSquadMembers = jdbcTemplate.queryForObject("SELECT count(*) FROM squad_members", Long.class);
        Long invitedCount = jdbcTemplate.queryForObject("SELECT count(*) FROM teams WHERE status = 'Invited'", Long.class);
        Long acceptedCount = jdbcTemplate.queryForObject("SELECT count(*) FROM teams WHERE status = 'Accepted'", Long.class);
        Long pendingCount = jdbcTemplate.queryForObject("SELECT count(*) FROM teams WHERE status = 'Pending'", Long.class);

        log.info("================================================================================");
        log.info("ACTUAL POSTGRESQL DATABASE VERIFICATION (MODULE 5 ENTITIES PERSISTED)");
        log.info("================================================================================");
        log.info("Total Teams in 'teams' Table                   : {}", totalTeams);
        log.info("Total Squad Members in 'squad_members' Table   : {}", totalSquadMembers);
        log.info("Team Status Breakdown:");
        log.info("  ├── Pending Teams                             : {}", pendingCount);
        log.info("  ├── Invited Teams                             : {}", invitedCount);
        log.info("  └── Accepted (Claimed) Teams                  : {}", acceptedCount);
        log.info("================================================================================");

        assertTrue(totalTeams != null && totalTeams >= 3750,
                "Expected at least 3,750 teams persisted in PostgreSQL database!");
        assertTrue(acceptedCount != null && acceptedCount >= 1250,
                "Expected at least 1,250 teams claimed by captains in PostgreSQL database!");
        assertTrue(totalSquadMembers != null && totalSquadMembers >= 1250,
                "Expected at least 1,250 captain squad members in PostgreSQL database!");
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
        double avg = latencies.stream().mapToLong(Long::longValue).average().orElse(0.0);

        log.info("================================================================================");
        log.info("MODULE 5 CONCURRENCY BENCHMARK RESULTS (5,000 CONCURRENT VIRTUAL THREADS)");
        log.info("================================================================================");
        log.info("Total Concurrent Operations : {}", totalRequests);
        log.info("Successful Operations        : {}", successCount);
        log.info("Failed Operations            : {}", failureCount);
        log.info("Success Rate                 : {}%", String.format("%.2f", (successCount * 100.0) / totalRequests));
        log.info("Total Elapsed Time           : {} ms ({:.2f} s)", totalDurationMs, totalDurationMs / 1000.0);
        log.info("Throughput (Throughput RPS)  : {} req/sec", String.format("%.2f", throughputRps));
        log.info("Latency Metrics (Wall Clock):");
        log.info("  ├── Min Latency            : {} ms", min);
        log.info("  ├── Average Latency        : {} ms", String.format("%.2f", avg));
        log.info("  ├── Median (p50) Latency   : {} ms", p50);
        log.info("  ├── 90th Percentile (p90)  : {} ms", p90);
        log.info("  ├── 95th Percentile (p95)  : {} ms", p95);
        log.info("  ├── 99th Percentile (p99)  : {} ms", p99);
        log.info("  └── Max Latency            : {} ms", max);
        log.info("================================================================================");
    }
}
