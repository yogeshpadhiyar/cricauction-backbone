package com.cricauction.modules.team.controller;

import com.cricauction.modules.auth.entity.enums.UserRole;
import com.cricauction.modules.team.dto.request.*;
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

@Slf4j
@SpringBootTest
@ActiveProfiles("postgres")
public class TeamIndividualApiLatencyBenchmarkTest {

    @Autowired
    private TeamService teamService;

    @Autowired
    private TournamentService tournamentService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public record LatencyStats(
            String apiId,
            String endpointName,
            String httpMethodAndPath,
            int totalSamples,
            double minMs,
            double avgMs,
            double p50Ms,
            double p90Ms,
            double p95Ms,
            double p99Ms,
            double maxMs,
            double throughputRps
    ) {}

    @Test
    @DisplayName("BENCHMARK: Individual Latency Breakdown for Each Module 5 Team API")
    void benchmarkEachTeamApiSeparately() throws Exception {
        log.info("================================================================================");
        log.info("STARTING INDIVIDUAL API LATENCY PROFILING (MODULE 5: TEAM & CAPTAIN MANAGEMENT)");
        log.info("Target DB: jdbc:postgresql://localhost:5432/cricauction");
        log.info("================================================================================");

        // Fetch user records
        List<Map<String, Object>> userRows = jdbcTemplate.queryForList(
                "SELECT id, full_name, email, mobile FROM users ORDER BY id ASC LIMIT 1000"
        );
        Long organizerId = ((Number) userRows.get(0).get("id")).longValue();
        String organizerEmail = (String) userRows.get(0).get("email");
        UserPrincipal organizer = buildOrganizerPrincipal(organizerId, organizerEmail);

        // Host tournament setup
        CreateTournamentRequest tourReq = CreateTournamentRequest.builder()
                .name("Individual API Latency Arena " + System.currentTimeMillis())
                .location("Wankhede Stadium, Mumbai")
                .ground("Main Ground")
                .startDate(LocalDate.now().plusDays(10))
                .endDate(LocalDate.now().plusDays(25))
                .auctionDate(LocalDate.now().plusDays(5))
                .auctionTime("18:00")
                .ballType("Leather")
                .format("T20")
                .build();
        CreateTournamentResponse hostTour = tournamentService.createTournament(organizer, tourReq);
        String tournamentId = hostTour.getId();

        tournamentService.saveSettings(organizer, tournamentId, TournamentSettingsRequest.builder()
                .teams(5000)
                .budget(100_000_000L)
                .captainRegistrationMode("BOTH")
                .captainApprovalMode("ORGANIZER_APPROVAL")
                .build());

        List<LatencyStats> baselineResults = new ArrayList<>();
        List<LatencyStats> concurrentResults = new ArrayList<>();

        // -----------------------------------------------------------------------------------------
        // 1. API 27: POST /api/v1/tournaments/{id}/teams (Add Team)
        // -----------------------------------------------------------------------------------------
        log.info("Benchmarking API 27: Add New Team...");
        baselineResults.add(measureBaseline("API 27", "Add New Team", "POST /tournaments/{id}/teams", 100, i -> {
            AddTeamRequest req = AddTeamRequest.builder()
                    .name("Base Team " + UUID.randomUUID().toString().substring(0, 8))
                    .captainName("Base Cap " + i)
                    .captainMobile("+9194000" + String.format("%05d", i % 100000))
                    .build();
            teamService.addTeam(organizer, tournamentId, req);
        }));

        concurrentResults.add(measureConcurrent("API 27", "Add New Team", "POST /tournaments/{id}/teams", 200, 25, i -> {
            AddTeamRequest req = AddTeamRequest.builder()
                    .name("Conc Team " + UUID.randomUUID().toString().substring(0, 8))
                    .captainName("Conc Cap " + i)
                    .captainMobile("+9194100" + String.format("%05d", i % 100000))
                    .build();
            teamService.addTeam(organizer, tournamentId, req);
        }));

        // -----------------------------------------------------------------------------------------
        // 2. API 26: GET /api/v1/tournaments/{id}/teams (List Tournament Teams)
        // -----------------------------------------------------------------------------------------
        log.info("Benchmarking API 26: List Tournament Teams...");
        baselineResults.add(measureBaseline("API 26", "List Teams", "GET /tournaments/{id}/teams", 100, i -> {
            teamService.getTournamentTeams(organizer, tournamentId);
        }));

        concurrentResults.add(measureConcurrent("API 26", "List Teams", "GET /tournaments/{id}/teams", 200, 25, i -> {
            teamService.getTournamentTeams(organizer, tournamentId);
        }));

        // Pre-create 300 teams for update, delete, invite, and squad benchmarks
        List<Long> fixtureTeamIds = new ArrayList<>();
        List<String> fixtureInviteCodes = new ArrayList<>();
        for (int i = 0; i < 300; i++) {
            AddTeamRequest req = AddTeamRequest.builder()
                    .name("Fixture Team " + i)
                    .captainName("Fixture Cap " + i)
                    .captainMobile("+9195000" + String.format("%05d", i))
                    .build();
            AddTeamResponse res = teamService.addTeam(organizer, tournamentId, req);
            fixtureTeamIds.add(res.getId());
            fixtureInviteCodes.add(res.getInviteCode());
        }

        // -----------------------------------------------------------------------------------------
        // 3. API 28: PUT /api/v1/tournaments/{id}/teams/{teamId} (Update Team Details)
        // -----------------------------------------------------------------------------------------
        log.info("Benchmarking API 28: Update Team Details...");
        baselineResults.add(measureBaseline("API 28", "Update Team Details", "PUT /tournaments/{id}/teams/{teamId}", 100, i -> {
            Long teamId = fixtureTeamIds.get(i % 50);
            UpdateTeamRequest req = UpdateTeamRequest.builder()
                    .name("Updated Team " + i)
                    .captainName("Updated Cap " + i)
                    .shortCode("UP" + (i % 99))
                    .build();
            teamService.updateTeam(organizer, tournamentId, teamId, req);
        }));

        concurrentResults.add(measureConcurrent("API 28", "Update Team Details", "PUT /tournaments/{id}/teams/{teamId}", 200, 25, i -> {
            Long teamId = fixtureTeamIds.get(i % 50);
            UpdateTeamRequest req = UpdateTeamRequest.builder()
                    .name("Conc Upd " + i)
                    .captainName("Conc Cap " + i)
                    .shortCode("CU" + (i % 99))
                    .build();
            teamService.updateTeam(organizer, tournamentId, teamId, req);
        }));

        // -----------------------------------------------------------------------------------------
        // 4. API 30: POST /api/v1/tournaments/{id}/teams/{teamId}/invite (Send Invite)
        // -----------------------------------------------------------------------------------------
        log.info("Benchmarking API 30: Send Captain Invite...");
        baselineResults.add(measureBaseline("API 30", "Send Captain Invite", "POST /tournaments/{id}/teams/{teamId}/invite", 100, i -> {
            Long teamId = fixtureTeamIds.get(50 + (i % 50));
            teamService.sendCaptainInvite(organizer, tournamentId, teamId,
                    InviteCaptainRequest.builder().channel("whatsapp").build());
        }));

        concurrentResults.add(measureConcurrent("API 30", "Send Captain Invite", "POST /tournaments/{id}/teams/{teamId}/invite", 200, 25, i -> {
            Long teamId = fixtureTeamIds.get(50 + (i % 50));
            teamService.sendCaptainInvite(organizer, tournamentId, teamId,
                    InviteCaptainRequest.builder().channel("sms").build());
        }));

        // -----------------------------------------------------------------------------------------
        // 5. API 31: POST /api/v1/teams/join (Captain Claims Invite)
        // -----------------------------------------------------------------------------------------
        log.info("Benchmarking API 31: Captain Accepts Invite...");
        // Pre-create distinct teams for API 31 claims
        List<String> claimCodes = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            AddTeamResponse tr = teamService.addTeam(organizer, tournamentId, AddTeamRequest.builder()
                    .name("Claimable Team " + i).build());
            teamService.sendCaptainInvite(organizer, tournamentId, tr.getId(),
                    InviteCaptainRequest.builder().channel("whatsapp").build());
            claimCodes.add(tr.getInviteCode());
        }

        baselineResults.add(measureBaseline("API 31", "Accept Captain Invite", "POST /teams/join", 50, i -> {
            Long userId = ((Number) userRows.get((100 + i) % userRows.size()).get("id")).longValue();
            String email = (String) userRows.get((100 + i) % userRows.size()).get("email");
            UserPrincipal player = buildPlayerPrincipal(userId, email);
            teamService.joinTeam(player, JoinTeamRequest.builder().inviteCode(claimCodes.get(i)).build());
        }));

        concurrentResults.add(measureConcurrent("API 31", "Accept Captain Invite", "POST /teams/join", 100, 20, i -> {
            Long userId = ((Number) userRows.get((200 + i) % userRows.size()).get("id")).longValue();
            String email = (String) userRows.get((200 + i) % userRows.size()).get("email");
            UserPrincipal player = buildPlayerPrincipal(userId, email);
            teamService.joinTeam(player, JoinTeamRequest.builder().inviteCode(claimCodes.get(50 + i)).build());
        }));

        // -----------------------------------------------------------------------------------------
        // 6. API 31b: POST /api/v1/tournaments/{id}/teams/register-captain (Self Registration)
        // -----------------------------------------------------------------------------------------
        log.info("Benchmarking API 31b: Captain Self-Registration...");
        baselineResults.add(measureBaseline("API 31b", "Captain Self-Registration", "POST /tournaments/{id}/teams/register-captain", 50, i -> {
            Long userId = ((Number) userRows.get((400 + i) % userRows.size()).get("id")).longValue();
            String email = (String) userRows.get((400 + i) % userRows.size()).get("email");
            UserPrincipal player = buildPlayerPrincipal(userId, email);
            teamService.selfRegisterCaptain(player, tournamentId, SelfRegisterCaptainRequest.builder()
                    .teamName("Self Reg Team " + i)
                    .shortCode("SR" + (i % 99))
                    .captainMobile("+9197" + String.format("%06d", i))
                    .build());
        }));

        concurrentResults.add(measureConcurrent("API 31b", "Captain Self-Registration", "POST /tournaments/{id}/teams/register-captain", 100, 20, i -> {
            Long userId = ((Number) userRows.get((500 + i) % userRows.size()).get("id")).longValue();
            String email = (String) userRows.get((500 + i) % userRows.size()).get("email");
            UserPrincipal player = buildPlayerPrincipal(userId, email);
            teamService.selfRegisterCaptain(player, tournamentId, SelfRegisterCaptainRequest.builder()
                    .teamName("Conc Self Team " + i)
                    .shortCode("CS" + (i % 99))
                    .captainMobile("+9198" + String.format("%06d", i))
                    .build());
        }));

        // -----------------------------------------------------------------------------------------
        // 7. API 31c: GET /api/v1/tournaments/{id}/teams/captain-requests (Pending Applications)
        // -----------------------------------------------------------------------------------------
        log.info("Benchmarking API 31c: List Pending Captain Requests...");
        baselineResults.add(measureBaseline("API 31c", "List Pending Captains", "GET /tournaments/{id}/teams/captain-requests", 100, i -> {
            teamService.getPendingCaptainRequests(organizer, tournamentId);
        }));

        concurrentResults.add(measureConcurrent("API 31c", "List Pending Captains", "GET /tournaments/{id}/teams/captain-requests", 200, 25, i -> {
            teamService.getPendingCaptainRequests(organizer, tournamentId);
        }));

        // -----------------------------------------------------------------------------------------
        // 8. API 31d: POST /api/v1/tournaments/{id}/teams/{teamId}/captain-approval (Approval/Rejection)
        // -----------------------------------------------------------------------------------------
        log.info("Benchmarking API 31d: Organizer Captain Approval/Rejection...");
        List<TeamSummaryResponse> pendingRequests = teamService.getPendingCaptainRequests(organizer, tournamentId);
        int pendingSize = pendingRequests.size();

        baselineResults.add(measureBaseline("API 31d", "Review Captain Request", "POST /tournaments/{id}/teams/{teamId}/captain-approval", Math.min(30, pendingSize / 2), i -> {
            Long targetTeamId = pendingRequests.get(i).getId();
            teamService.reviewCaptainApplication(organizer, tournamentId, targetTeamId,
                    CaptainApprovalRequest.builder().action(i % 2 == 0 ? "APPROVED" : "REJECTED").reason("Benchmark review").build());
        }));

        concurrentResults.add(measureConcurrent("API 31d", "Review Captain Request", "POST /tournaments/{id}/teams/{teamId}/captain-approval", Math.min(50, pendingSize / 2), 10, i -> {
            Long targetTeamId = pendingRequests.get(Math.min(30 + i, pendingSize - 1)).getId();
            try {
                teamService.reviewCaptainApplication(organizer, tournamentId, targetTeamId,
                        CaptainApprovalRequest.builder().action("APPROVED").reason("Concurrent review").build());
            } catch (Exception ignored) {}
        }));

        // -----------------------------------------------------------------------------------------
        // 9. API 32: GET /api/v1/teams/{teamId}/squad (Get Squad Roster & Purse)
        // -----------------------------------------------------------------------------------------
        log.info("Benchmarking API 32: Get Team Squad Roster & Purse...");
        Long squadTeamId = fixtureTeamIds.get(0);
        baselineResults.add(measureBaseline("API 32", "Get Team Squad Roster", "GET /teams/{teamId}/squad", 100, i -> {
            teamService.getTeamSquad(organizer, squadTeamId);
        }));

        concurrentResults.add(measureConcurrent("API 32", "Get Team Squad Roster", "GET /teams/{teamId}/squad", 200, 25, i -> {
            teamService.getTeamSquad(organizer, squadTeamId);
        }));

        // -----------------------------------------------------------------------------------------
        // 10. API 33: PUT /api/v1/teams/{teamId}/profile (Update Team Branding)
        // -----------------------------------------------------------------------------------------
        log.info("Benchmarking API 33: Update Team Branding Profile...");
        baselineResults.add(measureBaseline("API 33", "Update Team Branding", "PUT /teams/{teamId}/profile", 100, i -> {
            Long targetTeamId = fixtureTeamIds.get(100 + (i % 20));
            teamService.updateTeamProfile(organizer, targetTeamId,
                    TeamProfileUpdateRequest.builder().name("Brand " + i).shortCode("B" + (i % 99)).build(), null);
        }));

        concurrentResults.add(measureConcurrent("API 33", "Update Team Branding", "PUT /teams/{teamId}/profile", 200, 25, i -> {
            Long targetTeamId = fixtureTeamIds.get(100 + (i % 20));
            teamService.updateTeamProfile(organizer, targetTeamId,
                    TeamProfileUpdateRequest.builder().name("Conc Brand " + i).shortCode("CB" + (i % 99)).build(), null);
        }));

        // -----------------------------------------------------------------------------------------
        // 11. API 29: DELETE /api/v1/tournaments/{id}/teams/{teamId} (Delete Team)
        // -----------------------------------------------------------------------------------------
        log.info("Benchmarking API 29: Delete Team...");
        // Pre-create 150 disposable teams for delete benchmark
        List<Long> deleteTeams = new ArrayList<>();
        for (int i = 0; i < 150; i++) {
            AddTeamResponse tr = teamService.addTeam(organizer, tournamentId,
                    AddTeamRequest.builder().name("Del Target " + i).build());
            deleteTeams.add(tr.getId());
        }

        baselineResults.add(measureBaseline("API 29", "Delete Team", "DELETE /tournaments/{id}/teams/{teamId}", 50, i -> {
            teamService.deleteTeam(organizer, tournamentId, deleteTeams.get(i));
        }));

        concurrentResults.add(measureConcurrent("API 29", "Delete Team", "DELETE /tournaments/{id}/teams/{teamId}", 80, 10, i -> {
            teamService.deleteTeam(organizer, tournamentId, deleteTeams.get(50 + i));
        }));

        // Print final reports
        printReportTable("BASELINE (ISOLATED EXECUTION) LATENCY PER API", baselineResults);
        printReportTable("CONCURRENT EXECUTION LATENCY PER API (POOL CONTENTION)", concurrentResults);
    }

    private LatencyStats measureBaseline(String apiId, String endpointName, String httpMethodAndPath,
                                         int sampleCount, OperationRunner runner) {
        // Warmup 5 executions
        for (int i = 0; i < 5; i++) {
            try { runner.run(i); } catch (Exception ignored) {}
        }

        List<Double> latencies = new ArrayList<>();
        long startTotal = System.nanoTime();

        for (int i = 0; i < sampleCount; i++) {
            long t0 = System.nanoTime();
            try {
                runner.run(i);
                double ms = (System.nanoTime() - t0) / 1_000_000.0;
                latencies.add(ms);
            } catch (Exception ex) {
                log.warn("Baseline sample {} failed for {}: {}", i, apiId, ex.getMessage());
            }
        }

        long totalElapsedNs = System.nanoTime() - startTotal;
        double throughputRps = (latencies.size() / (totalElapsedNs / 1_000_000_000.0));

        return computeStats(apiId, endpointName, httpMethodAndPath, latencies, throughputRps);
    }

    private LatencyStats measureConcurrent(String apiId, String endpointName, String httpMethodAndPath,
                                           int totalRequests, int concurrency, OperationRunner runner) throws InterruptedException {
        CountDownLatch readyLatch = new CountDownLatch(concurrency);
        CountDownLatch startGun = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(totalRequests);

        List<Double> latencies = new CopyOnWriteArrayList<>();
        AtomicInteger opIndex = new AtomicInteger(0);

        long startTotal;
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int c = 0; c < concurrency; c++) {
                executor.submit(() -> {
                    readyLatch.countDown();
                    try {
                        startGun.await();
                        while (true) {
                            int idx = opIndex.getAndIncrement();
                            if (idx >= totalRequests) {
                                break;
                            }
                            long t0 = System.nanoTime();
                            try {
                                runner.run(idx);
                                double ms = (System.nanoTime() - t0) / 1_000_000.0;
                                latencies.add(ms);
                            } catch (Exception ex) {
                                // log.warn("Concurrent op failed: {}", ex.getMessage());
                            } finally {
                                finishLatch.countDown();
                            }
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            }

            readyLatch.await(10, TimeUnit.SECONDS);
            startTotal = System.nanoTime();
            startGun.countDown();
            finishLatch.await(60, TimeUnit.SECONDS);
        }

        long totalElapsedNs = System.nanoTime() - startTotal;
        double throughputRps = (latencies.size() / (totalElapsedNs / 1_000_000_000.0));

        return computeStats(apiId, endpointName, httpMethodAndPath, latencies, throughputRps);
    }

    private LatencyStats computeStats(String apiId, String endpointName, String httpMethodAndPath,
                                      List<Double> latencies, double throughputRps) {
        Collections.sort(latencies);
        int n = latencies.size();
        if (n == 0) {
            return new LatencyStats(apiId, endpointName, httpMethodAndPath, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        }

        double min = latencies.get(0);
        double max = latencies.get(n - 1);
        double avg = latencies.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double p50 = latencies.get((int) (n * 0.50));
        double p90 = latencies.get((int) (n * 0.90));
        double p95 = latencies.get((int) (n * 0.95));
        double p99 = latencies.get((int) (n * 0.99));

        return new LatencyStats(apiId, endpointName, httpMethodAndPath, n, min, avg, p50, p90, p95, p99, max, throughputRps);
    }

    private void printReportTable(String title, List<LatencyStats> statsList) {
        log.info("===============================================================================================================================================");
        log.info("{}", title);
        log.info("===============================================================================================================================================");
        log.info(String.format("%-8s | %-26s | %-42s | %6s | %8s | %8s | %8s | %8s | %8s | %8s | %8s | %10s",
                "API ID", "Name", "HTTP Path", "Count", "Min(ms)", "Avg(ms)", "P50(ms)", "P90(ms)", "P95(ms)", "P99(ms)", "Max(ms)", "Throughput"));
        log.info("-----------------------------------------------------------------------------------------------------------------------------------------------");
        for (LatencyStats s : statsList) {
            log.info(String.format("%-8s | %-26s | %-42s | %6d | %8.2f | %8.2f | %8.2f | %8.2f | %8.2f | %8.2f | %8.2f | %8.1f rps",
                    s.apiId(), s.endpointName(), s.httpMethodAndPath(), s.totalSamples(),
                    s.minMs(), s.avgMs(), s.p50Ms(), s.p90Ms(), s.p95Ms(), s.p99Ms(), s.maxMs(), s.throughputRps()));
        }
        log.info("===============================================================================================================================================");
    }

    @FunctionalInterface
    interface OperationRunner {
        void run(int index) throws Exception;
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
}
