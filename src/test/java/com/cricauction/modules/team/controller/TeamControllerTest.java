package com.cricauction.modules.team.controller;

import com.cricauction.modules.auth.entity.Role;
import com.cricauction.modules.auth.entity.User;
import com.cricauction.modules.auth.entity.enums.UserRole;
import com.cricauction.modules.auth.repository.RoleRepository;
import com.cricauction.modules.auth.repository.UserRepository;
import com.cricauction.modules.team.dto.request.*;
import com.cricauction.modules.team.entity.SquadMember;
import com.cricauction.modules.team.entity.Team;
import com.cricauction.modules.team.repository.SquadMemberRepository;
import com.cricauction.modules.team.repository.TeamRepository;
import com.cricauction.modules.tournament.dto.request.CreateTournamentRequest;
import com.cricauction.modules.tournament.dto.request.TournamentSettingsRequest;
import com.cricauction.modules.tournament.entity.Tournament;
import com.cricauction.modules.tournament.repository.TournamentRepository;
import com.cricauction.modules.tournament.repository.TournamentSettingsRepository;
import com.cricauction.modules.tournament.service.TournamentService;
import com.cricauction.security.JwtTokenProvider;
import com.cricauction.security.UserPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class TeamControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private TournamentRepository tournamentRepository;

    @Autowired
    private TournamentSettingsRepository settingsRepository;

    @Autowired
    private TournamentService tournamentService;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private SquadMemberRepository squadMemberRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private User organizerUser;
    private User otherOrganizerUser;
    private User playerUser;
    private User anotherPlayerUser;

    private String organizerToken;
    private String otherOrganizerToken;
    private String playerToken;
    private String anotherPlayerToken;

    private String tournamentId;
    private Tournament tournamentEntity;

    @BeforeEach
    void setUp() {
        squadMemberRepository.deleteAll();
        teamRepository.deleteAll();
        settingsRepository.deleteAll();
        tournamentRepository.deleteAll();

        String suffix = UUID.randomUUID().toString().substring(0, 6);

        Role organizerRole = roleRepository.findByName(UserRole.ORGANIZER)
                .orElseGet(() -> roleRepository.save(Role.builder().name(UserRole.ORGANIZER).build()));
        Role playerRole = roleRepository.findByName(UserRole.PLAYER)
                .orElseGet(() -> roleRepository.save(Role.builder().name(UserRole.PLAYER).build()));
        roleRepository.findByName(UserRole.CAPTAIN)
                .orElseGet(() -> roleRepository.save(Role.builder().name(UserRole.CAPTAIN).build()));

        organizerUser = User.builder()
                .fullName("Rohit Sharma")
                .email("organizer.rohit." + suffix + "@cricauction.app")
                .mobile("+9198" + (int) (Math.random() * 89999999 + 10000000))
                .passwordHash("DummyHash123")
                .isActive(true)
                .build();
        organizerUser.addRole(organizerRole);
        organizerUser = userRepository.save(organizerUser);
        organizerToken = jwtTokenProvider.generateAccessToken(organizerUser);

        otherOrganizerUser = User.builder()
                .fullName("Sourav Ganguly")
                .email("organizer.dada." + suffix + "@cricauction.app")
                .mobile("+9197" + (int) (Math.random() * 89999999 + 10000000))
                .passwordHash("DummyHash123")
                .isActive(true)
                .build();
        otherOrganizerUser.addRole(organizerRole);
        otherOrganizerUser = userRepository.save(otherOrganizerUser);
        otherOrganizerToken = jwtTokenProvider.generateAccessToken(otherOrganizerUser);

        playerUser = User.builder()
                .fullName("Hardik Pandya")
                .email("hardik." + suffix + "@cricauction.app")
                .mobile("+9196" + (int) (Math.random() * 89999999 + 10000000))
                .passwordHash("DummyHash123")
                .isActive(true)
                .build();
        playerUser.addRole(playerRole);
        playerUser = userRepository.save(playerUser);
        playerToken = jwtTokenProvider.generateAccessToken(playerUser);

        anotherPlayerUser = User.builder()
                .fullName("Jasprit Bumrah")
                .email("bumrah." + suffix + "@cricauction.app")
                .mobile("+9195" + (int) (Math.random() * 89999999 + 10000000))
                .passwordHash("DummyHash123")
                .isActive(true)
                .build();
        anotherPlayerUser.addRole(playerRole);
        anotherPlayerUser = userRepository.save(anotherPlayerUser);
        anotherPlayerToken = jwtTokenProvider.generateAccessToken(anotherPlayerUser);

        // Pre-create tournament with settings (teams = 2, budget = 100,000,000)
        UserPrincipal organizerPrincipal = UserPrincipal.create(organizerUser);
        CreateTournamentRequest tourReq = CreateTournamentRequest.builder()
                .name("Mumbai Premier League " + suffix)
                .location("Wankhede Stadium, Mumbai")
                .ground("Wankhede")
                .startDate(LocalDate.now().plusDays(10))
                .endDate(LocalDate.now().plusDays(20))
                .auctionDate(LocalDate.now().plusDays(5))
                .auctionTime("18:00")
                .ballType("Leather")
                .format("T20")
                .build();

        var tourRes = tournamentService.createTournament(organizerPrincipal, tourReq);
        tournamentId = tourRes.getId();

        TournamentSettingsRequest setReq = TournamentSettingsRequest.builder()
                .teams(2)
                .squadMinimum(11)
                .squadMaximum(15)
                .budget(100_000_000L)
                .basePrice(2_000_000L)
                .bidIncrement(500_000L)
                .bidTimer(30)
                .build();
        tournamentService.saveSettings(organizerPrincipal, tournamentId, setReq);

        tournamentEntity = tournamentRepository.findByPublicId(tournamentId).orElseThrow();
    }

    @Test
    @DisplayName("API 27: POST /api/v1/tournaments/{id}/teams - Add Team Successfully")
    void testAddTeamSuccess() throws Exception {
        AddTeamRequest req = AddTeamRequest.builder()
                .name("Mumbai Paltan")
                .captainName("Rohit Sharma")
                .captainMobile("+919876543210")
                .build();

        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/teams")
                        .header("Authorization", "Bearer " + organizerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.id", notNullValue()))
                .andExpect(jsonPath("$.data.name", is("Mumbai Paltan")))
                .andExpect(jsonPath("$.data.status", is("Pending")))
                .andExpect(jsonPath("$.data.inviteCode", notNullValue()))
                .andExpect(jsonPath("$.data.remainingBudget", is(100_000_000)));
    }

    @Test
    @DisplayName("API 27 Capacity Limit: Cannot add more teams than totalTeams configured")
    void testAddTeamCapacityExceeded() throws Exception {
        // Add Team 1
        AddTeamRequest req1 = AddTeamRequest.builder()
                .name("Team One")
                .build();
        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/teams")
                        .header("Authorization", "Bearer " + organizerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req1)))
                .andExpect(status().isCreated());

        // Add Team 2 (reaches totalTeams limit of 2)
        AddTeamRequest req2 = AddTeamRequest.builder()
                .name("Team Two")
                .build();
        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/teams")
                        .header("Authorization", "Bearer " + organizerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req2)))
                .andExpect(status().isCreated());

        // Attempt Team 3 (should fail with 400 Bad Request)
        AddTeamRequest req3 = AddTeamRequest.builder()
                .name("Team Three")
                .build();
        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/teams")
                        .header("Authorization", "Bearer " + organizerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req3)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.error.code", is("TEAM_CAPACITY_REACHED")));
    }

    @Test
    @DisplayName("API 27 Security: Non-organizer or different organizer cannot add team")
    void testAddTeamForbiddenForOtherUser() throws Exception {
        AddTeamRequest req = AddTeamRequest.builder()
                .name("Unauthorized Super Kings")
                .build();

        // Other organizer should get 403 Forbidden
        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/teams")
                        .header("Authorization", "Bearer " + otherOrganizerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());

        // Regular player should get 403 Forbidden
        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/teams")
                        .header("Authorization", "Bearer " + playerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("API 26: GET /api/v1/tournaments/{id}/teams - List Tournament Teams")
    void testListTournamentTeams() throws Exception {
        // Add a team first
        AddTeamRequest req = AddTeamRequest.builder()
                .name("Chennai Warriors")
                .captainName("MS Dhoni")
                .captainMobile("+919988776655")
                .build();

        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/teams")
                        .header("Authorization", "Bearer " + organizerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

        // List teams
        mockMvc.perform(get("/api/v1/tournaments/" + tournamentId + "/teams")
                        .header("Authorization", "Bearer " + organizerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.totalTeams", is(2)))
                .andExpect(jsonPath("$.data.assignedCaptains", is(0)))
                .andExpect(jsonPath("$.data.teams", hasSize(1)))
                .andExpect(jsonPath("$.data.teams[0].name", is("Chennai Warriors")))
                .andExpect(jsonPath("$.data.teams[0].status", is("Pending")))
                .andExpect(jsonPath("$.data.teams[0].squadCount", is(0)));
    }

    @Test
    @DisplayName("API 28: PUT /api/v1/tournaments/{id}/teams/{teamId} - Edit Team Details")
    void testUpdateTeamDetails() throws Exception {
        // Add team
        Team team = Team.builder()
                .tournament(tournamentEntity)
                .name("Gujarat Lions")
                .captainName("Suresh Raina")
                .captainMobile("+919123456789")
                .initialBudget(100_000_000L)
                .remainingBudget(100_000_000L)
                .status("Invited")
                .inviteCode("GL-INV-1")
                .build();
        team = teamRepository.save(team);

        UpdateTeamRequest updateReq = UpdateTeamRequest.builder()
                .name("Gujarat Titans")
                .captainName("Shubman Gill")
                .captainMobile("+919876500000")
                .build();

        mockMvc.perform(put("/api/v1/tournaments/" + tournamentId + "/teams/" + team.getId())
                        .header("Authorization", "Bearer " + organizerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.id", is(team.getId().intValue())))
                .andExpect(jsonPath("$.data.name", is("Gujarat Titans")))
                .andExpect(jsonPath("$.data.captainName", is("Shubman Gill")));
    }

    @Test
    @DisplayName("API 29: DELETE /api/v1/tournaments/{id}/teams/{teamId} - Delete Team")
    void testDeleteTeam() throws Exception {
        Team team = Team.builder()
                .tournament(tournamentEntity)
                .name("Deccan Chargers")
                .initialBudget(100_000_000L)
                .remainingBudget(100_000_000L)
                .status("Invited")
                .inviteCode("DC-INV-1")
                .build();
        team = teamRepository.save(team);

        mockMvc.perform(delete("/api/v1/tournaments/" + tournamentId + "/teams/" + team.getId())
                        .header("Authorization", "Bearer " + organizerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.message", containsString("removed")));

        assertTrue(teamRepository.findById(team.getId()).isEmpty());
    }

    @Test
    @DisplayName("API 30: POST /api/v1/tournaments/{id}/teams/{teamId}/invite - Send Captain Invite")
    void testSendCaptainInvite() throws Exception {
        Team team = Team.builder()
                .tournament(tournamentEntity)
                .name("Delhi Capitals")
                .captainName("Rishabh Pant")
                .captainMobile("+919000011111")
                .initialBudget(100_000_000L)
                .remainingBudget(100_000_000L)
                .status("Invited")
                .inviteCode("DC-INV-99")
                .build();
        team = teamRepository.save(team);

        InviteCaptainRequest req = InviteCaptainRequest.builder()
                .channel("whatsapp")
                .build();

        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/teams/" + team.getId() + "/invite")
                        .header("Authorization", "Bearer " + organizerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.teamId", is(team.getId().intValue())))
                .andExpect(jsonPath("$.data.status", is("Invited")))
                .andExpect(jsonPath("$.data.inviteLink", notNullValue()));
    }

    @Test
    @DisplayName("API 31: POST /api/v1/teams/join - Captain Claims Team Invite & Grants ROLE_CAPTAIN")
    void testCaptainAcceptsInvite() throws Exception {
        Team team = Team.builder()
                .tournament(tournamentEntity)
                .name("Kolkata Knight Riders")
                .captainName("Shreyas Iyer")
                .initialBudget(100_000_000L)
                .remainingBudget(100_000_000L)
                .status("Invited")
                .inviteCode("KKR-CLAIM-CODE")
                .build();
        team = teamRepository.save(team);

        JoinTeamRequest joinReq = JoinTeamRequest.builder()
                .inviteCode("KKR-CLAIM-CODE")
                .build();

        // Player accepts invite
        mockMvc.perform(post("/api/v1/teams/join")
                        .header("Authorization", "Bearer " + playerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(joinReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.teamId", is(team.getId().intValue())))
                .andExpect(jsonPath("$.data.teamName", is("Kolkata Knight Riders")))
                .andExpect(jsonPath("$.data.tournamentId", is(tournamentId)))
                .andExpect(jsonPath("$.data.role", is("captain")));

        // Verify team DB state
        Team updatedTeam = teamRepository.findById(team.getId()).orElseThrow();
        assertEquals("Accepted", updatedTeam.getStatus());
        assertEquals(playerUser.getId(), updatedTeam.getCaptainId());

        // Verify user has ROLE_CAPTAIN
        User refreshedUser = userRepository.findById(playerUser.getId()).orElseThrow();
        boolean hasCaptainRole = refreshedUser.getRoles().stream()
                .anyMatch(r -> r.getName() == UserRole.CAPTAIN);
        assertTrue(hasCaptainRole, "User must be granted ROLE_CAPTAIN upon accepting team invite");

        // Verify captain is added to squad_members
        var squad = squadMemberRepository.findByTeamIdOrderByIdAsc(team.getId());
        assertEquals(1, squad.size());
        SquadMember captainMember = squad.get(0);
        assertTrue(captainMember.isCaptain());
        assertEquals(playerUser.getId(), captainMember.getPlayerUserId());
        assertEquals(0L, captainMember.getPurchasePrice());

        // Verify conflict: another player cannot claim the already accepted team
        mockMvc.perform(post("/api/v1/teams/join")
                        .header("Authorization", "Bearer " + anotherPlayerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(joinReq)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.error.code", is("TEAM_ALREADY_CLAIMED")));
    }

    @Test
    @DisplayName("API 32: GET /api/v1/teams/{teamId}/squad - View Squad Roster and Purse")
    void testGetTeamSquad() throws Exception {
        Team team = Team.builder()
                .tournament(tournamentEntity)
                .name("Rajasthan Royals")
                .captainName("Sanju Samson")
                .captainId(playerUser.getId())
                .initialBudget(100_000_000L)
                .remainingBudget(85_000_000L)
                .status("Accepted")
                .inviteCode("RR-INV-1")
                .build();
        team = teamRepository.save(team);

        // Add Captain squad member
        SquadMember captain = SquadMember.builder()
                .team(team)
                .playerUserId(playerUser.getId())
                .playerName("Sanju Samson")
                .playingRole("Wicketkeeper Batsman")
                .purchasePrice(0L)
                .isCaptain(true)
                .build();
        squadMemberRepository.save(captain);

        // Add an auctioned player
        SquadMember boughtPlayer = SquadMember.builder()
                .team(team)
                .playerUserId(anotherPlayerUser.getId())
                .playerName("Jasprit Bumrah")
                .playingRole("Fast Bowler")
                .purchasePrice(15_000_000L)
                .isCaptain(false)
                .build();
        squadMemberRepository.save(boughtPlayer);

        mockMvc.perform(get("/api/v1/teams/" + team.getId() + "/squad")
                        .header("Authorization", "Bearer " + playerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.teamId", is(team.getId().intValue())))
                .andExpect(jsonPath("$.data.teamName", is("Rajasthan Royals")))
                .andExpect(jsonPath("$.data.totalPurse", is(100_000_000)))
                .andExpect(jsonPath("$.data.remainingPurse", is(85_000_000)))
                .andExpect(jsonPath("$.data.spentPurse", is(15_000_000)))
                .andExpect(jsonPath("$.data.squadCount", is(2)))
                .andExpect(jsonPath("$.data.players", hasSize(2)))
                .andExpect(jsonPath("$.data.players[0].name", is("Sanju Samson")))
                .andExpect(jsonPath("$.data.players[0].isCaptain", is(true)))
                .andExpect(jsonPath("$.data.players[1].name", is("Jasprit Bumrah")))
                .andExpect(jsonPath("$.data.players[1].purchasePrice", is(15_000_000)));
    }

    @Test
    @DisplayName("API 33: PUT /api/v1/teams/{teamId}/profile - Captain Updates Team Branding")
    void testCaptainUpdatesTeamBranding() throws Exception {
        Team team = Team.builder()
                .tournament(tournamentEntity)
                .name("Royal Challengers")
                .shortCode("RC")
                .captainId(playerUser.getId())
                .initialBudget(100_000_000L)
                .remainingBudget(100_000_000L)
                .status("Accepted")
                .inviteCode("RCB-INV-1")
                .build();
        team = teamRepository.save(team);

        TeamProfileUpdateRequest updateReq = TeamProfileUpdateRequest.builder()
                .name("Royal Challengers Bengaluru")
                .shortCode("RCB")
                .build();

        // Captain updates team branding
        mockMvc.perform(put("/api/v1/teams/" + team.getId() + "/profile")
                        .header("Authorization", "Bearer " + playerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.name", is("Royal Challengers Bengaluru")))
                .andExpect(jsonPath("$.data.shortCode", is("RCB")));

        // Organizer can also update branding
        TeamProfileUpdateRequest organizerUpdateReq = TeamProfileUpdateRequest.builder()
                .name("RCB Champions")
                .shortCode("RCBC")
                .build();

        mockMvc.perform(put("/api/v1/teams/" + team.getId() + "/profile")
                        .header("Authorization", "Bearer " + organizerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(organizerUpdateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name", is("RCB Champions")));

        // Unrelated user cannot update branding (403 Forbidden)
        mockMvc.perform(put("/api/v1/teams/" + team.getId() + "/profile")
                        .header("Authorization", "Bearer " + anotherPlayerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("API 31b: POST /api/v1/tournaments/{id}/teams/register-captain - Open Self-Registration (AUTO Approval Mode)")
    void testCaptainSelfRegistrationWhenAllowed() throws Exception {
        // Configure tournament with captainRegistrationMode = BOTH and captainApprovalMode = AUTO
        TournamentSettingsRequest setReq = TournamentSettingsRequest.builder()
                .teams(4)
                .squadMinimum(11)
                .squadMaximum(15)
                .budget(100_000_000L)
                .captainRegistrationMode("BOTH")
                .captainApprovalMode("AUTO")
                .build();
        tournamentService.saveSettings(UserPrincipal.create(organizerUser), tournamentId, setReq);

        SelfRegisterCaptainRequest req = SelfRegisterCaptainRequest.builder()
                .teamName("Western Warriors")
                .shortCode("WW")
                .captainMobile("+919611223344")
                .build();

        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/teams/register-captain")
                        .header("Authorization", "Bearer " + playerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.teamName", is("Western Warriors")))
                .andExpect(jsonPath("$.data.role", is("captain")))
                .andExpect(jsonPath("$.data.status", is("Accepted")))
                .andExpect(jsonPath("$.data.tournamentId", is(tournamentId)));

        // Verify claiming existing unassigned team in BOTH mode
        Team unassignedTeam = Team.builder()
                .tournament(tournamentEntity)
                .name("Southern Stars")
                .shortCode("SS")
                .initialBudget(100_000_000L)
                .remainingBudget(100_000_000L)
                .status("Pending")
                .inviteCode("SS-OPEN-1")
                .build();
        unassignedTeam = teamRepository.save(unassignedTeam);

        SelfRegisterCaptainRequest claimReq = SelfRegisterCaptainRequest.builder()
                .teamId(unassignedTeam.getId())
                .build();

        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/teams/register-captain")
                        .header("Authorization", "Bearer " + anotherPlayerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(claimReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.teamId", is(unassignedTeam.getId().intValue())))
                .andExpect(jsonPath("$.data.teamName", is("Southern Stars")))
                .andExpect(jsonPath("$.data.status", is("Accepted")));
    }

    @Test
    @DisplayName("API 31b Security: POST /api/v1/tournaments/{id}/teams/register-captain - Forbidden when mode is INVITE_ONLY")
    void testCaptainSelfRegistrationForbiddenWhenInviteOnly() throws Exception {
        // Default tournament settings have captainRegistrationMode = INVITE_ONLY
        SelfRegisterCaptainRequest req = SelfRegisterCaptainRequest.builder()
                .teamName("Blocked Kings")
                .build();

        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/teams/register-captain")
                        .header("Authorization", "Bearer " + playerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.error.code", is("CAPTAIN_INVITE_ONLY")));
    }

    @Test
    @DisplayName("APIs 31b, 31c, 31d: Full Lifecycle of Captain Self-Registration with ORGANIZER_APPROVAL")
    void testOrganizerApprovalWorkflowLifecycle() throws Exception {
        // 1. Organizer configures tournament with BOTH and ORGANIZER_APPROVAL
        TournamentSettingsRequest setReq = TournamentSettingsRequest.builder()
                .teams(4)
                .squadMinimum(11)
                .squadMaximum(15)
                .budget(100_000_000L)
                .captainRegistrationMode("BOTH")
                .captainApprovalMode("ORGANIZER_APPROVAL")
                .build();
        tournamentService.saveSettings(UserPrincipal.create(organizerUser), tournamentId, setReq);

        // 2. Captain candidate submits self-registration
        SelfRegisterCaptainRequest registerReq = SelfRegisterCaptainRequest.builder()
                .teamName("Deccan Gladiators")
                .shortCode("DG")
                .captainMobile("+919611223344")
                .build();

        var registerAction = mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/teams/register-captain")
                        .header("Authorization", "Bearer " + playerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.teamName", is("Deccan Gladiators")))
                .andExpect(jsonPath("$.data.status", is("Pending_Approval")))
                .andReturn();

        Long appliedTeamId = objectMapper.readTree(registerAction.getResponse().getContentAsString())
                .path("data").path("teamId").asLong();

        // Verify DB: team is Pending_Approval, user does NOT yet have ROLE_CAPTAIN, no squad member
        Team teamInDb = teamRepository.findById(appliedTeamId).orElseThrow();
        assertEquals("Pending_Approval", teamInDb.getStatus());
        assertEquals(playerUser.getId(), teamInDb.getCaptainId());

        User preApprovedUser = userRepository.findById(playerUser.getId()).orElseThrow();
        boolean hasCaptainBeforeApproval = preApprovedUser.getRoles().stream()
                .anyMatch(r -> r.getName() == UserRole.CAPTAIN);
        assertFalse(hasCaptainBeforeApproval, "ROLE_CAPTAIN must NOT be granted while pending organizer review");
        assertEquals(0, squadMemberRepository.countByTeamId(appliedTeamId));

        // 3. Organizer views pending requests queue (API 31c)
        mockMvc.perform(get("/api/v1/tournaments/" + tournamentId + "/teams/captain-requests")
                        .header("Authorization", "Bearer " + organizerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].id", is(appliedTeamId.intValue())))
                .andExpect(jsonPath("$.data[0].name", is("Deccan Gladiators")))
                .andExpect(jsonPath("$.data[0].status", is("Pending_Approval")));

        // 4. Organizer approves the application (API 31d)
        CaptainApprovalRequest approveReq = CaptainApprovalRequest.builder()
                .action("APPROVED")
                .reason("Verified team credentials and KYC.")
                .build();

        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/teams/" + appliedTeamId + "/captain-approval")
                        .header("Authorization", "Bearer " + organizerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(approveReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.status", is("Accepted")))
                .andExpect(jsonPath("$.data.notificationDispatched", is(true)))
                .andExpect(jsonPath("$.data.captainUserId", is(playerUser.getId().intValue())));

        // 5. Verify post-approval DB state: team is Accepted, user has ROLE_CAPTAIN, squad member added
        Team approvedTeam = teamRepository.findById(appliedTeamId).orElseThrow();
        assertEquals("Accepted", approvedTeam.getStatus());

        User approvedUser = userRepository.findById(playerUser.getId()).orElseThrow();
        boolean hasCaptainAfterApproval = approvedUser.getRoles().stream()
                .anyMatch(r -> r.getName() == UserRole.CAPTAIN);
        assertTrue(hasCaptainAfterApproval, "ROLE_CAPTAIN must be granted upon Organizer approval");

        var squad = squadMemberRepository.findByTeamIdOrderByIdAsc(appliedTeamId);
        assertEquals(1, squad.size());
        assertTrue(squad.get(0).isCaptain());
        assertEquals(playerUser.getId(), squad.get(0).getPlayerUserId());

        // 6. Verify pending queue is now empty
        mockMvc.perform(get("/api/v1/tournaments/" + tournamentId + "/teams/captain-requests")
                        .header("Authorization", "Bearer " + organizerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    @DisplayName("API 31d: Organizer Rejection Workflow Lifecycle")
    void testOrganizerRejectionWorkflowLifecycle() throws Exception {
        // 1. Organizer configures tournament with BOTH and ORGANIZER_APPROVAL
        TournamentSettingsRequest setReq = TournamentSettingsRequest.builder()
                .teams(4)
                .squadMinimum(11)
                .squadMaximum(15)
                .budget(100_000_000L)
                .captainRegistrationMode("BOTH")
                .captainApprovalMode("ORGANIZER_APPROVAL")
                .build();
        tournamentService.saveSettings(UserPrincipal.create(organizerUser), tournamentId, setReq);

        // 2. Candidate registers
        SelfRegisterCaptainRequest registerReq = SelfRegisterCaptainRequest.builder()
                .teamName("Reject FC")
                .captainMobile("+919611223399")
                .build();

        var action = mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/teams/register-captain")
                        .header("Authorization", "Bearer " + anotherPlayerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isCreated())
                .andReturn();

        Long appliedTeamId = objectMapper.readTree(action.getResponse().getContentAsString())
                .path("data").path("teamId").asLong();

        // 3. Organizer rejects the application (API 31d)
        CaptainApprovalRequest rejectReq = CaptainApprovalRequest.builder()
                .action("REJECTED")
                .reason("Incomplete verification documentation.")
                .build();

        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/teams/" + appliedTeamId + "/captain-approval")
                        .header("Authorization", "Bearer " + organizerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(rejectReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.status", is("Rejected")))
                .andExpect(jsonPath("$.data.notificationDispatched", is(true)));

        // 4. Verify DB state: team is Rejected, user has NOT received ROLE_CAPTAIN
        Team rejectedTeam = teamRepository.findById(appliedTeamId).orElseThrow();
        assertEquals("Rejected", rejectedTeam.getStatus());

        User rejectedUser = userRepository.findById(anotherPlayerUser.getId()).orElseThrow();
        boolean hasCaptainRole = rejectedUser.getRoles().stream()
                .anyMatch(r -> r.getName() == UserRole.CAPTAIN);
        assertFalse(hasCaptainRole, "ROLE_CAPTAIN must NOT be granted when application is rejected");
        assertEquals(0, squadMemberRepository.countByTeamId(appliedTeamId));
    }
}
