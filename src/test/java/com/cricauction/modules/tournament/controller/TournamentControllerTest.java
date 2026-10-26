package com.cricauction.modules.tournament.controller;

import com.cricauction.modules.auth.entity.Role;
import com.cricauction.modules.auth.entity.User;
import com.cricauction.modules.auth.entity.enums.UserRole;
import com.cricauction.modules.auth.repository.RoleRepository;
import com.cricauction.modules.auth.repository.UserRepository;
import com.cricauction.modules.tournament.dto.request.CreateTournamentRequest;
import com.cricauction.modules.tournament.dto.request.TournamentSettingsRequest;
import com.cricauction.modules.tournament.dto.request.UpdateTournamentRequest;
import com.cricauction.modules.tournament.entity.Tournament;
import com.cricauction.modules.tournament.repository.TournamentRepository;
import com.cricauction.modules.tournament.repository.TournamentSettingsRepository;
import com.cricauction.security.JwtTokenProvider;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class TournamentControllerTest {

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
    private JwtTokenProvider jwtTokenProvider;

    private User organizerUser;
    private User otherOrganizerUser;
    private User playerOnlyUser;

    private String organizerToken;
    private String otherOrganizerToken;
    private String playerToken;

    @BeforeEach
    void setUp() {
        tournamentRepository.deleteAll();

        String suffix = UUID.randomUUID().toString().substring(0, 6);

        Role organizerRole = roleRepository.findByName(UserRole.ORGANIZER)
                .orElseGet(() -> roleRepository.save(Role.builder().name(UserRole.ORGANIZER).build()));
        Role playerRole = roleRepository.findByName(UserRole.PLAYER)
                .orElseGet(() -> roleRepository.save(Role.builder().name(UserRole.PLAYER).build()));

        organizerUser = User.builder()
                .fullName("Jay Shah")
                .email("organizer.spl." + suffix + "@cricauction.app")
                .mobile("+9198" + (int)(Math.random() * 89999999 + 10000000))
                .passwordHash("DummyHash123")
                .isActive(true)
                .build();
        organizerUser.addRole(organizerRole);
        organizerUser = userRepository.save(organizerUser);
        organizerToken = jwtTokenProvider.generateAccessToken(organizerUser);

        otherOrganizerUser = User.builder()
                .fullName("Rajiv Shukla")
                .email("organizer.bcci." + suffix + "@cricauction.app")
                .mobile("+9197" + (int)(Math.random() * 89999999 + 10000000))
                .passwordHash("DummyHash123")
                .isActive(true)
                .build();
        otherOrganizerUser.addRole(organizerRole);
        otherOrganizerUser = userRepository.save(otherOrganizerUser);
        otherOrganizerToken = jwtTokenProvider.generateAccessToken(otherOrganizerUser);

        playerOnlyUser = User.builder()
                .fullName("Ishan Kishan")
                .email("ishan.player." + suffix + "@cricauction.app")
                .mobile("+9196" + (int)(Math.random() * 89999999 + 10000000))
                .passwordHash("DummyHash123")
                .isActive(true)
                .build();
        playerOnlyUser.addRole(playerRole);
        playerOnlyUser = userRepository.save(playerOnlyUser);
        playerToken = jwtTokenProvider.generateAccessToken(playerOnlyUser);
    }

    @Test
    @DisplayName("API 18: POST /api/v1/tournaments - Create Tournament Draft (Screen 16)")
    void testCreateTournamentDraft() throws Exception {
        CreateTournamentRequest request = CreateTournamentRequest.builder()
                .name("Surat Premier League 2026")
                .location("Surat, Gujarat")
                .ground("Lalbhai Contractor Stadium")
                .startDate(LocalDate.of(2026, 9, 5))
                .endDate(LocalDate.of(2026, 9, 15))
                .auctionDate(LocalDate.of(2026, 8, 28))
                .auctionTime("19:00")
                .registrationStartDate(LocalDate.of(2026, 8, 1))
                .registrationEndDate(LocalDate.of(2026, 8, 25))
                .ballType("Tennis")
                .format("T20")
                .entryFee("Free")
                .description("Surat Premier League annual tournament and live auction.")
                .build();

        mockMvc.perform(post("/api/v1/tournaments")
                        .header("Authorization", "Bearer " + organizerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.message", is("Tournament draft created")))
                .andExpect(jsonPath("$.data.id", notNullValue()))
                .andExpect(jsonPath("$.data.slug", is("surat-premier-league-2026")))
                .andExpect(jsonPath("$.data.step", is(1)))
                .andExpect(jsonPath("$.data.createdAt", notNullValue()));
    }

    @Test
    @DisplayName("API 18 Security: POST /api/v1/tournaments - Player cannot create tournament")
    void testCreateTournamentNonOrganizerForbidden() throws Exception {
        CreateTournamentRequest request = CreateTournamentRequest.builder()
                .name("Unauthorized League")
                .location("Mumbai")
                .ground("Wankhede")
                .startDate(LocalDate.of(2026, 10, 1))
                .endDate(LocalDate.of(2026, 10, 10))
                .ballType("Leather")
                .format("T20")
                .build();

        mockMvc.perform(post("/api/v1/tournaments")
                        .header("Authorization", "Bearer " + playerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.error.code", is("ORGANIZER_ROLE_REQUIRED")));
    }

    @Test
    @DisplayName("API 19: GET /api/v1/tournaments - List Organizer's Tournaments with Status Filter")
    void testListOrganizerTournaments() throws Exception {
        createSampleTournament("Tournament Alpha", organizerUser.getId(), "draft");
        createSampleTournament("Tournament Beta", organizerUser.getId(), "published");
        createSampleTournament("Other Organizer Tourn", otherOrganizerUser.getId(), "draft");

        // Fetch all organizer tournaments
        mockMvc.perform(get("/api/v1/tournaments")
                        .header("Authorization", "Bearer " + organizerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data", hasSize(2)));

        // Filter by status = draft
        mockMvc.perform(get("/api/v1/tournaments?status=draft")
                        .header("Authorization", "Bearer " + organizerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].name", is("Tournament Alpha")));
    }

    @Test
    @DisplayName("API 20: GET /api/v1/tournaments/{id} - Get Tournament Details by ID or Slug")
    void testGetTournamentDetailsByIdOrSlug() throws Exception {
        Tournament tournament = createSampleTournament("Baroda T20 Cup", organizerUser.getId(), "draft");

        // By Public ID
        mockMvc.perform(get("/api/v1/tournaments/" + tournament.getPublicId())
                        .header("Authorization", "Bearer " + playerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.name", is("Baroda T20 Cup")))
                .andExpect(jsonPath("$.data.slug", is(tournament.getSlug())))
                .andExpect(jsonPath("$.data.settings.teams", is(10)))
                .andExpect(jsonPath("$.data.settings.budget", is(1000000)));

        // By Slug
        mockMvc.perform(get("/api/v1/tournaments/" + tournament.getSlug())
                        .header("Authorization", "Bearer " + organizerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name", is("Baroda T20 Cup")));
    }

    @Test
    @DisplayName("API 21: PATCH /api/v1/tournaments/{id} - Update Tournament Basic Details (Ownership Checked)")
    void testUpdateTournamentDetails() throws Exception {
        Tournament tournament = createSampleTournament("SPL 2026 Initial", organizerUser.getId(), "draft");

        UpdateTournamentRequest updateReq = UpdateTournamentRequest.builder()
                .ground("Reliance Stadium")
                .description("Updated tournament stadium and notes.")
                .build();

        // Successful update by Owner
        mockMvc.perform(patch("/api/v1/tournaments/" + tournament.getPublicId())
                        .header("Authorization", "Bearer " + organizerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.message", is("Tournament details updated")))
                .andExpect(jsonPath("$.data.ground", is("Reliance Stadium")));

        // Forbidden update by Another Organizer
        mockMvc.perform(patch("/api/v1/tournaments/" + tournament.getPublicId())
                        .header("Authorization", "Bearer " + otherOrganizerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code", is("TOURNAMENT_ACCESS_DENIED")));
    }

    @Test
    @DisplayName("API 22: PUT /api/v1/tournaments/{id}/settings - Configure Rules & Settings (Screen 17)")
    void testSaveTournamentSettings() throws Exception {
        Tournament tournament = createSampleTournament("Ahmedabad Champions League", organizerUser.getId(), "draft");

        TournamentSettingsRequest settingsReq = TournamentSettingsRequest.builder()
                .teams(12)
                .squadMinimum(13)
                .squadMaximum(16)
                .budget(1500000L)
                .basePrice(25000L)
                .bidIncrement(15000L)
                .bidTimer(12)
                .autoStartNext(true)
                .matches(25)
                .matchDuration(3)
                .allowPlayersToWithdraw(false)
                .build();

        mockMvc.perform(put("/api/v1/tournaments/" + tournament.getPublicId() + "/settings")
                        .header("Authorization", "Bearer " + organizerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(settingsReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.message", is("Tournament rules and auction settings saved")))
                .andExpect(jsonPath("$.data.step", is(2)))
                .andExpect(jsonPath("$.data.teamsAllocated", is(12)));
    }

    @Test
    @DisplayName("API 23: POST /api/v1/tournaments/{id}/publish - Launch Tournament & Generate Links")
    void testPublishTournament() throws Exception {
        Tournament tournament = createSampleTournament("Rajkot Premier League", organizerUser.getId(), "draft");

        mockMvc.perform(post("/api/v1/tournaments/" + tournament.getPublicId() + "/publish")
                        .header("Authorization", "Bearer " + organizerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.message", is("Tournament is now live for registrations and invitations")))
                .andExpect(jsonPath("$.data.status", is("published")))
                .andExpect(jsonPath("$.data.registrationLink", containsString("register/rajkot-premier-league")))
                .andExpect(jsonPath("$.data.captainInviteLink", containsString("join/rajkot-premier-league")));
    }

    @Test
    @DisplayName("API 24: DELETE /api/v1/tournaments/{id} - Delete Tournament (Ownership Protected)")
    void testDeleteTournament() throws Exception {
        Tournament tournament = createSampleTournament("Cancel League", organizerUser.getId(), "draft");

        // Forbidden attempt by another organizer
        mockMvc.perform(delete("/api/v1/tournaments/" + tournament.getPublicId())
                        .header("Authorization", "Bearer " + otherOrganizerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code", is("TOURNAMENT_ACCESS_DENIED")));

        // Successful deletion by owner
        mockMvc.perform(delete("/api/v1/tournaments/" + tournament.getPublicId())
                        .header("Authorization", "Bearer " + organizerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", is("Tournament deleted successfully")));
    }

    @Test
    @DisplayName("API 25: GET /api/v1/tournaments/public/{slug} - Public Landing Page (No Auth Token Required)")
    void testGetPublicTournamentLandingPage() throws Exception {
        Tournament tournament = createSampleTournament("Gujarat T20 Bash", organizerUser.getId(), "published");

        // Unauthenticated request without Authorization header
        mockMvc.perform(get("/api/v1/tournaments/public/" + tournament.getSlug()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.id", is(tournament.getPublicId())))
                .andExpect(jsonPath("$.data.name", is("Gujarat T20 Bash")))
                .andExpect(jsonPath("$.data.location", is("Surat, Gujarat")))
                .andExpect(jsonPath("$.data.ballType", is("Tennis")))
                .andExpect(jsonPath("$.data.format", is("T20")))
                .andExpect(jsonPath("$.data.capacity", is(150)))
                .andExpect(jsonPath("$.data.registrationOpen", notNullValue()));
    }

    private Tournament createSampleTournament(String name, Long organizerId, String status) {
        String slug = name.toLowerCase().replaceAll("[^a-z0-9]+", "-");
        Tournament tournament = Tournament.builder()
                .publicId("tourn_" + slug.replace("-", "_"))
                .organizerId(organizerId)
                .name(name)
                .slug(slug)
                .location("Surat, Gujarat")
                .ground("Lalbhai Contractor Stadium")
                .startDate(LocalDate.of(2026, 9, 5))
                .endDate(LocalDate.of(2026, 9, 15))
                .auctionDate(LocalDate.of(2026, 8, 28))
                .auctionTime("19:00")
                .registrationStartDate(LocalDate.of(2026, 8, 1))
                .registrationEndDate(LocalDate.of(2026, 8, 25))
                .ballType("Tennis")
                .format("T20")
                .entryFee("Free")
                .description("Test tournament description.")
                .status(com.cricauction.modules.tournament.entity.enums.TournamentStatus.fromString(status))
                .step(1)
                .build();

        com.cricauction.modules.tournament.entity.TournamentSettings settings =
                com.cricauction.modules.tournament.entity.TournamentSettings.builder()
                        .tournament(tournament)
                        .totalTeams(10)
                        .squadMinimum(12)
                        .squadMaximum(15)
                        .teamBudget(1000000L)
                        .defaultBasePrice(20000L)
                        .bidIncrement(10000L)
                        .bidTimerSeconds(10)
                        .autoStartNext(false)
                        .matches(20)
                        .matchDurationHours(3)
                        .allowPlayersToWithdraw(true)
                        .build();

        tournament.setSettings(settings);
        return tournamentRepository.save(tournament);
    }
}
