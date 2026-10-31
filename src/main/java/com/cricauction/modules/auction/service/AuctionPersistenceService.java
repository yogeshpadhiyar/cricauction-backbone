package com.cricauction.modules.auction.service;

import com.cricauction.common.exception.BadRequestException;
import com.cricauction.common.exception.ConflictException;
import com.cricauction.modules.auction.dto.request.PlaceBidRequest;
import com.cricauction.modules.auction.dto.request.StartAuctionRequest;
import com.cricauction.modules.auction.dto.response.*;
import com.cricauction.modules.auction.entity.AuctionBid;
import com.cricauction.modules.auction.entity.CaptainWishlist;
import com.cricauction.modules.auction.entity.LiveAuctionState;
import com.cricauction.modules.auction.entity.enums.AuctionStatus;
import com.cricauction.modules.auction.repository.AuctionBidRepository;
import com.cricauction.modules.auction.repository.CaptainWishlistRepository;
import com.cricauction.modules.auction.repository.LiveAuctionStateRepository;
import com.cricauction.modules.auction.strategy.BidCalculationResult;
import com.cricauction.modules.auction.strategy.BidIncrementStrategy;
import com.cricauction.modules.auction.strategy.BidStrategyResolver;
import com.cricauction.modules.auction.strategy.BiddingStrategyType;
import com.cricauction.modules.player.entity.TournamentPlayer;
import com.cricauction.modules.player.repository.TournamentPlayerRepository;
import com.cricauction.modules.team.entity.SquadMember;
import com.cricauction.modules.team.entity.Team;
import com.cricauction.modules.team.repository.SquadMemberRepository;
import com.cricauction.modules.team.repository.TeamRepository;
import com.cricauction.modules.tournament.entity.Tournament;
import com.cricauction.modules.tournament.entity.TournamentSettings;
import com.cricauction.modules.tournament.entity.enums.TournamentStatus;
import com.cricauction.modules.tournament.repository.TournamentRepository;
import com.cricauction.modules.tournament.repository.TournamentSettingsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Dedicated Mutation & Concurrency Service for Live Auction Engine.
 * All mutations execute on CricAuction-Master-Pool with PostgreSQL Advisory Locks.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AuctionPersistenceService {

    private final JdbcTemplate jdbcTemplate;
    private final TournamentRepository tournamentRepository;
    private final TournamentSettingsRepository settingsRepository;
    private final LiveAuctionStateRepository auctionStateRepository;
    private final AuctionBidRepository auctionBidRepository;
    private final CaptainWishlistRepository wishlistRepository;
    private final TeamRepository teamRepository;
    private final TournamentPlayerRepository playerRepository;
    private final SquadMemberRepository squadMemberRepository;
    private final BidStrategyResolver bidStrategyResolver;

    /**
     * Serializes concurrent operations per tournament using PostgreSQL Transaction Advisory Locks.
     * Prevents race conditions, double bidding, or purse overdrawing under high concurrency.
     */
    public void acquireTournamentAdvisoryLock(Long tournamentId) {
        try {
            jdbcTemplate.query("SELECT pg_advisory_xact_lock(?)", ps -> ps.setLong(1, tournamentId), rs -> null);
        } catch (BadSqlGrammarException ex) {
            // Graceful fallback for H2 in-memory test database where PostgreSQL dialect functions don't exist
            log.debug("pg_advisory_xact_lock bypassed on non-PostgreSQL database: {}", ex.getMessage());
        }
    }

    public AuctionStartResponse startAuction(Tournament tournament, StartAuctionRequest request) {
        acquireTournamentAdvisoryLock(tournament.getId());

        tournament.setStatus(TournamentStatus.AUCTION_LIVE);
        tournamentRepository.save(tournament);

        Instant now = Instant.now();
        LiveAuctionState state = auctionStateRepository.findByTournamentId(tournament.getId())
                .orElseGet(() -> LiveAuctionState.builder()
                        .tournamentId(tournament.getId())
                        .build());

        state.setStatus(AuctionStatus.LIVE);
        state.setIsPaused(false);
        state.setStartedAt(now);

        TournamentSettings settings = settingsRepository.findById(tournament.getId()).orElse(null);
        long defaultIncrement = (settings != null && settings.getBidIncrement() > 0)
                ? settings.getBidIncrement()
                : 1000L;

        if (request != null) {
            state.setBidStrategy(request.getBidStrategy() != null ? request.getBidStrategy() : BiddingStrategyType.ADDITIONAL_AMOUNT);
            state.setIncrementPercentage(request.getIncrementPercentage() != null ? request.getIncrementPercentage() : 10.0);
            state.setMinBidIncrement(request.getMinBidIncrement() != null ? request.getMinBidIncrement() : defaultIncrement);
        } else {
            if (state.getBidStrategy() == null) {
                state.setBidStrategy(BiddingStrategyType.ADDITIONAL_AMOUNT);
            }
            if (state.getIncrementPercentage() == null) {
                state.setIncrementPercentage(10.0);
            }
            if (state.getMinBidIncrement() == null) {
                state.setMinBidIncrement(defaultIncrement);
            }
        }

        state = auctionStateRepository.save(state);

        log.info("Master micro-write: Auction room LIVE for Tournament [{}] with strategy [{}] (Percentage: {}%, MinIncrement: ₹{})",
                tournament.getPublicId(), state.getBidStrategy(), state.getIncrementPercentage(), state.getMinBidIncrement());

        return AuctionStartResponse.builder()
                .auctionId("auc_" + tournament.getId())
                .status("LIVE")
                .startedAt(now)
                .bidStrategy(state.getBidStrategy())
                .incrementPercentage(state.getIncrementPercentage())
                .minBidIncrement(state.getMinBidIncrement())
                .build();
    }

    public void pauseAuction(Tournament tournament) {
        acquireTournamentAdvisoryLock(tournament.getId());

        LiveAuctionState state = auctionStateRepository.findByTournamentId(tournament.getId())
                .orElseThrow(() -> new ConflictException("AUCTION_NOT_INITIALIZED", "Auction room has not been initialized."));

        state.setIsPaused(true);
        state.setStatus(AuctionStatus.PAUSED);
        auctionStateRepository.save(state);
        log.info("Master micro-write: Auction PAUSED for Tournament [{}]", tournament.getPublicId());
    }

    public void resumeAuction(Tournament tournament) {
        acquireTournamentAdvisoryLock(tournament.getId());

        LiveAuctionState state = auctionStateRepository.findByTournamentId(tournament.getId())
                .orElseThrow(() -> new ConflictException("AUCTION_NOT_INITIALIZED", "Auction room has not been initialized."));

        state.setIsPaused(false);
        state.setStatus(AuctionStatus.LIVE);
        auctionStateRepository.save(state);
        log.info("Master micro-write: Auction RESUMED for Tournament [{}]", tournament.getPublicId());
    }

    public CurrentPlayerDto callNextPlayer(Tournament tournament, TournamentPlayer player) {
        acquireTournamentAdvisoryLock(tournament.getId());

        LiveAuctionState state = auctionStateRepository.findByTournamentId(tournament.getId())
                .orElseGet(() -> LiveAuctionState.builder()
                        .tournamentId(tournament.getId())
                        .status(AuctionStatus.LIVE)
                        .build());

        TournamentSettings settings = settingsRepository.findById(tournament.getId()).orElse(null);
        int timerSeconds = (settings != null && settings.getBidTimerSeconds() > 0)
                ? settings.getBidTimerSeconds()
                : 10;

        if (state.getCurrentPlayer() != null) {
            if (state.getLeadingTeam() != null) {
                throw new ConflictException("PLAYER_HAS_ACTIVE_BIDS",
                        "Cannot call next player: Player '" + state.getCurrentPlayer().getPlayerName() + "' has active bids. Please sell or pass them first.");
            }
            if (!state.getCurrentPlayer().getId().equals(player.getId())) {
                TournamentPlayer previousPlayer = state.getCurrentPlayer();
                previousPlayer.setStatus("Unsold");
                playerRepository.save(previousPlayer);
            }
        }

        player.setStatus("UnderHammer");
        playerRepository.save(player);

        Long basePrice = (player.getBasePrice() != null && player.getBasePrice() > 0)
                ? player.getBasePrice()
                : ((settings != null && settings.getDefaultBasePrice() > 0) ? settings.getDefaultBasePrice() : 5000L);

        state.setCurrentPlayer(player);
        state.setCurrentBid(basePrice);
        state.setLeadingTeam(null);
        state.setTimerSeconds(timerSeconds);
        state.setGoingTwiceWarning(0);
        auctionStateRepository.save(state);

        log.info("Master micro-write: Next player [{}] (ID {}) called to hammer in Tournament [{}]",
                player.getPlayerName(), player.getId(), tournament.getPublicId());

        return CurrentPlayerDto.builder()
                .id(player.getId())
                .name(player.getPlayerName())
                .role(player.getRegisteredRole())
                .battingStyle(player.getBattingStyle())
                .bowlingStyle(player.getBowlingStyle())
                .basePrice(basePrice)
                .currentBid(basePrice)
                .leadingTeamId(null)
                .leadingTeam(null)
                .secondsRemaining(timerSeconds)
                .photo(player.getPhotoUrl())
                .build();
    }

    public BidResponse placeBid(Tournament tournament, Team team, PlaceBidRequest request) {
        acquireTournamentAdvisoryLock(tournament.getId());

        LiveAuctionState state = auctionStateRepository.findByTournamentId(tournament.getId())
                .orElseThrow(() -> new ConflictException("AUCTION_NOT_INITIALIZED", "Auction room is not open."));

        if (state.getStatus() != AuctionStatus.LIVE || Boolean.TRUE.equals(state.getIsPaused())) {
            throw new ConflictException("AUCTION_NOT_LIVE", "Bids cannot be placed while auction is paused or not LIVE.");
        }

        if (state.getCurrentPlayer() == null) {
            throw new BadRequestException("NO_PLAYER_ON_HAMMER", "No player is currently on the auction hammer.");
        }

        // Prevent self-outbidding
        if (state.getLeadingTeam() != null && state.getLeadingTeam().getId().equals(team.getId())) {
            throw new ConflictException("CANNOT_OUTBID_SELF", "Team '" + team.getName() + "' already holds the leading bid.");
        }

        TournamentSettings settings = settingsRepository.findById(tournament.getId()).orElse(null);

        // Execute dynamic Strategy calculation & validation
        BidIncrementStrategy strategy = bidStrategyResolver.resolve(state.getBidStrategy());
        BidCalculationResult calcResult = strategy.calculateAndValidateBid(state, request, settings);
        Long finalBidAmount = calcResult.getFinalBidAmount();

        // Validate team purse
        if (team.getRemainingBudget() < finalBidAmount) {
            throw new BadRequestException("INSUFFICIENT_PURSE", "Team '" + team.getName() + "' has insufficient remaining budget (₹" + team.getRemainingBudget() + ") for bid of ₹" + finalBidAmount + ".");
        }

        // Validate squad capacity
        long currentSquadSize = squadMemberRepository.countByTeamId(team.getId());
        int maxSquad = settings != null ? settings.getSquadMaximum() : 15;
        if (currentSquadSize >= maxSquad) {
            throw new ConflictException("SQUAD_FULL", "Team squad is already full (" + maxSquad + " players maximum).");
        }

        int timerReset = (settings != null && settings.getBidTimerSeconds() > 0)
                ? settings.getBidTimerSeconds()
                : 10;

        state.setCurrentBid(finalBidAmount);
        state.setLeadingTeam(team);
        state.setTimerSeconds(timerReset);
        state.setGoingTwiceWarning(0);
        auctionStateRepository.save(state);

        int bidCount = auctionBidRepository.countBidsForPlayer(tournament.getId(), state.getCurrentPlayer().getId()) + 1;
        AuctionBid auditBid = AuctionBid.builder()
                .tournament(tournament)
                .tournamentPlayer(state.getCurrentPlayer())
                .team(team)
                .bidAmount(finalBidAmount)
                .bidNumber(bidCount)
                .build();
        auctionBidRepository.save(auditBid);

        log.info("Master micro-write: Bid ₹{} ({}) accepted for Team [{}] on Player [{}] in Tournament [{}]",
                finalBidAmount, calcResult.getStrategyApplied(), team.getName(), state.getCurrentPlayer().getPlayerName(), tournament.getPublicId());

        return BidResponse.builder()
                .currentBid(finalBidAmount)
                .leadingTeamId(team.getId())
                .leadingTeamName(team.getName())
                .timerReset(timerReset)
                .incrementApplied(calcResult.getIncrementApplied())
                .strategyApplied(calcResult.getStrategyApplied())
                .build();
    }

    public AuctionSellResponse sellPlayer(Tournament tournament) {
        acquireTournamentAdvisoryLock(tournament.getId());

        LiveAuctionState state = auctionStateRepository.findByTournamentId(tournament.getId())
                .orElseThrow(() -> new ConflictException("AUCTION_NOT_INITIALIZED", "Auction room not initialized."));

        if (state.getCurrentPlayer() == null) {
            throw new BadRequestException("NO_PLAYER_ON_HAMMER", "No player is currently on the hammer to sell.");
        }

        if (state.getLeadingTeam() == null) {
            throw new ConflictException("NO_ACTIVE_BID", "Cannot sell player without any placed bids. Use pass if unsold.");
        }

        TournamentPlayer player = state.getCurrentPlayer();
        Team winningTeam = state.getLeadingTeam();
        Long finalPrice = state.getCurrentBid();

        // 1. Debit team purse
        winningTeam.setRemainingBudget(winningTeam.getRemainingBudget() - finalPrice);
        teamRepository.save(winningTeam);

        // 2. Add to team squad
        SquadMember squadMember = SquadMember.builder()
                .team(winningTeam)
                .tournamentPlayerId(player.getId())
                .playerUserId(player.getUser() != null ? player.getUser().getId() : null)
                .playerName(player.getPlayerName())
                .playingRole(player.getRegisteredRole())
                .purchasePrice(finalPrice)
                .isCaptain(false)
                .build();
        squadMemberRepository.save(squadMember);

        // 3. Update player status
        player.setStatus("Sold");
        playerRepository.save(player);

        // 4. Clear current player from hammer
        state.setCurrentPlayer(null);
        state.setCurrentBid(0L);
        state.setLeadingTeam(null);
        state.setGoingTwiceWarning(0);
        auctionStateRepository.save(state);

        log.info("Master micro-write: Player [{}] SOLD to Team [{}] for ₹{} in Tournament [{}]",
                player.getPlayerName(), winningTeam.getName(), finalPrice, tournament.getPublicId());

        return AuctionSellResponse.builder()
                .playerId(player.getId())
                .playerName(player.getPlayerName())
                .soldToTeamId(winningTeam.getId())
                .soldToTeamName(winningTeam.getName())
                .finalPrice(finalPrice)
                .remainingPurse(winningTeam.getRemainingBudget())
                .build();
    }

    public AuctionPassResponse passPlayer(Tournament tournament) {
        acquireTournamentAdvisoryLock(tournament.getId());

        LiveAuctionState state = auctionStateRepository.findByTournamentId(tournament.getId())
                .orElseThrow(() -> new ConflictException("AUCTION_NOT_INITIALIZED", "Auction room not initialized."));

        if (state.getCurrentPlayer() == null) {
            throw new BadRequestException("NO_PLAYER_ON_HAMMER", "No player is currently on the hammer to pass.");
        }

        TournamentPlayer player = state.getCurrentPlayer();
        player.setStatus("Unsold");
        playerRepository.save(player);

        state.setCurrentPlayer(null);
        state.setCurrentBid(0L);
        state.setLeadingTeam(null);
        state.setGoingTwiceWarning(0);
        auctionStateRepository.save(state);

        log.info("Master micro-write: Player [{}] marked UNSOLD in Tournament [{}]",
                player.getPlayerName(), tournament.getPublicId());

        return AuctionPassResponse.builder()
                .playerId(player.getId())
                .playerName(player.getPlayerName())
                .status("UNSOLD")
                .build();
    }

    public AuctionUndoResponse undoLastBid(Tournament tournament) {
        acquireTournamentAdvisoryLock(tournament.getId());

        LiveAuctionState state = auctionStateRepository.findByTournamentId(tournament.getId())
                .orElseThrow(() -> new ConflictException("AUCTION_NOT_INITIALIZED", "Auction room not initialized."));

        if (state.getCurrentPlayer() == null) {
            throw new BadRequestException("NO_PLAYER_ON_HAMMER", "No player is currently on the hammer.");
        }

        Long playerId = state.getCurrentPlayer().getId();
        List<AuctionBid> bids = auctionBidRepository.findByTournamentIdAndTournamentPlayerIdOrderByIdDesc(tournament.getId(), playerId);

        if (bids.isEmpty()) {
            throw new ConflictException("NO_BIDS_TO_UNDO", "No bids exist for current player to undo.");
        }

        AuctionBid lastBid = bids.get(0);
        auctionBidRepository.delete(lastBid);

        TournamentSettings settings = settingsRepository.findById(tournament.getId()).orElse(null);
        int timerSeconds = (settings != null && settings.getBidTimerSeconds() > 0)
                ? settings.getBidTimerSeconds()
                : 10;

        if (bids.size() > 1) {
            AuctionBid previousBid = bids.get(1);
            state.setCurrentBid(previousBid.getBidAmount());
            state.setLeadingTeam(previousBid.getTeam());
            state.setTimerSeconds(timerSeconds);
            state.setGoingTwiceWarning(0);
            auctionStateRepository.save(state);

            log.info("Master micro-write: Undone bid ₹{} on Player [{}] in Tournament [{}], reverted to Team [{}] at ₹{}",
                    lastBid.getBidAmount(), state.getCurrentPlayer().getPlayerName(), tournament.getPublicId(),
                    previousBid.getTeam().getName(), previousBid.getBidAmount());

            return AuctionUndoResponse.builder()
                    .currentBid(previousBid.getBidAmount())
                    .leadingTeamId(previousBid.getTeam().getId())
                    .leadingTeamName(previousBid.getTeam().getName())
                    .build();
        } else {
            // No previous bids; revert to base price
            TournamentPlayer currPlayer = state.getCurrentPlayer();
            Long basePrice = (currPlayer.getBasePrice() != null && currPlayer.getBasePrice() > 0)
                    ? currPlayer.getBasePrice()
                    : ((settings != null && settings.getDefaultBasePrice() > 0) ? settings.getDefaultBasePrice() : 5000L);
            state.setCurrentBid(basePrice);
            state.setLeadingTeam(null);
            state.setTimerSeconds(timerSeconds);
            state.setGoingTwiceWarning(0);
            auctionStateRepository.save(state);

            log.info("Master micro-write: Undone opening bid ₹{} on Player [{}], reverted to base price ₹{}",
                    lastBid.getBidAmount(), state.getCurrentPlayer().getPlayerName(), basePrice);

            return AuctionUndoResponse.builder()
                    .currentBid(basePrice)
                    .leadingTeamId(null)
                    .leadingTeamName(null)
                    .build();
        }
    }

    public int startAcceleratedRound(Tournament tournament, Double revisedBasePriceFactor) {
        acquireTournamentAdvisoryLock(tournament.getId());

        List<TournamentPlayer> unsoldPlayers = playerRepository.findByTournamentIdAndStatusIgnoreCase(tournament.getId(), "Unsold");
        int count = 0;
        for (TournamentPlayer tp : unsoldPlayers) {
            tp.setStatus("Approved");
            if (revisedBasePriceFactor != null && revisedBasePriceFactor > 0 && tp.getBasePrice() != null) {
                long discountedPrice = Math.round(tp.getBasePrice() * revisedBasePriceFactor);
                tp.setBasePrice(Math.max(1000L, discountedPrice));
            }
            playerRepository.save(tp);
            count++;
        }

        LiveAuctionState state = auctionStateRepository.findByTournamentId(tournament.getId())
                .orElseGet(() -> LiveAuctionState.builder().tournamentId(tournament.getId()).build());
        state.setRound(state.getRound() + 1);
        auctionStateRepository.save(state);

        log.info("Master micro-write: Accelerated Round started for Tournament [{}], reset {} unsold players to Approved (Round {})",
                tournament.getPublicId(), count, state.getRound());

        return count;
    }

    public void updateCaptainWishlist(Tournament tournament, Team team, TournamentPlayer player, String action, Long maxBudget, String priority) {
        if ("remove".equalsIgnoreCase(action)) {
            wishlistRepository.deleteByTeamIdAndTournamentPlayerId(team.getId(), player.getId());
            log.info("Master micro-write: Removed Player [{}] from Team [{}] wishlist", player.getId(), team.getId());
        } else {
            CaptainWishlist item = wishlistRepository.findByTeamIdAndTournamentPlayerId(team.getId(), player.getId())
                    .orElseGet(() -> CaptainWishlist.builder()
                            .tournament(tournament)
                            .team(team)
                            .tournamentPlayer(player)
                            .build());

            item.setMaxBudget(maxBudget);
            item.setPriority(priority != null ? priority : "Medium");
            wishlistRepository.save(item);

            log.info("Master micro-write: Saved Player [{}] in Team [{}] wishlist (Priority: {}, MaxBudget: {})",
                    player.getId(), team.getId(), priority, maxBudget);
        }
    }
}
