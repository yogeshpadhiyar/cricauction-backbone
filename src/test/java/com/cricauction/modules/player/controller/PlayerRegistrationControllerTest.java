package com.cricauction.modules.player.controller;

import com.cricauction.modules.auth.entity.Role;
import com.cricauction.modules.auth.entity.User;
import com.cricauction.modules.auth.entity.enums.UserRole;
import com.cricauction.modules.auth.repository.RoleRepository;
import com.cricauction.modules.auth.repository.UserRepository;
import com.cricauction.modules.player.dto.request.PlayerRegistrationRequest;
import com.cricauction.modules.player.dto.request.UpdatePlayerBasePriceRequest;
import com.cricauction.modules.player.dto.request.UpdatePlayerStatusRequest;
import com.cricauction.modules.player.entity.TournamentPlayer;
import com.cricauction.modules.player.repository.TournamentPlayerRepository;
import com.cricauction.modules.tournament.dto.request.CreateTournamentRequest;
import com.cricauction.modules.tournament.dto.request.TournamentSettingsRequest;
import com.cricauction.modules.tournament.dto.response.CreateTournamentResponse;
import com.cricauction.modules.tournament.entity.Tournament;
import com.cricauction.modules.tournament.entity.TournamentSettings;
import com.cricauction.modules.tournament.entity.enums.TournamentStatus;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class PlayerRegistrationControllerTest {

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
    private TournamentPlayerRepository tournamentPlayerRepository;

    @Autowired
    private TournamentService tournamentService;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private User organizerUser;
    private User otherOrganizerUser;
    private User playerUser;
    private User secondPlayerUser;

    private String organizerToken;
    private String otherOrganizerToken;
    private String playerToken;
    private String secondPlayerToken;

    private String tournamentId;
    private Tournament tournamentEntity;

    @BeforeEach
    void setUp() {
        tournamentPlayerRepository.deleteAll();
        settingsRepository.deleteAll();
        tournamentRepository.deleteAll();

        String suffix = UUID.randomUUID().toString().substring(0, 6);

        Role organizerRole = roleRepository.findByName(UserRole.ORGANIZER)
                .orElseGet(() -> roleRepository.save(Role.builder().name(UserRole.ORGANIZER).build()));
        Role playerRole = roleRepository.findByName(UserRole.PLAYER)
                .orElseGet(() -> roleRepository.save(Role.builder().name(UserRole.PLAYER).build()));

        organizerUser = User.builder()
                .fullName("Rohit Sharma")
                .email("organizer." + suffix + "@cricauction.app")
                .mobile("+9198" + (int) (Math.random() * 89999999 + 10000000))
                .passwordHash("DummyHash123")
                .isActive(true)
                .build();
        organizerUser.addRole(organizerRole);
        organizerUser = userRepository.save(organizerUser);
        organizerToken = jwtTokenProvider.generateAccessToken(organizerUser);

        otherOrganizerUser = User.builder()
                .fullName("Sourav Ganguly")
                .email("other.organizer." + suffix + "@cricauction.app")
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
                .city("Baroda")
                .isActive(true)
                .build();
        playerUser.addRole(playerRole);
        playerUser = userRepository.save(playerUser);
        playerToken = jwtTokenProvider.generateAccessToken(playerUser);

        secondPlayerUser = User.builder()
                .fullName("Jasprit Bumrah")
                .email("bumrah." + suffix + "@cricauction.app")
                .mobile("+9195" + (int) (Math.random() * 89999999 + 10000000))
                .passwordHash("DummyHash123")
                .city("Ahmedabad")
                .isActive(true)
                .build();
        secondPlayerUser.addRole(playerRole);
        secondPlayerUser = userRepository.save(secondPlayerUser);
        secondPlayerToken = jwtTokenProvider.generateAccessToken(secondPlayerUser);

        // Create Tournament with capacity = 2 teams * 2 max players = 4 max players
        CreateTournamentRequest tournReq = CreateTournamentRequest.builder()
                .name("Surat Premier League " + suffix)
                .location("Surat")
                .ground("Lalbhai Contractor Stadium")
                .startDate(LocalDate.now().plusDays(10))
                .endDate(LocalDate.now().plusDays(20))
                .ballType("Leather")
                .format("T20")
                .entryFee("Free")
                .build();

        CreateTournamentResponse draft = tournamentService.createTournament(UserPrincipal.create(organizerUser), tournReq);
        tournamentId = draft.getId();

        TournamentSettingsRequest rulesReq = TournamentSettingsRequest.builder()
                .teams(2)
                .squadMinimum(1)
                .squadMaximum(2) // capacity = 2 * 2 = 4 players
                .budget(1000000L)
                .basePrice(5000L)
                .bidIncrement(1000L)
                .bidTimer(15)
                .build();

        tournamentService.saveSettings(UserPrincipal.create(organizerUser), tournamentId, rulesReq);
        tournamentEntity = tournamentRepository.findByPublicId(tournamentId).orElseThrow();
    }

    @Test
    @DisplayName("API 34: Player registers for tournament successfully")
    void testRegisterPlayer_Success() throws Exception {
        PlayerRegistrationRequest request = PlayerRegistrationRequest.builder()
                .playingRole("All-Rounder")
                .battingStyle("Right-hand bat")
                .bowlingStyle("Right-arm medium fast")
                .basePrice(7500L)
                .city("Baroda")
                .jerseySize("L")
                .cricHeroesLink("https://cricheroes.in/player/hardik")
                .build();

        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/players/register")
                        .header("Authorization", "Bearer " + playerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.registrationId", startsWith("reg_")))
                .andExpect(jsonPath("$.data.status", is("Registered")))
                .andExpect(jsonPath("$.data.tournamentName", containsString("Surat Premier League")));
    }

    @Test
    @DisplayName("API 34: Duplicate registration attempt rejected with 409 Conflict")
    void testRegisterPlayer_DuplicateRejected() throws Exception {
        PlayerRegistrationRequest request = PlayerRegistrationRequest.builder()
                .playingRole("All-Rounder")
                .build();

        // First registration succeeds
        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/players/register")
                        .header("Authorization", "Bearer " + playerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        // Second registration fails
        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/players/register")
                        .header("Authorization", "Bearer " + playerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.error.code", is("ALREADY_REGISTERED")));
    }

    @Test
    @DisplayName("API 34: Default base price inherited from tournament settings if omitted")
    void testRegisterPlayer_DefaultBasePriceInherited() throws Exception {
        PlayerRegistrationRequest request = PlayerRegistrationRequest.builder()
                .playingRole("Batsman")
                .build();

        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/players/register")
                        .header("Authorization", "Bearer " + playerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        TournamentPlayer tp = tournamentPlayerRepository.findByTournamentPublicIdAndUserId(tournamentId, playerUser.getId()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(5000L, tp.getBasePrice());
    }

    @Test
    @DisplayName("API 34: Tournament capacity reached rejects registration with 409 Conflict")
    void testRegisterPlayer_CapacityReached() throws Exception {
        // Set capacity to 1 player (1 team * 1 player)
        TournamentSettings settings = settingsRepository.findById(tournamentEntity.getId()).orElseThrow();
        settings.setTotalTeams(1);
        settings.setSquadMaximum(1);
        settings.setRegisteredPlayersCount(1); // Already at capacity 1
        settingsRepository.save(settings);

        PlayerRegistrationRequest request = PlayerRegistrationRequest.builder()
                .playingRole("Bowler")
                .build();

        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/players/register")
                        .header("Authorization", "Bearer " + playerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.error.code", is("CAPACITY_REACHED")));
    }

    @Test
    @DisplayName("API 35: Player tracks own registration status successfully")
    void testGetMyRegistrationStatus_Success() throws Exception {
        PlayerRegistrationRequest request = PlayerRegistrationRequest.builder()
                .playingRole("All-Rounder")
                .basePrice(8000L)
                .build();

        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/players/register")
                        .header("Authorization", "Bearer " + playerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/tournaments/" + tournamentId + "/players/my-status")
                        .header("Authorization", "Bearer " + playerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.tournamentId", is(tournamentId)))
                .andExpect(jsonPath("$.data.status", is("Registered")))
                .andExpect(jsonPath("$.data.basePrice", is(8000)));
    }

    @Test
    @DisplayName("API 35: Registration status 404 when player not registered")
    void testGetMyRegistrationStatus_NotFound() throws Exception {
        mockMvc.perform(get("/api/v1/tournaments/" + tournamentId + "/players/my-status")
                        .header("Authorization", "Bearer " + playerToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code", is("REGISTRATION_NOT_FOUND")));
    }

    @Test
    @DisplayName("API 36: Player withdraws from tournament successfully")
    void testWithdrawFromTournament_Success() throws Exception {
        PlayerRegistrationRequest request = PlayerRegistrationRequest.builder()
                .playingRole("Bowler")
                .build();

        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/players/register")
                        .header("Authorization", "Bearer " + playerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/v1/tournaments/" + tournamentId + "/players/withdraw")
                        .header("Authorization", "Bearer " + playerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.message", containsString("successfully withdrawn")));

        org.junit.jupiter.api.Assertions.assertFalse(
                tournamentPlayerRepository.existsByTournamentIdAndUserId(tournamentEntity.getId(), playerUser.getId())
        );
    }

    @Test
    @DisplayName("API 36: Withdrawal rejected when tournament settings disallow withdrawal")
    void testWithdrawFromTournament_Disallowed() throws Exception {
        TournamentSettings settings = settingsRepository.findById(tournamentEntity.getId()).orElseThrow();
        settings.setAllowPlayersToWithdraw(false);
        settingsRepository.save(settings);

        PlayerRegistrationRequest request = PlayerRegistrationRequest.builder()
                .playingRole("Batsman")
                .build();

        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/players/register")
                        .header("Authorization", "Bearer " + playerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/v1/tournaments/" + tournamentId + "/players/withdraw")
                        .header("Authorization", "Bearer " + playerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code", is("WITHDRAWAL_NOT_ALLOWED")));
    }

    @Test
    @DisplayName("API 36: Withdrawal returns 404 when player is not registered")
    void testWithdrawFromTournament_NotFound() throws Exception {
        mockMvc.perform(delete("/api/v1/tournaments/" + tournamentId + "/players/withdraw")
                        .header("Authorization", "Bearer " + playerToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code", is("REGISTRATION_NOT_FOUND")));
    }

    @Test
    @DisplayName("API 37: Organizer lists registered players with filters")
    void testListRegisteredPlayers_Success() throws Exception {
        PlayerRegistrationRequest req1 = PlayerRegistrationRequest.builder().playingRole("Batsman").build();
        PlayerRegistrationRequest req2 = PlayerRegistrationRequest.builder().playingRole("Bowler").build();

        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/players/register")
                        .header("Authorization", "Bearer " + playerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req1)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/players/register")
                        .header("Authorization", "Bearer " + secondPlayerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req2)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/tournaments/" + tournamentId + "/players")
                        .header("Authorization", "Bearer " + organizerToken)
                        .param("filter", "All")
                        .param("page", "1")
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.total", is(2)))
                .andExpect(jsonPath("$.data.players", hasSize(2)));
    }

    @Test
    @DisplayName("API 37: Listing players by non-owner returns 403 Forbidden")
    void testListRegisteredPlayers_ForbiddenForNonOwner() throws Exception {
        mockMvc.perform(get("/api/v1/tournaments/" + tournamentId + "/players")
                        .header("Authorization", "Bearer " + otherOrganizerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code", is("TOURNAMENT_ACCESS_DENIED")));
    }

    @Test
    @DisplayName("API 38: Registration capacity stats returns accurate breakdown")
    void testGetCapacityStats_Success() throws Exception {
        PlayerRegistrationRequest req1 = PlayerRegistrationRequest.builder().playingRole("Batsman").build();
        PlayerRegistrationRequest req2 = PlayerRegistrationRequest.builder().playingRole("Bowler").build();

        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/players/register")
                        .header("Authorization", "Bearer " + playerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req1)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/players/register")
                        .header("Authorization", "Bearer " + secondPlayerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req2)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/tournaments/" + tournamentId + "/players/stats")
                        .header("Authorization", "Bearer " + organizerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.capacity", is(4)))
                .andExpect(jsonPath("$.data.registered", is(2)))
                .andExpect(jsonPath("$.data.percentageFilled", is(50)))
                .andExpect(jsonPath("$.data.isOpen", is(true)))
                .andExpect(jsonPath("$.data.roleBreakdown.batsmen", is(1)))
                .andExpect(jsonPath("$.data.roleBreakdown.bowlers", is(1)));
    }

    @Test
    @DisplayName("API 39: Organizer approves/rejects player status")
    void testUpdatePlayerStatus_Success() throws Exception {
        PlayerRegistrationRequest req = PlayerRegistrationRequest.builder().playingRole("Batsman").build();
        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/players/register")
                        .header("Authorization", "Bearer " + playerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

        TournamentPlayer tp = tournamentPlayerRepository.findByTournamentPublicIdAndUserId(tournamentId, playerUser.getId()).orElseThrow();

        UpdatePlayerStatusRequest statusReq = UpdatePlayerStatusRequest.builder()
                .status("Approved")
                .reason("Documents and CricHeroes stats verified")
                .build();

        mockMvc.perform(patch("/api/v1/tournaments/" + tournamentId + "/players/" + tp.getId() + "/status")
                        .header("Authorization", "Bearer " + organizerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(statusReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.message", is("Player status updated")));

        TournamentPlayer updated = tournamentPlayerRepository.findById(tp.getId()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals("Approved", updated.getStatus());
        org.junit.jupiter.api.Assertions.assertEquals("Documents and CricHeroes stats verified", updated.getReviewReason());
    }

    @Test
    @DisplayName("API 39: Update status by non-owner returns 403 Forbidden")
    void testUpdatePlayerStatus_ForbiddenForNonOwner() throws Exception {
        PlayerRegistrationRequest req = PlayerRegistrationRequest.builder().playingRole("Batsman").build();
        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/players/register")
                        .header("Authorization", "Bearer " + playerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

        TournamentPlayer tp = tournamentPlayerRepository.findByTournamentPublicIdAndUserId(tournamentId, playerUser.getId()).orElseThrow();

        UpdatePlayerStatusRequest statusReq = UpdatePlayerStatusRequest.builder()
                .status("Approved")
                .build();

        mockMvc.perform(patch("/api/v1/tournaments/" + tournamentId + "/players/" + tp.getId() + "/status")
                        .header("Authorization", "Bearer " + otherOrganizerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(statusReq)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code", is("TOURNAMENT_ACCESS_DENIED")));
    }

    @Test
    @DisplayName("API 40: Organizer updates player base price")
    void testUpdatePlayerBasePrice_Success() throws Exception {
        PlayerRegistrationRequest req = PlayerRegistrationRequest.builder().playingRole("Batsman").build();
        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/players/register")
                        .header("Authorization", "Bearer " + playerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

        TournamentPlayer tp = tournamentPlayerRepository.findByTournamentPublicIdAndUserId(tournamentId, playerUser.getId()).orElseThrow();

        UpdatePlayerBasePriceRequest priceReq = UpdatePlayerBasePriceRequest.builder()
                .basePrice(15000L)
                .build();

        mockMvc.perform(patch("/api/v1/tournaments/" + tournamentId + "/players/" + tp.getId() + "/base-price")
                        .header("Authorization", "Bearer " + organizerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(priceReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.message", is("Player base price updated")));

        TournamentPlayer updated = tournamentPlayerRepository.findById(tp.getId()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(15000L, updated.getBasePrice());
    }

    @Test
    @DisplayName("API 40: Update base price by non-owner returns 403 Forbidden")
    void testUpdatePlayerBasePrice_ForbiddenForNonOwner() throws Exception {
        PlayerRegistrationRequest req = PlayerRegistrationRequest.builder().playingRole("Batsman").build();
        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/players/register")
                        .header("Authorization", "Bearer " + playerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

        TournamentPlayer tp = tournamentPlayerRepository.findByTournamentPublicIdAndUserId(tournamentId, playerUser.getId()).orElseThrow();

        UpdatePlayerBasePriceRequest priceReq = UpdatePlayerBasePriceRequest.builder()
                .basePrice(20000L)
                .build();

        mockMvc.perform(patch("/api/v1/tournaments/" + tournamentId + "/players/" + tp.getId() + "/base-price")
                        .header("Authorization", "Bearer " + otherOrganizerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(priceReq)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code", is("TOURNAMENT_ACCESS_DENIED")));
    }

    @Test
    @DisplayName("Security & Scale: Rejection frees capacity slot, restore blocked if full (PT-REV-2)")
    void testRejectionFreesCapacitySlotAndRestoringFullFails() throws Exception {
        // Set capacity to exactly 1 player
        TournamentSettings settings = settingsRepository.findById(tournamentEntity.getId()).orElseThrow();
        settings.setTotalTeams(1);
        settings.setSquadMaximum(1);
        settings.setRegisteredPlayersCount(0);
        settingsRepository.save(settings);

        // Player 1 registers successfully
        PlayerRegistrationRequest req1 = PlayerRegistrationRequest.builder().playingRole("Batsman").build();
        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/players/register")
                        .header("Authorization", "Bearer " + playerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req1)))
                .andExpect(status().isCreated());

        TournamentPlayer tp1 = tournamentPlayerRepository.findByTournamentPublicIdAndUserId(tournamentId, playerUser.getId()).orElseThrow();

        // Player 2 attempts to register -> rejected with 409 CAPACITY_REACHED
        PlayerRegistrationRequest req2 = PlayerRegistrationRequest.builder().playingRole("Bowler").build();
        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/players/register")
                        .header("Authorization", "Bearer " + secondPlayerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req2)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code", is("CAPACITY_REACHED")));

        // Organizer rejects Player 1
        UpdatePlayerStatusRequest rejectReq = UpdatePlayerStatusRequest.builder()
                .status("Rejected")
                .reason("Incomplete documents")
                .build();
        mockMvc.perform(patch("/api/v1/tournaments/" + tournamentId + "/players/" + tp1.getId() + "/status")
                        .header("Authorization", "Bearer " + organizerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(rejectReq)))
                .andExpect(status().isOk());

        // Now Player 2 registers -> succeeds because slot was freed!
        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/players/register")
                        .header("Authorization", "Bearer " + secondPlayerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req2)))
                .andExpect(status().isCreated());

        // Organizer tries to restore Player 1 to Approved while capacity is full -> 409 Conflict
        UpdatePlayerStatusRequest restoreReq = UpdatePlayerStatusRequest.builder()
                .status("Approved")
                .build();
        mockMvc.perform(patch("/api/v1/tournaments/" + tournamentId + "/players/" + tp1.getId() + "/status")
                        .header("Authorization", "Bearer " + organizerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(restoreReq)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code", is("CAPACITY_REACHED")));
    }

    @Test
    @DisplayName("Correctness: Filter by Wicket-Keeper with hyphen or space returns player (PT-REV-3)")
    void testFilterByWicketKeeperWithHyphenOrSpace() throws Exception {
        PlayerRegistrationRequest req = PlayerRegistrationRequest.builder()
                .playingRole("Wicket-Keeper")
                .build();
        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/players/register")
                        .header("Authorization", "Bearer " + playerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

        // Filter with hyphen
        mockMvc.perform(get("/api/v1/tournaments/" + tournamentId + "/players?filter=Wicket-Keeper")
                        .header("Authorization", "Bearer " + organizerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.players", hasSize(1)))
                .andExpect(jsonPath("$.data.players[0].name", is("Hardik Pandya")));

        // Filter with space
        mockMvc.perform(get("/api/v1/tournaments/" + tournamentId + "/players?filter=Wicket Keeper")
                        .header("Authorization", "Bearer " + organizerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.players", hasSize(1)))
                .andExpect(jsonPath("$.data.players[0].name", is("Hardik Pandya")));
    }

    @Test
    @DisplayName("Correctness: Player with null basePrice formats safely as ₹0 (PT-REV-4)")
    void testListPlayersWithNullBasePriceDoesNotCrash() throws Exception {
        TournamentPlayer tp = TournamentPlayer.builder()
                .tournament(tournamentEntity)
                .user(playerUser)
                .playerName("Hardik Pandya")
                .registeredRole("All-Rounder")
                .basePrice(null)
                .status("Registered")
                .build();
        tournamentPlayerRepository.save(tp);

        mockMvc.perform(get("/api/v1/tournaments/" + tournamentId + "/players")
                        .header("Authorization", "Bearer " + organizerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.players[0].basePrice", is("₹0")));
    }

    @Test
    @DisplayName("Security SEC-MOD6-01: Withdrawing a rejected player does not double-decrement capacity slot")
    void testWithdrawDoesNotDoubleDecrementOnRejectedPlayer() throws Exception {
        // Register player
        PlayerRegistrationRequest regRequest = PlayerRegistrationRequest.builder()
                .playingRole("Batsman")
                .build();
        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/players/register")
                        .header("Authorization", "Bearer " + playerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(regRequest)))
                .andExpect(status().isCreated());

        TournamentPlayer tp = tournamentPlayerRepository.findByTournamentPublicIdAndUserId(tournamentId, playerUser.getId()).orElseThrow();
        TournamentSettings settings = settingsRepository.findById(tournamentEntity.getId()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(1, settings.getRegisteredPlayersCount());

        // Organizer rejects player (decrements slot to 0)
        UpdatePlayerStatusRequest rejectReq = UpdatePlayerStatusRequest.builder()
                .status("Rejected")
                .reason("Incomplete documents")
                .build();
        mockMvc.perform(patch("/api/v1/tournaments/" + tournamentId + "/players/" + tp.getId() + "/status")
                        .header("Authorization", "Bearer " + organizerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(rejectReq)))
                .andExpect(status().isOk());

        settings = settingsRepository.findById(tournamentEntity.getId()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(0, settings.getRegisteredPlayersCount());

        // Rejected player withdraws: slot should NOT be decremented again to negative
        mockMvc.perform(delete("/api/v1/tournaments/" + tournamentId + "/players/withdraw")
                        .header("Authorization", "Bearer " + playerToken))
                .andExpect(status().isOk());

        settings = settingsRepository.findById(tournamentEntity.getId()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(0, settings.getRegisteredPlayersCount());
        org.junit.jupiter.api.Assertions.assertTrue(tournamentPlayerRepository.findByTournamentPublicIdAndUserId(tournamentId, playerUser.getId()).isEmpty());
    }

    @Test
    @DisplayName("Security SEC-MOD6-02: Status update rejects invalid status strings and already-sold players")
    void testUpdatePlayerStatusRejectsInvalidStatusAndSoldPlayer() throws Exception {
        TournamentPlayer tp = tournamentPlayerRepository.save(TournamentPlayer.builder()
                .tournament(tournamentEntity)
                .user(playerUser)
                .playerName("Hardik Pandya")
                .registeredRole("All-Rounder")
                .status("Registered")
                .build());

        // 1. Invalid status string rejected by DTO pattern
        UpdatePlayerStatusRequest invalidReq = UpdatePlayerStatusRequest.builder()
                .status("Admin")
                .build();
        mockMvc.perform(patch("/api/v1/tournaments/" + tournamentId + "/players/" + tp.getId() + "/status")
                        .header("Authorization", "Bearer " + organizerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidReq)))
                .andExpect(status().isBadRequest());

        // 2. Cannot alter status of player who has already been sold
        tp.setStatus("Sold");
        tournamentPlayerRepository.save(tp);

        UpdatePlayerStatusRequest rejectSoldReq = UpdatePlayerStatusRequest.builder()
                .status("Rejected")
                .build();
        mockMvc.perform(patch("/api/v1/tournaments/" + tournamentId + "/players/" + tp.getId() + "/status")
                        .header("Authorization", "Bearer " + organizerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(rejectSoldReq)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code", is("CANNOT_CHANGE_STATUS")));
    }

    @Test
    @DisplayName("Security SEC-MOD6-03: Pagination limit rejects values over 100 preventing Heap DoS")
    void testListPlayersPaginationLimitValidation() throws Exception {
        // limit > 100 rejected with 400 Bad Request
        mockMvc.perform(get("/api/v1/tournaments/" + tournamentId + "/players?limit=101")
                        .header("Authorization", "Bearer " + organizerToken))
                .andExpect(status().isBadRequest());

        // limit < 1 rejected with 400 Bad Request
        mockMvc.perform(get("/api/v1/tournaments/" + tournamentId + "/players?limit=0")
                        .header("Authorization", "Bearer " + organizerToken))
                .andExpect(status().isBadRequest());

        // page < 1 rejected with 400 Bad Request
        mockMvc.perform(get("/api/v1/tournaments/" + tournamentId + "/players?page=0")
                        .header("Authorization", "Bearer " + organizerToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Security SEC-MOD6-04: CricHeroes link rejects javascript: XSS protocol URI")
    void testPlayerRegistrationRejectsMaliciousCricHeroesUri() throws Exception {
        PlayerRegistrationRequest maliciousReq = PlayerRegistrationRequest.builder()
                .playingRole("Batsman")
                .cricHeroesLink("javascript:alert(document.cookie)")
                .build();

        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/players/register")
                        .header("Authorization", "Bearer " + playerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(maliciousReq)))
                .andExpect(status().isBadRequest());

        // Valid https link succeeds
        PlayerRegistrationRequest validReq = PlayerRegistrationRequest.builder()
                .playingRole("Batsman")
                .cricHeroesLink("https://cricheroes.in/player/9912")
                .build();

        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/players/register")
                        .header("Authorization", "Bearer " + playerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validReq)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Security SEC-MOD6-05: Registration is blocked with 409 Conflict when tournament is ONGOING")
    void testRegistrationClosedForOngoingTournament() throws Exception {
        tournamentEntity.setStatus(TournamentStatus.ONGOING);
        tournamentRepository.save(tournamentEntity);

        PlayerRegistrationRequest req = PlayerRegistrationRequest.builder()
                .playingRole("Batsman")
                .build();

        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/players/register")
                        .header("Authorization", "Bearer " + playerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code", is("REGISTRATION_CLOSED")));
    }

    @Test
    @DisplayName("API 34: Auto-approval tournament setting gives registered player Approved status immediately")
    void testRegisterPlayer_AutoApprovalMode_SetsStatusApproved() throws Exception {
        // Update tournament settings to AUTO_APPROVAL
        TournamentSettingsRequest autoApprovalRules = TournamentSettingsRequest.builder()
                .teams(2)
                .squadMinimum(1)
                .squadMaximum(2)
                .budget(1000000L)
                .basePrice(5000L)
                .bidIncrement(1000L)
                .bidTimer(15)
                .playerApprovalMode("AUTO_APPROVAL")
                .build();
        tournamentService.saveSettings(UserPrincipal.create(organizerUser), tournamentId, autoApprovalRules);

        PlayerRegistrationRequest request = PlayerRegistrationRequest.builder()
                .playingRole("Batsman")
                .build();

        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/players/register")
                        .header("Authorization", "Bearer " + playerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.status", is("Approved")));

        TournamentPlayer tp = tournamentPlayerRepository.findByTournamentPublicIdAndUserId(tournamentId, playerUser.getId()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals("Approved", tp.getStatus());
    }

    @Test
    @DisplayName("API 34: Manual organizer approval setting gives registered player Registered status")
    void testRegisterPlayer_OrganizerApprovalMode_SetsStatusRegistered() throws Exception {
        // Update tournament settings to ORGANIZER_APPROVAL
        TournamentSettingsRequest manualRules = TournamentSettingsRequest.builder()
                .teams(2)
                .squadMinimum(1)
                .squadMaximum(2)
                .budget(1000000L)
                .basePrice(5000L)
                .bidIncrement(1000L)
                .bidTimer(15)
                .playerApprovalMode("ORGANIZER_APPROVAL")
                .build();
        tournamentService.saveSettings(UserPrincipal.create(organizerUser), tournamentId, manualRules);

        PlayerRegistrationRequest request = PlayerRegistrationRequest.builder()
                .playingRole("Bowler")
                .build();

        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/players/register")
                        .header("Authorization", "Bearer " + playerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.status", is("Registered")));

        TournamentPlayer tp = tournamentPlayerRepository.findByTournamentPublicIdAndUserId(tournamentId, playerUser.getId()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals("Registered", tp.getStatus());
    }

    @Test
    @DisplayName("Ponytail Review 1: Role breakdown stats accurately counts all-rounder and wicketkeeper with spaces or hyphens")
    void testRoleCountsMatchAlternateSpacingAndHyphens() throws Exception {
        // Player 1 with "All Rounder" (space)
        TournamentPlayer tp1 = TournamentPlayer.builder()
                .tournament(tournamentEntity)
                .user(playerUser)
                .playerName("Hardik Pandya")
                .registeredRole("All Rounder")
                .status("Registered")
                .build();
        tournamentPlayerRepository.save(tp1);

        // Player 2 with "Wicket-Keeper" (hyphen)
        User p2User = userRepository.save(User.builder()
                .fullName("Rishabh Pant")
                .email("pant_" + UUID.randomUUID() + "@cricauction.in")
                .mobile("+9191000" + UUID.randomUUID().toString().substring(0, 5))
                .isActive(true)
                .build());

        TournamentPlayer tp2 = TournamentPlayer.builder()
                .tournament(tournamentEntity)
                .user(p2User)
                .playerName("Rishabh Pant")
                .registeredRole("Wicket-Keeper")
                .status("Approved")
                .build();
        tournamentPlayerRepository.save(tp2);

        mockMvc.perform(get("/api/v1/tournaments/" + tournamentId + "/players/stats")
                        .header("Authorization", "Bearer " + playerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roleBreakdown.allRounders", is(1)))
                .andExpect(jsonPath("$.data.roleBreakdown.wicketKeepers", is(1)))
                .andExpect(jsonPath("$.data.registered", is(2)));
    }

    @Test
    @DisplayName("Ponytail Review 2: totalEnrolled counts Sold players and excludes Rejected players")
    void testTotalEnrolledIncludesSoldPlayers() throws Exception {
        TournamentPlayer soldPlayer = TournamentPlayer.builder()
                .tournament(tournamentEntity)
                .user(playerUser)
                .playerName("Virat Kohli")
                .registeredRole("Batsman")
                .status("Sold")
                .build();
        tournamentPlayerRepository.save(soldPlayer);

        User rejectedUser = userRepository.save(User.builder()
                .fullName("Rejected Candidate")
                .email("rej_" + UUID.randomUUID() + "@cricauction.in")
                .isActive(true)
                .build());

        TournamentPlayer rejectedPlayer = TournamentPlayer.builder()
                .tournament(tournamentEntity)
                .user(rejectedUser)
                .playerName("Rejected Candidate")
                .registeredRole("Bowler")
                .status("Rejected")
                .build();
        tournamentPlayerRepository.save(rejectedPlayer);

        mockMvc.perform(get("/api/v1/tournaments/" + tournamentId + "/players/stats")
                        .header("Authorization", "Bearer " + playerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.registered", is(1))); // 1 Sold player counted, 1 Rejected excluded
    }

    @Test
    @DisplayName("Ponytail Review 3: Withdrawal succeeds even if tournament is ONGOING (does not throw REGISTRATION_CLOSED)")
    void testWithdrawSucceedsOnOngoingTournament() throws Exception {
        // Player registers while UPCOMING
        TournamentPlayer tp = TournamentPlayer.builder()
                .tournament(tournamentEntity)
                .user(playerUser)
                .playerName("K L Rahul")
                .registeredRole("Wicket Keeper")
                .status("Registered")
                .build();
        tournamentPlayerRepository.save(tp);

        // Tournament transitions to ONGOING
        tournamentEntity.setStatus(TournamentStatus.ONGOING);
        tournamentRepository.save(tournamentEntity);

        // Player withdraws - should succeed with 200 OK
        mockMvc.perform(delete("/api/v1/tournaments/" + tournamentId + "/players/withdraw")
                        .header("Authorization", "Bearer " + playerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)));

        org.junit.jupiter.api.Assertions.assertTrue(
                tournamentPlayerRepository.findByTournamentPublicIdAndUserId(tournamentId, playerUser.getId()).isEmpty()
        );
    }
}
