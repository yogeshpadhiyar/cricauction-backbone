package com.cricauction.modules.match.controller;

import com.cricauction.modules.auth.entity.Role;
import com.cricauction.modules.auth.entity.User;
import com.cricauction.modules.auth.entity.enums.AuthProvider;
import com.cricauction.modules.auth.entity.enums.UserRole;
import com.cricauction.modules.auth.repository.RoleRepository;
import com.cricauction.modules.auth.repository.UserRepository;
import com.cricauction.modules.match.dto.CreateMatchFixtureRequest;
import com.cricauction.modules.match.dto.RecordBallRequest;
import com.cricauction.modules.match.entity.Match;
import com.cricauction.modules.match.entity.enums.MatchStatus;
import com.cricauction.modules.match.repository.MatchBallRepository;
import com.cricauction.modules.match.repository.MatchRepository;
import com.cricauction.modules.match.repository.MatchScorecardRepository;
import com.cricauction.modules.match.service.MatchService;
import com.cricauction.modules.team.entity.Team;
import com.cricauction.modules.team.repository.TeamRepository;
import com.cricauction.modules.tournament.dto.request.CreateTournamentRequest;
import com.cricauction.modules.tournament.dto.request.TournamentSettingsRequest;
import com.cricauction.modules.tournament.dto.response.CreateTournamentResponse;
import com.cricauction.modules.tournament.entity.Tournament;
import com.cricauction.modules.tournament.repository.TournamentRepository;
import com.cricauction.modules.tournament.repository.TournamentSettingsRepository;
import com.cricauction.modules.tournament.service.TournamentService;
import com.cricauction.security.JwtTokenProvider;
import com.cricauction.security.UserPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MatchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MatchRepository matchRepository;

    @Autowired
    private MatchScorecardRepository matchScorecardRepository;

    @Autowired
    private MatchBallRepository matchBallRepository;

    @Autowired
    private TournamentRepository tournamentRepository;

    @Autowired
    private TournamentSettingsRepository settingsRepository;

    @Autowired
    private TournamentService tournamentService;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private MatchService matchService;

    private User organizerUser;
    private User otherOrganizerUser;
    private User spectatorUser;

    private String organizerToken;
    private String otherOrganizerToken;
    private String spectatorToken;

    private String tournamentId;
    private Tournament tournamentEntity;
    private Team team1;
    private Team team2;

    @BeforeEach
    void setUp() {
        cleanAll();

        String suffix = UUID.randomUUID().toString().substring(0, 6);

        Role organizerRole = roleRepository.findByName(UserRole.ORGANIZER)
                .orElseGet(() -> roleRepository.save(Role.builder().name(UserRole.ORGANIZER).build()));
        Role playerRole = roleRepository.findByName(UserRole.PLAYER)
                .orElseGet(() -> roleRepository.save(Role.builder().name(UserRole.PLAYER).build()));

        organizerUser = userRepository.save(User.builder()
                .fullName("Organizer " + suffix)
                .email("org_" + suffix + "@cricauction.com")
                .mobile("+9198" + (int) (Math.random() * 89999999 + 10000000))
                .passwordHash("DummyHash123")
                .isActive(true)
                .build());
        organizerUser.addRole(organizerRole);
        organizerUser = userRepository.save(organizerUser);
        organizerToken = jwtTokenProvider.generateAccessToken(organizerUser);

        otherOrganizerUser = userRepository.save(User.builder()
                .fullName("Other Org " + suffix)
                .email("other_org_" + suffix + "@cricauction.com")
                .mobile("+9197" + (int) (Math.random() * 89999999 + 10000000))
                .passwordHash("DummyHash123")
                .isActive(true)
                .build());
        otherOrganizerUser.addRole(organizerRole);
        otherOrganizerUser = userRepository.save(otherOrganizerUser);
        otherOrganizerToken = jwtTokenProvider.generateAccessToken(otherOrganizerUser);

        spectatorUser = userRepository.save(User.builder()
                .fullName("Spectator " + suffix)
                .email("spec_" + suffix + "@cricauction.com")
                .mobile("+9196" + (int) (Math.random() * 89999999 + 10000000))
                .passwordHash("DummyHash123")
                .isActive(true)
                .build());
        spectatorUser.addRole(playerRole);
        spectatorUser = userRepository.save(spectatorUser);
        spectatorToken = jwtTokenProvider.generateAccessToken(spectatorUser);

        // Create Tournament
        CreateTournamentRequest createReq = CreateTournamentRequest.builder()
                .name("Gujarat Cricket League " + suffix)
                .location("Surat")
                .ground("Lalbhai Contractor Stadium")
                .startDate(LocalDate.now().plusDays(5))
                .endDate(LocalDate.now().plusDays(20))
                .ballType("Leather")
                .format("T20")
                .entryFee("Free")
                .build();

        CreateTournamentResponse createRes = tournamentService.createTournament(
                UserPrincipal.create(organizerUser), createReq);
        tournamentId = createRes.getId();

        TournamentSettingsRequest setReq = TournamentSettingsRequest.builder()
                .teams(8)
                .squadMinimum(11)
                .squadMaximum(15)
                .budget(1000000L)
                .basePrice(10000L)
                .bidIncrement(1000L)
                .bidTimer(15)
                .build();
        tournamentService.saveSettings(UserPrincipal.create(organizerUser), tournamentId, setReq);
        tournamentEntity = tournamentRepository.findByPublicId(tournamentId).orElseThrow();

        // Create Teams
        team1 = teamRepository.save(Team.builder()
                .tournament(tournamentEntity)
                .name("Surat Strikers " + suffix)
                .captainName("Rohit Sharma")
                .captainMobile("9800000001")
                .initialBudget(1000000L)
                .remainingBudget(1000000L)
                .status("Approved")
                .inviteCode("SS-" + suffix)
                .build());

        team2 = teamRepository.save(Team.builder()
                .tournament(tournamentEntity)
                .name("City Warriors " + suffix)
                .captainName("Virat Kohli")
                .captainMobile("9800000002")
                .initialBudget(1000000L)
                .remainingBudget(1000000L)
                .status("Approved")
                .inviteCode("CW-" + suffix)
                .build());
    }

    @AfterEach
    void tearDown() {
        cleanAll();
    }

    private void cleanAll() {
        matchBallRepository.deleteAll();
        matchScorecardRepository.deleteAll();
        matchRepository.deleteAll();
        teamRepository.deleteAll();
        settingsRepository.deleteAll();
        tournamentRepository.deleteAll();
    }

    @Test
    @DisplayName("API 54: List Tournament Fixtures (empty initially)")
    void testListFixtures_Empty() throws Exception {
        mockMvc.perform(get("/api/v1/tournaments/" + tournamentId + "/matches")
                        .header("Authorization", "Bearer " + organizerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    @DisplayName("API 55: Organizer creates match fixture successfully")
    void testCreateFixture_Success() throws Exception {
        CreateMatchFixtureRequest request = CreateMatchFixtureRequest.builder()
                .team1Id(team1.getId())
                .team2Id(team2.getId())
                .scheduledAt(Instant.now().plusSeconds(3600))
                .ground("Lalbhai Contractor Stadium")
                .overs(20)
                .build();

        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/matches")
                        .header("Authorization", "Bearer " + organizerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.message", is("Fixture scheduled")))
                .andExpect(jsonPath("$.data.id", notNullValue()))
                .andExpect(jsonPath("$.data.team1", is(team1.getName())))
                .andExpect(jsonPath("$.data.team2", is(team2.getName())))
                .andExpect(jsonPath("$.data.status", is("UPCOMING")));
    }

    @Test
    @DisplayName("API 55: Other organizer cannot schedule fixture on someone else's tournament")
    void testCreateFixture_ForbiddenForOtherOrganizer() throws Exception {
        CreateMatchFixtureRequest request = CreateMatchFixtureRequest.builder()
                .team1Id(team1.getId())
                .team2Id(team2.getId())
                .scheduledAt(Instant.now().plusSeconds(3600))
                .ground("Lalbhai Contractor Stadium")
                .overs(20)
                .build();

        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/matches")
                        .header("Authorization", "Bearer " + otherOrganizerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("API 55: Validation fails when team1 and team2 are identical")
    void testCreateFixture_SameTeamFails() throws Exception {
        CreateMatchFixtureRequest request = CreateMatchFixtureRequest.builder()
                .team1Id(team1.getId())
                .team2Id(team1.getId())
                .scheduledAt(Instant.now().plusSeconds(3600))
                .ground("Lalbhai Contractor Stadium")
                .overs(20)
                .build();

        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/matches")
                        .header("Authorization", "Bearer " + organizerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("API 56: Get Match Scorecard")
    void testGetMatchScorecard_Success() throws Exception {
        CreateMatchFixtureRequest req = CreateMatchFixtureRequest.builder()
                .team1Id(team1.getId())
                .team2Id(team2.getId())
                .scheduledAt(Instant.now().plusSeconds(3600))
                .ground("Lalbhai Contractor Stadium")
                .overs(20)
                .build();

        var fixture = matchService.createFixture(UserPrincipal.create(organizerUser), tournamentId, req);

        mockMvc.perform(get("/api/v1/matches/" + fixture.getId())
                        .header("Authorization", "Bearer " + spectatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.matchId", is(fixture.getId())))
                .andExpect(jsonPath("$.data.team1.name", is(team1.getName())))
                .andExpect(jsonPath("$.data.team1.runs", is(0)))
                .andExpect(jsonPath("$.data.team2.name", is(team2.getName())))
                .andExpect(jsonPath("$.data.team2.runs", is(0)))
                .andExpect(jsonPath("$.data.status", is("UPCOMING")));
    }

    @Test
    @DisplayName("API 57: Live Match Ball-by-Ball Update and Idempotent Retry")
    void testRecordBall_SuccessAndIdempotency() throws Exception {
        CreateMatchFixtureRequest req = CreateMatchFixtureRequest.builder()
                .team1Id(team1.getId())
                .team2Id(team2.getId())
                .scheduledAt(Instant.now().plusSeconds(3600))
                .ground("Lalbhai Contractor Stadium")
                .overs(20)
                .build();

        var fixture = matchService.createFixture(UserPrincipal.create(organizerUser), tournamentId, req);

        // Record ball 1: 4 runs by batsman
        RecordBallRequest ball1 = RecordBallRequest.builder()
                .innings(1)
                .over(0)
                .ball(1)
                .runs(4)
                .isWicket(false)
                .batsmanId("ply_01")
                .batsmanName("Rahul Patel")
                .bowlerId("ply_03")
                .bowlerName("Vivek Joshi")
                .build();

        mockMvc.perform(patch("/api/v1/matches/" + fixture.getId() + "/score")
                        .header("Authorization", "Bearer " + organizerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ball1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.message", is("Ball recorded")))
                .andExpect(jsonPath("$.data.runs", is(4)))
                .andExpect(jsonPath("$.data.totalRuns", is(4)))
                .andExpect(jsonPath("$.data.matchStatus", is("LIVE")));

        // Idempotency: Send the exact same ball again (simulating network duplicate retry)
        mockMvc.perform(patch("/api/v1/matches/" + fixture.getId() + "/score")
                        .header("Authorization", "Bearer " + organizerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ball1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.totalRuns", is(4))); // Total runs NOT doubled!

        // Record ball 2: Wicket
        RecordBallRequest ball2 = RecordBallRequest.builder()
                .innings(1)
                .over(0)
                .ball(2)
                .runs(0)
                .isWicket(true)
                .wicketType("BOWLED")
                .batsmanId("ply_01")
                .batsmanName("Rahul Patel")
                .bowlerId("ply_03")
                .bowlerName("Vivek Joshi")
                .build();

        mockMvc.perform(patch("/api/v1/matches/" + fixture.getId() + "/score")
                        .header("Authorization", "Bearer " + organizerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ball2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalWickets", is(1)));

        // Verify scorecard
        mockMvc.perform(get("/api/v1/matches/" + fixture.getId())
                        .header("Authorization", "Bearer " + spectatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.team1.runs", is(4)))
                .andExpect(jsonPath("$.data.team1.wickets", is(1)))
                .andExpect(jsonPath("$.data.currentBowler.wickets", is(1)));
    }

    @Test
    @DisplayName("API 58: Get Tournament Standings (Points Table)")
    void testGetStandings_AfterMatchCompletion() throws Exception {
        // Create a 1-over quick match
        CreateMatchFixtureRequest req = CreateMatchFixtureRequest.builder()
                .team1Id(team1.getId())
                .team2Id(team2.getId())
                .scheduledAt(Instant.now().plusSeconds(3600))
                .ground("Lalbhai Contractor Stadium")
                .overs(1) // 1 over = 6 balls
                .build();

        var fixture = matchService.createFixture(UserPrincipal.create(organizerUser), tournamentId, req);

        // Innings 1: Team 1 scores 6 runs in 6 balls
        for (int b = 1; b <= 6; b++) {
            RecordBallRequest ball = RecordBallRequest.builder()
                    .innings(1)
                    .over(0)
                    .ball(b)
                    .runs(1)
                    .isWicket(false)
                    .build();
            matchService.recordBall(UserPrincipal.create(organizerUser), fixture.getId(), ball);
        }

        // Innings 2: Team 2 chases target by hitting two 4s (8 runs > 6 runs, wins!)
        RecordBallRequest ballChase1 = RecordBallRequest.builder()
                .innings(2)
                .over(0)
                .ball(1)
                .runs(4)
                .isWicket(false)
                .build();
        matchService.recordBall(UserPrincipal.create(organizerUser), fixture.getId(), ballChase1);

        RecordBallRequest ballChase2 = RecordBallRequest.builder()
                .innings(2)
                .over(0)
                .ball(2)
                .runs(4)
                .isWicket(false)
                .build();
        matchService.recordBall(UserPrincipal.create(organizerUser), fixture.getId(), ballChase2);

        // Verify Standings
        mockMvc.perform(get("/api/v1/tournaments/" + tournamentId + "/standings")
                        .header("Authorization", "Bearer " + spectatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].team", is(team2.getName())))
                .andExpect(jsonPath("$.data[0].won", is(1)))
                .andExpect(jsonPath("$.data[0].points", is(2)))
                .andExpect(jsonPath("$.data[1].team", is(team1.getName())))
                .andExpect(jsonPath("$.data[1].lost", is(1)))
                .andExpect(jsonPath("$.data[1].points", is(0)));
    }

    @Test
    @DisplayName("API 57: New batsman and bowler stats reset cleanly on player change")
    void testRecordBall_NewBatsmanAndBowlerStatsReset() throws Exception {
        CreateMatchFixtureRequest req = CreateMatchFixtureRequest.builder()
                .team1Id(team1.getId())
                .team2Id(team2.getId())
                .scheduledAt(Instant.now().plusSeconds(3600))
                .ground("Lalbhai Contractor Stadium")
                .overs(20)
                .build();
        var fixture = matchService.createFixture(UserPrincipal.create(organizerUser), tournamentId, req);

        // Ball 1: Batsman 1 scores 4 runs off Bowler 1
        RecordBallRequest b1 = RecordBallRequest.builder()
                .innings(1).over(0).ball(1).runs(4).isWicket(false)
                .batsmanId("ply_01").batsmanName("Opener 1")
                .bowlerId("ply_10").bowlerName("Pacer 1")
                .build();
        matchService.recordBall(UserPrincipal.create(organizerUser), fixture.getId(), b1);

        // Ball 2: Opener 1 dismissed by Pacer 1
        RecordBallRequest b2 = RecordBallRequest.builder()
                .innings(1).over(0).ball(2).runs(0).isWicket(true).wicketType("CAUGHT")
                .batsmanId("ply_01").batsmanName("Opener 1")
                .bowlerId("ply_10").bowlerName("Pacer 1")
                .build();
        matchService.recordBall(UserPrincipal.create(organizerUser), fixture.getId(), b2);

        // Ball 3: New Batsman "Number 3" comes in and scores 1 run
        RecordBallRequest b3 = RecordBallRequest.builder()
                .innings(1).over(0).ball(3).runs(1).isWicket(false)
                .batsmanId("ply_02").batsmanName("Number 3")
                .bowlerId("ply_10").bowlerName("Pacer 1")
                .build();
        matchService.recordBall(UserPrincipal.create(organizerUser), fixture.getId(), b3);

        // Verify scorecard: Number 3 has 1 run and 1 ball, NOT 5 runs from Opener 1
        var scorecard = matchService.getMatchScorecard(UserPrincipal.create(spectatorUser), fixture.getId());
        assertEquals("Number 3", scorecard.getCurrentBatsmen().get(0).getName());
        assertEquals(1, scorecard.getCurrentBatsmen().get(0).getRuns());
        assertEquals(1, scorecard.getCurrentBatsmen().get(0).getBalls());

        // Over 1: New Bowler "Spinner 2" starts next over
        RecordBallRequest b4 = RecordBallRequest.builder()
                .innings(1).over(1).ball(1).runs(0).isWicket(false)
                .batsmanId("ply_02").batsmanName("Number 3")
                .bowlerId("ply_11").bowlerName("Spinner 2")
                .build();
        matchService.recordBall(UserPrincipal.create(organizerUser), fixture.getId(), b4);

        scorecard = matchService.getMatchScorecard(UserPrincipal.create(spectatorUser), fixture.getId());
        assertEquals("Spinner 2", scorecard.getCurrentBowler().getName());
        assertEquals(0, scorecard.getCurrentBowler().getWickets()); // Did NOT inherit Pacer 1's wicket!
    }

    @Test
    @DisplayName("API 57: Validation rejects negative extra runs")
    void testRecordBall_NegativeExtraRunsRejected() throws Exception {
        CreateMatchFixtureRequest req = CreateMatchFixtureRequest.builder()
                .team1Id(team1.getId())
                .team2Id(team2.getId())
                .scheduledAt(Instant.now().plusSeconds(3600))
                .ground("Lalbhai Contractor Stadium")
                .overs(20)
                .build();
        var fixture = matchService.createFixture(UserPrincipal.create(organizerUser), tournamentId, req);

        RecordBallRequest invalidBall = RecordBallRequest.builder()
                .innings(1).over(0).ball(1).runs(0).extraRuns(-5)
                .build();

        mockMvc.perform(patch("/api/v1/matches/" + fixture.getId() + "/score")
                        .header("Authorization", "Bearer " + organizerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidBall)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("API 55: Validation rejects overs exceeding maximum 100")
    void testCreateFixture_ExceedMaxOversRejected() throws Exception {
        CreateMatchFixtureRequest req = CreateMatchFixtureRequest.builder()
                .team1Id(team1.getId())
                .team2Id(team2.getId())
                .scheduledAt(Instant.now().plusSeconds(3600))
                .ground("Lalbhai Contractor Stadium")
                .overs(150)
                .build();

        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/matches")
                        .header("Authorization", "Bearer " + organizerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("SEC-04: Path variable validation rejects overly long match ID (> 100 chars)")
    void testMatch_OverlyLongMatchId_Rejected() throws Exception {
        String longMatchId = "m".repeat(101);

        mockMvc.perform(get("/api/v1/matches/" + longMatchId)
                        .header("Authorization", "Bearer " + organizerToken))
                .andExpect(status().isBadRequest());
    }
}
