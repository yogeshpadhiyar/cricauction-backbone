package com.cricauction.modules.auction.service;

import com.cricauction.common.exception.BadRequestException;
import com.cricauction.common.exception.ForbiddenException;
import com.cricauction.common.exception.ResourceNotFoundException;
import com.cricauction.modules.auction.dto.response.*;
import com.cricauction.modules.auction.entity.AuctionBid;
import com.cricauction.modules.auction.entity.CaptainWishlist;
import com.cricauction.modules.auction.entity.LiveAuctionState;
import com.cricauction.modules.auction.entity.enums.AuctionStatus;
import com.cricauction.modules.auction.repository.AuctionBidRepository;
import com.cricauction.modules.auction.repository.CaptainWishlistRepository;
import com.cricauction.modules.auction.repository.LiveAuctionStateRepository;
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
import com.cricauction.modules.tournament.repository.TournamentRepository;
import com.cricauction.modules.tournament.repository.TournamentSettingsRepository;
import com.cricauction.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Dedicated Read Service for Live Auction Engine.
 * All queries execute under @Transactional(readOnly = true) on CricAuction-Replica-Pool.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuctionQueryService {

    private final TournamentRepository tournamentRepository;
    private final TournamentSettingsRepository settingsRepository;
    private final LiveAuctionStateRepository auctionStateRepository;
    private final AuctionBidRepository auctionBidRepository;
    private final CaptainWishlistRepository wishlistRepository;
    private final TeamRepository teamRepository;
    private final TournamentPlayerRepository playerRepository;
    private final SquadMemberRepository squadMemberRepository;
    private final BidStrategyResolver bidStrategyResolver;

    public Tournament findTournament(String idOrSlug) {
        return tournamentRepository.findByPublicId(idOrSlug)
                .or(() -> tournamentRepository.findBySlug(idOrSlug))
                .orElseThrow(() -> new ResourceNotFoundException("TOURNAMENT_NOT_FOUND", "Tournament not found for ID or slug: " + idOrSlug));
    }

    public TournamentSettings findSettings(Long tournamentId) {
        return settingsRepository.findById(tournamentId).orElse(null);
    }

    public void validateOrganizerRole(UserPrincipal currentUser) {
        if (currentUser == null) {
            throw new ForbiddenException("UNAUTHORIZED", "Authentication required.");
        }
        boolean isOrganizer = currentUser.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ORGANIZER") || a.getAuthority().equals("ROLE_ADMIN"));
        if (!isOrganizer) {
            throw new ForbiddenException("FORBIDDEN", "Only tournament organizers or administrators may perform this action.");
        }
    }

    public void validateOrganizerOwnership(Tournament tournament, UserPrincipal currentUser) {
        validateOrganizerRole(currentUser);
        if (!tournament.getOrganizerId().equals(currentUser.getId())) {
            throw new ForbiddenException("TOURNAMENT_ACCESS_DENIED", "Access denied: You are not the owner of this tournament.");
        }
    }

    public Team validateCaptainOfTeam(Tournament tournament, Long teamId, UserPrincipal currentUser) {
        if (currentUser == null) {
            throw new ForbiddenException("UNAUTHORIZED", "Authentication required.");
        }
        Team team = teamRepository.findByIdAndTournamentId(teamId, tournament.getId())
                .orElseThrow(() -> new ResourceNotFoundException("TEAM_NOT_FOUND", "Team ID " + teamId + " not found in tournament."));

        if (team.getCaptainId() == null || !team.getCaptainId().equals(currentUser.getId())) {
            throw new ForbiddenException("NOT_TEAM_CAPTAIN", "You are not the registered captain of team '" + team.getName() + "'.");
        }
        return team;
    }

    public Team findCaptainTeam(Tournament tournament, UserPrincipal currentUser) {
        if (currentUser == null) {
            throw new ForbiddenException("UNAUTHORIZED", "Authentication required.");
        }
        return teamRepository.findByTournamentIdAndCaptainId(tournament.getId(), currentUser.getId())
                .orElseThrow(() -> new ForbiddenException("CAPTAIN_TEAM_NOT_FOUND", "You are not assigned as captain of any team in this tournament."));
    }

    public TournamentPlayer findTournamentPlayer(Tournament tournament, Long playerId) {
        return playerRepository.findById(playerId)
                .filter(p -> p.getTournament().getId().equals(tournament.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("PLAYER_NOT_FOUND", "Player ID " + playerId + " not found in tournament."));
    }

    public TournamentPlayer findNextEligiblePlayer(Tournament tournament, Long preferredPlayerId) {
        if (preferredPlayerId != null) {
            TournamentPlayer player = findTournamentPlayer(tournament, preferredPlayerId);
            String status = player.getStatus() != null ? player.getStatus().trim() : "";
            if ("Sold".equalsIgnoreCase(status)) {
                throw new BadRequestException("PLAYER_ALREADY_SOLD", "Player '" + player.getPlayerName() + "' has already been sold.");
            }
            return player;
        }

        // Auto-queue: Pick next player with status 'Approved'
        return playerRepository.findFirstByTournamentIdAndStatusIgnoreCaseOrderByIdAsc(tournament.getId(), "Approved")
                .or(() -> playerRepository.findFirstByTournamentIdAndStatusIgnoreCaseOrderByIdAsc(tournament.getId(), "Registered"))
                .orElseThrow(() -> new BadRequestException("NO_PLAYERS_IN_QUEUE", "No eligible players remaining in the auction queue."));
    }

    public AuctionStateResponse getAuctionState(Tournament tournament) {
        LiveAuctionState state = auctionStateRepository.findByTournamentId(tournament.getId())
                .orElse(null);

        TournamentSettings settings = findSettings(tournament.getId());
        int maxSquad = settings != null ? settings.getSquadMaximum() : 15;

        CurrentPlayerDto playerDto = null;
        if (state != null && state.getCurrentPlayer() != null) {
            TournamentPlayer p = state.getCurrentPlayer();
            playerDto = CurrentPlayerDto.builder()
                    .id(p.getId())
                    .name(p.getPlayerName())
                    .role(p.getRegisteredRole())
                    .battingStyle(p.getBattingStyle())
                    .bowlingStyle(p.getBowlingStyle())
                    .basePrice(p.getBasePrice() != null ? p.getBasePrice() : 5000L)
                    .currentBid(state.getCurrentBid())
                    .leadingTeamId(state.getLeadingTeam() != null ? state.getLeadingTeam().getId() : null)
                    .leadingTeam(state.getLeadingTeam() != null ? state.getLeadingTeam().getName() : null)
                    .secondsRemaining(state.getTimerSeconds())
                    .photo(p.getPhotoUrl())
                    .build();
        }

        List<Team> teams = teamRepository.findByTournamentIdOrderByIdAsc(tournament.getId());
        List<Long> teamIds = teams.stream().map(Team::getId).toList();
        Map<Long, Long> squadCountMap = teamIds.isEmpty()
                ? Collections.emptyMap()
                : squadMemberRepository.countSquadMembersByTeamIds(teamIds).stream()
                        .collect(Collectors.toMap(
                                p -> p.getTeamId(),
                                p -> p.getSquadCount() != null ? p.getSquadCount() : 0L,
                                (a, b) -> a
                        ));

        List<AuctionTeamDto> teamDtos = new ArrayList<>();
        for (Team t : teams) {
            long squadCount = squadCountMap.getOrDefault(t.getId(), 0L);
            teamDtos.add(AuctionTeamDto.builder()
                    .id(t.getId())
                    .name(t.getName())
                    .shortCode(t.getShortCode())
                    .remainingBudget(t.getRemainingBudget())
                    .initialBudget(t.getInitialBudget())
                    .squadCount(squadCount)
                    .maxSquad(maxSquad)
                    .build());
        }

        long upcomingCount = playerRepository.countByTournamentIdAndStatusIgnoreCase(tournament.getId(), "Approved");
        long soldCount = playerRepository.countByTournamentIdAndStatusIgnoreCase(tournament.getId(), "Sold");
        long unsoldCount = playerRepository.countByTournamentIdAndStatusIgnoreCase(tournament.getId(), "Unsold");

        String statusStr = (state != null && state.getStatus() != null)
                ? state.getStatus().name()
                : AuctionStatus.PENDING.name();

        BiddingStrategyType strategyType = (state != null && state.getBidStrategy() != null)
                ? state.getBidStrategy()
                : BiddingStrategyType.ADDITIONAL_AMOUNT;
        Double incrementPercentage = state != null ? state.getIncrementPercentage() : 10.0;
        Long minBidIncrement = state != null ? state.getMinBidIncrement() : (settings != null && settings.getBidIncrement() > 0 ? settings.getBidIncrement() : 1000L);

        Long nextMinimumBid = null;
        if (state != null && state.getCurrentPlayer() != null) {
            BidIncrementStrategy strategy = bidStrategyResolver.resolve(strategyType);
            nextMinimumBid = strategy.calculateMinimumNextBid(state, settings);
        }

        return AuctionStateResponse.builder()
                .status(statusStr)
                .isPaused(state != null && Boolean.TRUE.equals(state.getIsPaused()))
                .round(state != null ? state.getRound() : 1)
                .currentPlayer(playerDto)
                .teams(teamDtos)
                .upcomingQueueCount(upcomingCount)
                .soldCount(soldCount)
                .unsoldCount(unsoldCount)
                .bidStrategy(strategyType)
                .incrementPercentage(incrementPercentage)
                .minBidIncrement(minBidIncrement)
                .nextMinimumBid(nextMinimumBid)
                .build();
    }

    public List<AuctionHistoryItemDto> getAuctionHistory(Tournament tournament) {
        List<AuctionHistoryItemDto> history = new ArrayList<>();

        // 1. Fetch sold squad members for this tournament in a single JOIN FETCH query
        List<SquadMember> soldMembers = squadMemberRepository.findSoldSquadMembersByTournamentId(tournament.getId());
        for (SquadMember sm : soldMembers) {
            history.add(AuctionHistoryItemDto.builder()
                    .id(sm.getId())
                    .round(1)
                    .playerId(sm.getTournamentPlayerId())
                    .playerName(sm.getPlayerName())
                    .role(sm.getPlayingRole())
                    .soldToTeamId(sm.getTeam().getId())
                    .soldTo(sm.getTeam().getName())
                    .price(sm.getPurchasePrice())
                    .status("Sold")
                    .timestamp(sm.getBoughtAt())
                    .build());
        }

        // 2. Fetch unsold players
        List<TournamentPlayer> unsoldPlayers = playerRepository.findByTournamentIdAndStatusIgnoreCase(tournament.getId(), "Unsold");
        for (TournamentPlayer up : unsoldPlayers) {
            history.add(AuctionHistoryItemDto.builder()
                    .id(up.getId())
                    .round(1)
                    .playerId(up.getId())
                    .playerName(up.getPlayerName())
                    .role(up.getRegisteredRole())
                    .soldToTeamId(null)
                    .soldTo(null)
                    .price(0L)
                    .status("Unsold")
                    .timestamp(up.getUpdatedAt())
                    .build());
        }

        return history;
    }

    public List<WishlistItemDto> getCaptainWishlist(Tournament tournament, Team team) {
        List<CaptainWishlist> wishlists = wishlistRepository.findByTeamIdOrderByCreatedAtDesc(team.getId());
        List<WishlistItemDto> dtos = new ArrayList<>();
        for (CaptainWishlist cw : wishlists) {
            TournamentPlayer p = cw.getTournamentPlayer();
            dtos.add(WishlistItemDto.builder()
                    .id(cw.getId())
                    .playerId(p.getId())
                    .playerName(p.getPlayerName())
                    .playingRole(p.getRegisteredRole())
                    .basePrice(p.getBasePrice())
                    .playerStatus(p.getStatus())
                    .priority(cw.getPriority())
                    .maxBudget(cw.getMaxBudget())
                    .build());
        }
        return dtos;
    }
}
