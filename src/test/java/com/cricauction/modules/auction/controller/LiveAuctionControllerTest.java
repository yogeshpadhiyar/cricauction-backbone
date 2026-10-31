package com.cricauction.modules.auction.controller;

import com.cricauction.modules.auction.dto.request.AcceleratedRoundRequest;
import com.cricauction.modules.auction.dto.request.StartAuctionRequest;
import com.cricauction.modules.auction.strategy.BiddingStrategyType;
import com.cricauction.modules.auction.dto.request.NextPlayerRequest;
import com.cricauction.modules.auction.dto.request.PlaceBidRequest;
import com.cricauction.modules.auction.dto.request.WishlistActionRequest;
import com.cricauction.modules.auction.repository.AuctionBidRepository;
import com.cricauction.modules.auction.repository.CaptainWishlistRepository;
import com.cricauction.modules.auction.repository.LiveAuctionStateRepository;
import com.cricauction.modules.auth.entity.Role;
import com.cricauction.modules.auth.entity.User;
import com.cricauction.modules.auth.entity.enums.UserRole;
import com.cricauction.modules.auth.repository.RoleRepository;
import com.cricauction.modules.auth.repository.UserRepository;
import com.cricauction.modules.player.entity.TournamentPlayer;
import com.cricauction.modules.player.repository.TournamentPlayerRepository;
import com.cricauction.modules.team.entity.Team;
import com.cricauction.modules.team.repository.SquadMemberRepository;
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

import java.time.LocalDate;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LiveAuctionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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
    private TournamentPlayerRepository playerRepository;

    @Autowired
    private LiveAuctionStateRepository auctionStateRepository;

    @Autowired
    private AuctionBidRepository auctionBidRepository;

    @Autowired
    private CaptainWishlistRepository wishlistRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private User organizerUser;
    private User otherOrganizerUser;
    private User captainUser1;
    private User captainUser2;

    private String organizerToken;
    private String otherOrganizerToken;
    private String captain1Token;
    private String captain2Token;

    private String tournamentId;
    private Tournament tournamentEntity;
    private Team team1;
    private Team team2;
    private TournamentPlayer player1;
    private TournamentPlayer player2;

    @BeforeEach
    void setUp() {
        cleanAll();

        String suffix = UUID.randomUUID().toString().substring(0, 6);

        Role organizerRole = roleRepository.findByName(UserRole.ORGANIZER)
                .orElseGet(() -> roleRepository.save(Role.builder().name(UserRole.ORGANIZER).build()));
        Role captainRole = roleRepository.findByName(UserRole.CAPTAIN)
                .orElseGet(() -> roleRepository.save(Role.builder().name(UserRole.CAPTAIN).build()));
        Role playerRole = roleRepository.findByName(UserRole.PLAYER)
                .orElseGet(() -> roleRepository.save(Role.builder().name(UserRole.PLAYER).build()));

        organizerUser = userRepository.save(User.builder()
                .fullName("Rohit Sharma")
                .email("org." + suffix + "@cricauction.app")
                .mobile("+9198" + (int) (Math.random() * 89999999 + 10000000))
                .passwordHash("DummyHash123")
                .isActive(true)
                .build());
        organizerUser.addRole(organizerRole);
        organizerUser = userRepository.save(organizerUser);
        organizerToken = jwtTokenProvider.generateAccessToken(organizerUser);

        otherOrganizerUser = userRepository.save(User.builder()
                .fullName("Sourav Ganguly")
                .email("other." + suffix + "@cricauction.app")
                .mobile("+9197" + (int) (Math.random() * 89999999 + 10000000))
                .passwordHash("DummyHash123")
                .isActive(true)
                .build());
        otherOrganizerUser.addRole(organizerRole);
        otherOrganizerUser = userRepository.save(otherOrganizerUser);
        otherOrganizerToken = jwtTokenProvider.generateAccessToken(otherOrganizerUser);

        captainUser1 = userRepository.save(User.builder()
                .fullName("MS Dhoni")
                .email("dhoni." + suffix + "@cricauction.app")
                .mobile("+9196" + (int) (Math.random() * 89999999 + 10000000))
                .passwordHash("DummyHash123")
                .isActive(true)
                .build());
        captainUser1.addRole(captainRole);
        captainUser1 = userRepository.save(captainUser1);
        captain1Token = jwtTokenProvider.generateAccessToken(captainUser1);

        captainUser2 = userRepository.save(User.builder()
                .fullName("Virat Kohli")
                .email("kohli." + suffix + "@cricauction.app")
                .mobile("+9195" + (int) (Math.random() * 89999999 + 10000000))
                .passwordHash("DummyHash123")
                .isActive(true)
                .build());
        captainUser2.addRole(captainRole);
        captainUser2 = userRepository.save(captainUser2);
        captain2Token = jwtTokenProvider.generateAccessToken(captainUser2);

        // Create Tournament
        CreateTournamentRequest createReq = CreateTournamentRequest.builder()
                .name("Ahmedabad Premier League " + suffix)
                .location("Ahmedabad")
                .ground("Narendra Modi Stadium")
                .startDate(LocalDate.now().plusDays(10))
                .endDate(LocalDate.now().plusDays(25))
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
                .name("Chennai Super Kings")
                .captainName("MS Dhoni")
                .captainMobile(captainUser1.getMobile())
                .captainId(captainUser1.getId())
                .initialBudget(1000000L)
                .remainingBudget(1000000L)
                .status("Approved")
                .inviteCode("CSK-" + suffix)
                .build());

        team2 = teamRepository.save(Team.builder()
                .tournament(tournamentEntity)
                .name("Royal Challengers")
                .captainName("Virat Kohli")
                .captainMobile(captainUser2.getMobile())
                .captainId(captainUser2.getId())
                .initialBudget(1000000L)
                .remainingBudget(1000000L)
                .status("Approved")
                .inviteCode("RCB-" + suffix)
                .build());

        // Create Players
        player1 = playerRepository.save(TournamentPlayer.builder()
                .tournament(tournamentEntity)
                .user(captainUser1)
                .playerName("Jasprit Bumrah")
                .registeredRole("Bowler")
                .battingStyle("Right-hand bat")
                .bowlingStyle("Right-arm fast")
                .basePrice(10000L)
                .status("Approved")
                .build());

        player2 = playerRepository.save(TournamentPlayer.builder()
                .tournament(tournamentEntity)
                .user(captainUser2)
                .playerName("Hardik Pandya")
                .registeredRole("All-Rounder")
                .battingStyle("Right-hand bat")
                .bowlingStyle("Right-arm medium-fast")
                .basePrice(15000L)
                .status("Approved")
                .build());
    }

    @AfterEach
    void tearDown() {
        cleanAll();
    }

    private void cleanAll() {
        wishlistRepository.deleteAll();
        auctionBidRepository.deleteAll();
        auctionStateRepository.deleteAll();
        squadMemberRepository.deleteAll();
        playerRepository.deleteAll();
        teamRepository.deleteAll();
        settingsRepository.deleteAll();
        tournamentRepository.deleteAll();
    }

    @Test
    @DisplayName("API 41: Organizer starts auction room successfully")
    void testStartAuction_Success() throws Exception {
        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/auction/start")
                        .header("Authorization", "Bearer " + organizerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.status", is("LIVE")))
                .andExpect(jsonPath("$.data.auctionId", notNullValue()));
    }

    @Test
    @DisplayName("API 41: Other organizer cannot start someone else's auction room")
    void testStartAuction_ForbiddenForOtherOrganizer() throws Exception {
        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/auction/start")
                        .header("Authorization", "Bearer " + otherOrganizerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.error.code", is("TOURNAMENT_ACCESS_DENIED")));
    }

    @Test
    @DisplayName("API 42 & 43: Organizer pauses and resumes auction")
    void testPauseAndResumeAuction_Success() throws Exception {
        // Start auction
        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/auction/start")
                .header("Authorization", "Bearer " + organizerToken));

        // Pause
        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/auction/pause")
                        .header("Authorization", "Bearer " + organizerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", is("Auction paused")));

        // Resume
        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/auction/resume")
                        .header("Authorization", "Bearer " + organizerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", is("Auction resumed")));
    }

    @Test
    @DisplayName("API 44: Organizer calls next eligible player to the hammer")
    void testCallNextPlayer_Success() throws Exception {
        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/auction/start")
                .header("Authorization", "Bearer " + organizerToken));

        NextPlayerRequest req = NextPlayerRequest.builder()
                .playerId(player1.getId())
                .build();

        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/auction/next-player")
                        .header("Authorization", "Bearer " + organizerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.name", is("Jasprit Bumrah")))
                .andExpect(jsonPath("$.data.basePrice", is(10000)))
                .andExpect(jsonPath("$.data.currentBid", is(10000)));
    }

    @Test
    @DisplayName("API 45: Captain places valid opening bid")
    void testPlaceBid_Success() throws Exception {
        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/auction/start")
                .header("Authorization", "Bearer " + organizerToken));

        NextPlayerRequest nextReq = NextPlayerRequest.builder()
                .playerId(player1.getId())
                .build();
        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/auction/next-player")
                .header("Authorization", "Bearer " + organizerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(nextReq)));

        // Place opening bid (10,000)
        PlaceBidRequest bidReq = PlaceBidRequest.builder()
                .teamId(team1.getId())
                .bidAmount(10000L)
                .build();

        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/auction/bid")
                        .header("Authorization", "Bearer " + captain1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bidReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.currentBid", is(10000)))
                .andExpect(jsonPath("$.data.leadingTeamId", is(team1.getId().intValue())));
    }

    @Test
    @DisplayName("API 45: Captain cannot outbid themselves consecutively")
    void testPlaceBid_CannotOutbidSelf() throws Exception {
        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/auction/start")
                .header("Authorization", "Bearer " + organizerToken));

        NextPlayerRequest nextReq = NextPlayerRequest.builder().playerId(player1.getId()).build();
        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/auction/next-player")
                .header("Authorization", "Bearer " + organizerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(nextReq)));

        PlaceBidRequest bid1 = PlaceBidRequest.builder().teamId(team1.getId()).bidAmount(10000L).build();
        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/auction/bid")
                .header("Authorization", "Bearer " + captain1Token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(bid1)));

        // Attempting to outbid own team
        PlaceBidRequest bid2 = PlaceBidRequest.builder().teamId(team1.getId()).bidAmount(11000L).build();
        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/auction/bid")
                        .header("Authorization", "Bearer " + captain1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bid2)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code", is("CANNOT_OUTBID_SELF")));
    }

    @Test
    @DisplayName("API 45: Bid rejected if below current bid plus increment")
    void testPlaceBid_BidTooLow() throws Exception {
        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/auction/start")
                .header("Authorization", "Bearer " + organizerToken));

        NextPlayerRequest nextReq = NextPlayerRequest.builder().playerId(player1.getId()).build();
        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/auction/next-player")
                .header("Authorization", "Bearer " + organizerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(nextReq)));

        // CSK bids 10000
        PlaceBidRequest bid1 = PlaceBidRequest.builder().teamId(team1.getId()).bidAmount(10000L).build();
        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/auction/bid")
                .header("Authorization", "Bearer " + captain1Token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(bid1)));

        // RCB tries to bid 10500 (Increment is 1000, so min is 11000)
        PlaceBidRequest bid2 = PlaceBidRequest.builder().teamId(team2.getId()).bidAmount(10500L).build();
        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/auction/bid")
                        .header("Authorization", "Bearer " + captain2Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bid2)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code", is("BID_TOO_LOW")));
    }

    @Test
    @DisplayName("API 45: Bid rejected if team has insufficient remaining purse")
    void testPlaceBid_InsufficientPurse() throws Exception {
        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/auction/start")
                .header("Authorization", "Bearer " + organizerToken));

        NextPlayerRequest nextReq = NextPlayerRequest.builder().playerId(player1.getId()).build();
        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/auction/next-player")
                .header("Authorization", "Bearer " + organizerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(nextReq)));

        // Bid higher than remaining purse (1,000,000)
        PlaceBidRequest highBid = PlaceBidRequest.builder().teamId(team1.getId()).bidAmount(1500000L).build();
        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/auction/bid")
                        .header("Authorization", "Bearer " + captain1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(highBid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code", is("INSUFFICIENT_PURSE")));
    }

    @Test
    @DisplayName("API 46: Hammer sold deducts winning team purse and adds to squad")
    void testSellPlayer_Success() throws Exception {
        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/auction/start")
                .header("Authorization", "Bearer " + organizerToken));

        NextPlayerRequest nextReq = NextPlayerRequest.builder().playerId(player1.getId()).build();
        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/auction/next-player")
                .header("Authorization", "Bearer " + organizerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(nextReq)));

        PlaceBidRequest bid = PlaceBidRequest.builder().teamId(team1.getId()).bidAmount(25000L).build();
        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/auction/bid")
                .header("Authorization", "Bearer " + captain1Token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(bid)));

        // Hammer SOLD
        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/auction/sell")
                        .header("Authorization", "Bearer " + organizerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.finalPrice", is(25000)))
                .andExpect(jsonPath("$.data.soldToTeamId", is(team1.getId().intValue())))
                .andExpect(jsonPath("$.data.remainingPurse", is(975000)));

        // Verify player is now Sold
        TournamentPlayer soldPlayer = playerRepository.findById(player1.getId()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals("Sold", soldPlayer.getStatus());

        // Verify team budget deducted
        Team updatedTeam = teamRepository.findById(team1.getId()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(975000L, updatedTeam.getRemainingBudget());
    }

    @Test
    @DisplayName("API 47: Hammer pass marks player unsold and resets hammer")
    void testPassPlayer_Success() throws Exception {
        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/auction/start")
                .header("Authorization", "Bearer " + organizerToken));

        NextPlayerRequest nextReq = NextPlayerRequest.builder().playerId(player1.getId()).build();
        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/auction/next-player")
                .header("Authorization", "Bearer " + organizerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(nextReq)));

        // Organizer passes player
        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/auction/pass")
                        .header("Authorization", "Bearer " + organizerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.status", is("UNSOLD")));

        TournamentPlayer unsoldPlayer = playerRepository.findById(player1.getId()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals("Unsold", unsoldPlayer.getStatus());
    }

    @Test
    @DisplayName("API 48: Organizer undos mistaken bid and reverts to previous leading team")
    void testUndoLastBid_Success() throws Exception {
        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/auction/start")
                .header("Authorization", "Bearer " + organizerToken));

        NextPlayerRequest nextReq = NextPlayerRequest.builder().playerId(player1.getId()).build();
        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/auction/next-player")
                .header("Authorization", "Bearer " + organizerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(nextReq)));

        // CSK: 10,000
        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/auction/bid")
                .header("Authorization", "Bearer " + captain1Token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(PlaceBidRequest.builder().teamId(team1.getId()).bidAmount(10000L).build())));

        // RCB: 12,000
        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/auction/bid")
                .header("Authorization", "Bearer " + captain2Token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(PlaceBidRequest.builder().teamId(team2.getId()).bidAmount(12000L).build())));

        // Undo RCB bid
        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/auction/undo")
                        .header("Authorization", "Bearer " + organizerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.currentBid", is(10000)))
                .andExpect(jsonPath("$.data.leadingTeamId", is(team1.getId().intValue())));
    }

    @Test
    @DisplayName("API 49: Get state returns real-time snapshot with teams and upcoming count")
    void testGetAuctionState_Success() throws Exception {
        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/auction/start")
                .header("Authorization", "Bearer " + organizerToken));

        mockMvc.perform(get("/api/v1/tournaments/" + tournamentId + "/auction/state")
                        .header("Authorization", "Bearer " + organizerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.status", is("LIVE")))
                .andExpect(jsonPath("$.data.teams", hasSize(2)))
                .andExpect(jsonPath("$.data.upcomingQueueCount", is(2)));
    }

    @Test
    @DisplayName("API 50: Get auction history returns sold & unsold events")
    void testGetAuctionHistory_Success() throws Exception {
        mockMvc.perform(get("/api/v1/tournaments/" + tournamentId + "/auction/history")
                        .header("Authorization", "Bearer " + organizerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data", notNullValue()));
    }

    @Test
    @DisplayName("API 51: Accelerated round resets unsold players with discounted base price")
    void testStartAcceleratedRound_Success() throws Exception {
        player1.setStatus("Unsold");
        playerRepository.save(player1);

        AcceleratedRoundRequest req = AcceleratedRoundRequest.builder()
                .includeUnsold(true)
                .revisedBasePriceFactor(0.8)
                .build();

        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/auction/accelerated-round")
                        .header("Authorization", "Bearer " + organizerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.message", containsString("Accelerated round prepared")));

        TournamentPlayer updated = playerRepository.findById(player1.getId()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals("Approved", updated.getStatus());
        org.junit.jupiter.api.Assertions.assertEquals(8000L, updated.getBasePrice()); // 10000 * 0.8 = 8000
    }

    @Test
    @DisplayName("API 52 & 53: Captain adds, views, and removes players in wishlist")
    void testCaptainWishlistOperations_Success() throws Exception {
        WishlistActionRequest addReq = WishlistActionRequest.builder()
                .playerId(player1.getId())
                .action("add")
                .maxBudget(75000L)
                .priority("High")
                .build();

        // 1. Add to wishlist
        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/auction/wishlist")
                        .header("Authorization", "Bearer " + captain1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(addReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", is("Wishlist updated")));

        // 2. View wishlist
        mockMvc.perform(get("/api/v1/tournaments/" + tournamentId + "/auction/wishlist")
                        .header("Authorization", "Bearer " + captain1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].playerName", is("Jasprit Bumrah")))
                .andExpect(jsonPath("$.data[0].maxBudget", is(75000)))
                .andExpect(jsonPath("$.data[0].priority", is("High")));

        // 3. Remove from wishlist
        WishlistActionRequest removeReq = WishlistActionRequest.builder()
                .playerId(player1.getId())
                .action("remove")
                .build();

        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/auction/wishlist")
                        .header("Authorization", "Bearer " + captain1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(removeReq)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/tournaments/" + tournamentId + "/auction/wishlist")
                        .header("Authorization", "Bearer " + captain1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    @DisplayName("STRATEGY 1: Percentage increment auto-calculates and enforces minimum percentage on next bid")
    void testPercentageIncrementBiddingStrategy_Success() throws Exception {
        // 1. Organizer starts auction with PERCENTAGE_INCREMENT (10%)
        StartAuctionRequest startReq = StartAuctionRequest.builder()
                .bidStrategy(BiddingStrategyType.PERCENTAGE_INCREMENT)
                .incrementPercentage(10.0)
                .build();

        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/auction/start")
                        .header("Authorization", "Bearer " + organizerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(startReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bidStrategy", is("percentage_increment")))
                .andExpect(jsonPath("$.data.incrementPercentage", is(10.0)));

        // 2. Call player1 (basePrice 10000)
        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/auction/next-player")
                        .header("Authorization", "Bearer " + organizerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(NextPlayerRequest.builder().playerId(player1.getId()).build())))
                .andExpect(status().isOk());

        // 3. Opening bid by Captain 1 at base price 10000
        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/auction/bid")
                        .header("Authorization", "Bearer " + captain1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(PlaceBidRequest.builder().teamId(team1.getId()).bidAmount(10000L).build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentBid", is(10000)))
                .andExpect(jsonPath("$.data.strategyApplied", containsString("Opening")));

        // 4. Captain 2 attempts bid below required 10% increment (10000 + 1000 = 11000) -> 10500 should fail
        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/auction/bid")
                        .header("Authorization", "Bearer " + captain2Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(PlaceBidRequest.builder().teamId(team2.getId()).bidAmount(10500L).build())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code", is("BID_TOO_LOW")));

        // 5. Captain 2 places auto-increment bid (null bidAmount) -> automatically calculates 10% increment (11000)
        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/auction/bid")
                        .header("Authorization", "Bearer " + captain2Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(PlaceBidRequest.builder().teamId(team2.getId()).build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentBid", is(11000)))
                .andExpect(jsonPath("$.data.incrementApplied", is(1000)))
                .andExpect(jsonPath("$.data.strategyApplied", containsString("10.0%")));

        // 6. Next minimum increment on 11000 is 10% = 1100 (total 12100). Captain 1 bids with additionalAmount = 1500 -> accepted at 12500
        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/auction/bid")
                        .header("Authorization", "Bearer " + captain1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(PlaceBidRequest.builder().teamId(team1.getId()).additionalAmount(1500L).build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentBid", is(12500)))
                .andExpect(jsonPath("$.data.incrementApplied", is(1500)));
    }

    @Test
    @DisplayName("STRATEGY 2: Additional amount bidding increases current price by captain subject to min increment")
    void testAdditionalAmountBiddingStrategy_Success() throws Exception {
        // 1. Organizer starts auction with ADDITIONAL_AMOUNT and minBidIncrement = 5000
        StartAuctionRequest startReq = StartAuctionRequest.builder()
                .bidStrategy(BiddingStrategyType.ADDITIONAL_AMOUNT)
                .minBidIncrement(5000L)
                .build();

        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/auction/start")
                        .header("Authorization", "Bearer " + organizerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(startReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bidStrategy", is("additional_amount")))
                .andExpect(jsonPath("$.data.minBidIncrement", is(5000)));

        // 2. Call player1 (basePrice 10000)
        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/auction/next-player")
                        .header("Authorization", "Bearer " + organizerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(NextPlayerRequest.builder().playerId(player1.getId()).build())))
                .andExpect(status().isOk());

        // 3. Opening bid by Captain 1 at base price 10000
        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/auction/bid")
                        .header("Authorization", "Bearer " + captain1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(PlaceBidRequest.builder().teamId(team1.getId()).bidAmount(10000L).build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentBid", is(10000)));

        // 4. Captain 2 attempts additionalAmount = 2000 (which is less than min increment 5000) -> 400
        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/auction/bid")
                        .header("Authorization", "Bearer " + captain2Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(PlaceBidRequest.builder().teamId(team2.getId()).additionalAmount(2000L).build())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code", is("ADDITIONAL_BID_TOO_LOW")));

        // 5. Captain 2 bids with additionalAmount = 5000 -> accepted at 15000
        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/auction/bid")
                        .header("Authorization", "Bearer " + captain2Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(PlaceBidRequest.builder().teamId(team2.getId()).additionalAmount(5000L).build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentBid", is(15000)))
                .andExpect(jsonPath("$.data.incrementApplied", is(5000)));

        // 6. Captain 1 bids with additionalAmount = 10000 -> accepted at 25000
        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/auction/bid")
                        .header("Authorization", "Bearer " + captain1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(PlaceBidRequest.builder().teamId(team1.getId()).additionalAmount(10000L).build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentBid", is(25000)))
                .andExpect(jsonPath("$.data.incrementApplied", is(10000)));
    }

    @Test
    @DisplayName("PONYTAIL FIX 1: Calling next player while active bids exist rejects with 409 Conflict")
    void testCallNextPlayer_PlayerHasActiveBids_Conflict() throws Exception {
        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/auction/start")
                .header("Authorization", "Bearer " + organizerToken));

        // Call player1
        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/auction/next-player")
                .header("Authorization", "Bearer " + organizerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(NextPlayerRequest.builder().playerId(player1.getId()).build())))
                .andExpect(status().isOk());

        // Place active bid
        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/auction/bid")
                .header("Authorization", "Bearer " + captain1Token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(PlaceBidRequest.builder().teamId(team1.getId()).bidAmount(10000L).build())))
                .andExpect(status().isOk());

        // Attempting to call player2 without sell or pass must be rejected with 409 Conflict
        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/auction/next-player")
                .header("Authorization", "Bearer " + organizerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(NextPlayerRequest.builder().playerId(player2.getId()).build())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code", is("PLAYER_HAS_ACTIVE_BIDS")));
    }

    @Test
    @DisplayName("PONYTAIL FIX 1: Calling next player when previous player had zero bids automatically marks them Unsold")
    void testCallNextPlayer_PreviousUnsoldAutoMarked() throws Exception {
        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/auction/start")
                .header("Authorization", "Bearer " + organizerToken));

        // Call player1 (no bids placed)
        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/auction/next-player")
                .header("Authorization", "Bearer " + organizerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(NextPlayerRequest.builder().playerId(player1.getId()).build())))
                .andExpect(status().isOk());

        // Call player2
        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/auction/next-player")
                .header("Authorization", "Bearer " + organizerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(NextPlayerRequest.builder().playerId(player2.getId()).build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name", is("Hardik Pandya")));

        // Verify player1 is safely marked Unsold instead of stuck in UnderHammer
        TournamentPlayer prev = playerRepository.findById(player1.getId()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals("Unsold", prev.getStatus());
    }

    @Test
    @DisplayName("PONYTAIL FIX 2: Undoing opening bid on player with null base price reverts to tournament defaultBasePrice")
    void testUndoOpeningBid_RevertsToTournamentDefaultBasePrice() throws Exception {
        User playerUser3 = userRepository.save(User.builder()
                .fullName("Ravindra Jadeja")
                .email("jadeja." + UUID.randomUUID() + "@cricauction.app")
                .mobile("+9197" + (int) (Math.random() * 89999999 + 10000000))
                .passwordHash("DummyHash123")
                .isActive(true)
                .build());

        TournamentPlayer playerNoBase = playerRepository.save(TournamentPlayer.builder()
                .tournament(tournamentEntity)
                .user(playerUser3)
                .playerName("Ravindra Jadeja")
                .registeredRole("All-Rounder")
                .battingStyle("Left-hand bat")
                .bowlingStyle("Left-arm orthodox")
                .basePrice(null)
                .status("Approved")
                .build());

        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/auction/start")
                .header("Authorization", "Bearer " + organizerToken));

        // Call playerNoBase to hammer (settings.defaultBasePrice = 10000)
        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/auction/next-player")
                .header("Authorization", "Bearer " + organizerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(NextPlayerRequest.builder().playerId(playerNoBase.getId()).build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentBid", is(10000)));

        // Place opening bid
        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/auction/bid")
                .header("Authorization", "Bearer " + captain1Token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(PlaceBidRequest.builder().teamId(team1.getId()).bidAmount(10000L).build())))
                .andExpect(status().isOk());

        // Undo opening bid
        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/auction/undo")
                .header("Authorization", "Bearer " + organizerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentBid", is(10000)))
                .andExpect(jsonPath("$.data.leadingTeamId", nullValue()));
    }

    @Test
    @DisplayName("SEC-01: Validation rejects invalid revisedBasePriceFactor (< 0.01 or > 2.0)")
    void testAcceleratedRound_InvalidFactor_Rejected() throws Exception {
        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/auction/start")
                .header("Authorization", "Bearer " + organizerToken));

        // Factor > 2.0
        AcceleratedRoundRequest tooHigh = AcceleratedRoundRequest.builder()
                .revisedBasePriceFactor(2.5)
                .build();
        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/auction/accelerated-round")
                .header("Authorization", "Bearer " + organizerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(tooHigh)))
                .andExpect(status().isBadRequest());

        // Factor < 0.01
        AcceleratedRoundRequest tooLow = AcceleratedRoundRequest.builder()
                .revisedBasePriceFactor(0.005)
                .build();
        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/auction/accelerated-round")
                .header("Authorization", "Bearer " + organizerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(tooLow)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("SEC-02: Validation rejects invalid wishlist action")
    void testCaptainWishlist_InvalidAction_Rejected() throws Exception {
        WishlistActionRequest invalidReq = WishlistActionRequest.builder()
                .playerId(player1.getId())
                .action("PURGE_ALL_DATA")
                .build();

        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/auction/wishlist")
                .header("Authorization", "Bearer " + captain1Token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidReq)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("SEC-03: Validation rejects non-positive playerId when calling next player")
    void testCallNextPlayer_NonPositiveId_Rejected() throws Exception {
        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/auction/start")
                .header("Authorization", "Bearer " + organizerToken));

        NextPlayerRequest invalidReq = NextPlayerRequest.builder()
                .playerId(-10L)
                .build();

        mockMvc.perform(post("/api/v1/tournaments/" + tournamentId + "/auction/next-player")
                .header("Authorization", "Bearer " + organizerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidReq)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("SEC-04: Path variable validation rejects overly long tournament ID (> 100 chars)")
    void testLiveAuction_OverlyLongTournamentId_Rejected() throws Exception {
        String longTournamentId = "t".repeat(101);

        mockMvc.perform(get("/api/v1/tournaments/" + longTournamentId + "/auction/state")
                .header("Authorization", "Bearer " + organizerToken))
                .andExpect(status().isBadRequest());
    }
}
