package com.cricauction.modules.dashboard.controller;

import com.cricauction.modules.auth.entity.Role;
import com.cricauction.modules.auth.entity.User;
import com.cricauction.modules.auth.entity.enums.UserRole;
import com.cricauction.modules.auth.repository.RoleRepository;
import com.cricauction.modules.auth.repository.UserRepository;
import com.cricauction.modules.player.entity.TournamentPlayer;
import com.cricauction.modules.player.repository.TournamentPlayerRepository;
import com.cricauction.modules.team.entity.Team;
import com.cricauction.modules.team.repository.TeamRepository;
import com.cricauction.modules.tournament.entity.Tournament;
import com.cricauction.modules.tournament.entity.TournamentSettings;
import com.cricauction.modules.tournament.entity.enums.TournamentStatus;
import com.cricauction.modules.tournament.repository.TournamentRepository;
import com.cricauction.modules.tournament.repository.TournamentSettingsRepository;
import com.cricauction.security.JwtTokenProvider;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class DashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private TournamentRepository tournamentRepository;

    @Autowired
    private TournamentSettingsRepository settingsRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private TournamentPlayerRepository tournamentPlayerRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private User organizerUser;
    private String organizerToken;

    private User captainUser;
    private String captainToken;

    private User playerUser;
    private String playerToken;

    private Tournament upcomingTournament;
    private Tournament draftTournament;
    private Team captainTeam;

    @BeforeEach
    void setUp() {
        Role organizerRole = roleRepository.findByName(UserRole.ORGANIZER)
                .orElseGet(() -> roleRepository.save(Role.builder().name(UserRole.ORGANIZER).build()));
        Role captainRole = roleRepository.findByName(UserRole.CAPTAIN)
                .orElseGet(() -> roleRepository.save(Role.builder().name(UserRole.CAPTAIN).build()));
        Role playerRole = roleRepository.findByName(UserRole.PLAYER)
                .orElseGet(() -> roleRepository.save(Role.builder().name(UserRole.PLAYER).build()));

        // Create Organizer
        String uid = UUID.randomUUID().toString().substring(0, 8);
        organizerUser = User.builder()
                .fullName("Organizer " + uid)
                .email("organizer_" + uid + "@test.com")
                .mobile("+9199" + uid)
                .passwordHash("$2a$10$abcdefghijklmnopqrstuvwxyz1234567890")
                .isEmailVerified(true)
                .isActive(true)
                .build();
        organizerUser.addRole(organizerRole);
        organizerUser = userRepository.save(organizerUser);
        organizerToken = "Bearer " + jwtTokenProvider.generateAccessToken(organizerUser);

        // Create Captain
        captainUser = User.builder()
                .fullName("Captain " + uid)
                .email("captain_" + uid + "@test.com")
                .mobile("+9198" + uid)
                .passwordHash("$2a$10$abcdefghijklmnopqrstuvwxyz1234567890")
                .isEmailVerified(true)
                .isActive(true)
                .build();
        captainUser.addRole(captainRole);
        captainUser = userRepository.save(captainUser);
        captainToken = "Bearer " + jwtTokenProvider.generateAccessToken(captainUser);

        // Create Player
        playerUser = User.builder()
                .fullName("Player " + uid)
                .email("player_" + uid + "@test.com")
                .mobile("+9197" + uid)
                .passwordHash("$2a$10$abcdefghijklmnopqrstuvwxyz1234567890")
                .isEmailVerified(true)
                .isActive(true)
                .build();
        playerUser.addRole(playerRole);
        playerUser = userRepository.save(playerUser);
        playerToken = "Bearer " + jwtTokenProvider.generateAccessToken(playerUser);

        // Seed Tournaments for Organizer
        upcomingTournament = tournamentRepository.save(Tournament.builder()
                .publicId("tourn_dash_" + uid)
                .organizerId(organizerUser.getId())
                .name("Surat Champions Trophy " + uid)
                .slug("surat-champions-trophy-" + uid)
                .location("Surat")
                .ground("CB Patel Stadium")
                .startDate(LocalDate.now().plusDays(10))
                .endDate(LocalDate.now().plusDays(20))
                .auctionDate(LocalDate.now().plusDays(5))
                .auctionTime("19:00")
                .ballType("Leather")
                .format("T20")
                .status(TournamentStatus.PUBLISHED)
                .step(4)
                .build());

        TournamentSettings settings = TournamentSettings.builder()
                .tournament(upcomingTournament)
                .totalTeams(8)
                .squadMinimum(11)
                .squadMaximum(15)
                .teamBudget(1000000L)
                .defaultBasePrice(20000L)
                .bidIncrement(10000L)
                .bidTimerSeconds(15)
                .build();
        settingsRepository.save(settings);

        draftTournament = tournamentRepository.save(Tournament.builder()
                .publicId("draft_dash_" + uid)
                .organizerId(organizerUser.getId())
                .name("Draft Trophy " + uid)
                .slug("draft-trophy-" + uid)
                .location("Surat")
                .ground("Lalbhai Stadium")
                .startDate(LocalDate.now().plusDays(30))
                .endDate(LocalDate.now().plusDays(40))
                .ballType("Tennis")
                .format("T20")
                .status(TournamentStatus.DRAFT)
                .step(2)
                .build());

        // Seed Team for Captain
        captainTeam = teamRepository.save(Team.builder()
                .tournament(upcomingTournament)
                .captainId(captainUser.getId())
                .name("Surat Strikers " + uid)
                .shortCode("SS")
                .captainName(captainUser.getFullName())
                .captainMobile(captainUser.getMobile())
                .initialBudget(1000000L)
                .remainingBudget(850000L)
                .inviteCode("CAPINV_" + uid)
                .status("Active")
                .build());

        // Seed TournamentPlayer registration for Player
        tournamentPlayerRepository.save(TournamentPlayer.builder()
                .tournament(upcomingTournament)
                .user(playerUser)
                .playerName(playerUser.getFullName())
                .registeredRole("All-Rounder")
                .battingStyle("Right-hand bat")
                .bowlingStyle("Right-arm medium")
                .basePrice(5000L)
                .status("Registered")
                .mobile(playerUser.getMobile())
                .build());
    }

    @Test
    @DisplayName("API 15: GET /dashboard/organizer - Successful feed with active draft and stats")
    void getOrganizerDashboard_success() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/organizer")
                        .header("Authorization", organizerToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.message", containsString("loaded successfully")))
                .andExpect(jsonPath("$.data.hasActiveDraft", is(true)))
                .andExpect(jsonPath("$.data.draftSummary.id", is(draftTournament.getPublicId())))
                .andExpect(jsonPath("$.data.draftSummary.step", is(2)))
                .andExpect(jsonPath("$.data.stats.myTournaments", greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.data.stats.teams", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.stats.playersRegistered", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.upcomingTournament.id", is(upcomingTournament.getPublicId())))
                .andExpect(jsonPath("$.data.upcomingTournament.status", is("Registration Open")));
    }

    @Test
    @DisplayName("API 16: GET /dashboard/captain - Successful feed with team budget and squad count")
    void getCaptainDashboard_success() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/captain")
                        .header("Authorization", captainToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.team.name", is(captainTeam.getName())))
                .andExpect(jsonPath("$.data.team.remainingBudget", is(850000)))
                .andExpect(jsonPath("$.data.team.budgetFormatted", is("₹8.5L")))
                .andExpect(jsonPath("$.data.upcomingAuction.name", is(upcomingTournament.getName())));
    }

    @Test
    @DisplayName("API 17: GET /dashboard/player - Successful feed with active journey and my tournaments")
    void getPlayerDashboard_success() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/player")
                        .header("Authorization", playerToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.activeJourney.tournamentId", is(upcomingTournament.getPublicId())))
                .andExpect(jsonPath("$.data.activeJourney.status", is("Registered")))
                .andExpect(jsonPath("$.data.activeJourney.basePrice", is(5000)))
                .andExpect(jsonPath("$.data.myTournaments", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.data.myTournaments[0].badge", is("Pending")));
    }

    @Test
    @DisplayName("API 16: GET /dashboard/captain - When user is not a captain, returns 403 Forbidden")
    void getCaptainDashboard_notCaptain_returns403() throws Exception {
        // Player user calls captain endpoint
        mockMvc.perform(get("/api/v1/dashboard/captain")
                        .header("Authorization", playerToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("API 17: GET /dashboard/player - When player has no registrations, returns empty feed")
    void getPlayerDashboard_noRegistrations_returnsEmptyFeed() throws Exception {
        // Fresh user with no registrations
        String freshUid = UUID.randomUUID().toString().substring(0, 8);
        Role playerRole = roleRepository.findByName(UserRole.PLAYER).orElseThrow();
        User freshPlayer = User.builder()
                .fullName("Fresh " + freshUid)
                .email("fresh_" + freshUid + "@test.com")
                .mobile("+9196" + freshUid)
                .passwordHash("$2a$10$abcdefghijklmnopqrstuvwxyz1234567890")
                .isEmailVerified(true)
                .isActive(true)
                .build();
        freshPlayer.addRole(playerRole);
        freshPlayer = userRepository.save(freshPlayer);
        String freshToken = "Bearer " + jwtTokenProvider.generateAccessToken(freshPlayer);

        mockMvc.perform(get("/api/v1/dashboard/player")
                        .header("Authorization", freshToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.activeJourney").value(nullValue()))
                .andExpect(jsonPath("$.data.myTournaments", hasSize(0)))
                .andExpect(jsonPath("$.data.upcomingMatches", hasSize(0)));
    }

    @Test
    @DisplayName("Security: Anonymous requests to dashboard endpoints return 401 Unauthorized")
    void unauthorizedAccess_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/organizer"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/dashboard/captain"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/dashboard/player"))
                .andExpect(status().isUnauthorized());
    }
}
