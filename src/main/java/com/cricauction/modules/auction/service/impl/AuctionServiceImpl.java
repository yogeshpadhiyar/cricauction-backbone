package com.cricauction.modules.auction.service.impl;

import com.cricauction.modules.auction.dto.request.AcceleratedRoundRequest;
import com.cricauction.modules.auction.dto.request.NextPlayerRequest;
import com.cricauction.modules.auction.dto.request.PlaceBidRequest;
import com.cricauction.modules.auction.dto.request.StartAuctionRequest;
import com.cricauction.modules.auction.dto.request.WishlistActionRequest;
import com.cricauction.modules.auction.dto.response.*;
import com.cricauction.modules.auction.entity.LiveAuctionState;
import com.cricauction.modules.auction.service.AuctionBroadcaster;
import com.cricauction.modules.auction.service.AuctionPersistenceService;
import com.cricauction.modules.auction.service.AuctionQueryService;
import com.cricauction.modules.auction.service.AuctionService;
import com.cricauction.modules.player.entity.TournamentPlayer;
import com.cricauction.modules.team.entity.Team;
import com.cricauction.modules.tournament.entity.Tournament;
import com.cricauction.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Concrete Implementation of AuctionService.
 * Orchestrates Read-Before-Write (Strategy 1) master-replica routing
 * and real-time STOMP event broadcasting.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuctionServiceImpl implements AuctionService {

    private final AuctionQueryService queryService;
    private final AuctionPersistenceService persistenceService;
    private final AuctionBroadcaster broadcaster;

    @Override
    public AuctionStartResponse initializeAndStartAuction(UserPrincipal currentUser, String tournamentId, StartAuctionRequest request) {
        // Step 1: Read Phase on Read Replica
        Tournament tournament = queryService.findTournament(tournamentId);
        queryService.validateOrganizerOwnership(tournament, currentUser);

        // Step 2: Micro-Write on Master DB
        AuctionStartResponse response = persistenceService.startAuction(tournament, request);

        // Step 3: Broadcast WebSocket Event
        broadcaster.broadcast(tournament.getPublicId(), "AUCTION_PAUSED_RESUMED", response);

        return response;
    }

    @Override
    public void pauseAuction(UserPrincipal currentUser, String tournamentId) {
        Tournament tournament = queryService.findTournament(tournamentId);
        queryService.validateOrganizerOwnership(tournament, currentUser);

        persistenceService.pauseAuction(tournament);
        broadcaster.broadcast(tournament.getPublicId(), "AUCTION_PAUSED_RESUMED", "{\"status\": \"PAUSED\"}");
    }

    @Override
    public void resumeAuction(UserPrincipal currentUser, String tournamentId) {
        Tournament tournament = queryService.findTournament(tournamentId);
        queryService.validateOrganizerOwnership(tournament, currentUser);

        persistenceService.resumeAuction(tournament);
        broadcaster.broadcast(tournament.getPublicId(), "AUCTION_PAUSED_RESUMED", "{\"status\": \"LIVE\"}");
    }

    @Override
    public CurrentPlayerDto callNextPlayer(UserPrincipal currentUser, String tournamentId, NextPlayerRequest request) {
        Tournament tournament = queryService.findTournament(tournamentId);
        queryService.validateOrganizerOwnership(tournament, currentUser);

        Long preferredPlayerId = (request != null) ? request.getPlayerId() : null;
        TournamentPlayer player = queryService.findNextEligiblePlayer(tournament, preferredPlayerId);

        CurrentPlayerDto playerDto = persistenceService.callNextPlayer(tournament, player);
        broadcaster.broadcast(tournament.getPublicId(), "AUCTION_NEW_PLAYER", playerDto);

        return playerDto;
    }

    @Override
    public BidResponse placeBid(UserPrincipal currentUser, String tournamentId, PlaceBidRequest request) {
        Tournament tournament = queryService.findTournament(tournamentId);
        Team team = queryService.validateCaptainOfTeam(tournament, request.getTeamId(), currentUser);

        BidResponse response = persistenceService.placeBid(tournament, team, request);
        broadcaster.broadcast(tournament.getPublicId(), "AUCTION_BID_PLACED", response);

        return response;
    }

    @Override
    public AuctionSellResponse sellPlayer(UserPrincipal currentUser, String tournamentId) {
        Tournament tournament = queryService.findTournament(tournamentId);
        queryService.validateOrganizerOwnership(tournament, currentUser);

        AuctionSellResponse response = persistenceService.sellPlayer(tournament);
        broadcaster.broadcast(tournament.getPublicId(), "AUCTION_PLAYER_SOLD", response);

        return response;
    }

    @Override
    public AuctionPassResponse passPlayer(UserPrincipal currentUser, String tournamentId) {
        Tournament tournament = queryService.findTournament(tournamentId);
        queryService.validateOrganizerOwnership(tournament, currentUser);

        AuctionPassResponse response = persistenceService.passPlayer(tournament);
        broadcaster.broadcast(tournament.getPublicId(), "AUCTION_PLAYER_UNSOLD", response);

        return response;
    }

    @Override
    public AuctionUndoResponse undoLastBid(UserPrincipal currentUser, String tournamentId) {
        Tournament tournament = queryService.findTournament(tournamentId);
        queryService.validateOrganizerOwnership(tournament, currentUser);

        AuctionUndoResponse response = persistenceService.undoLastBid(tournament);
        broadcaster.broadcast(tournament.getPublicId(), "AUCTION_BID_UNDONE", response);

        return response;
    }

    @Override
    public AuctionStateResponse getAuctionState(UserPrincipal currentUser, String tournamentId) {
        Tournament tournament = queryService.findTournament(tournamentId);
        return queryService.getAuctionState(tournament);
    }

    @Override
    public List<AuctionHistoryItemDto> getAuctionHistory(UserPrincipal currentUser, String tournamentId) {
        Tournament tournament = queryService.findTournament(tournamentId);
        return queryService.getAuctionHistory(tournament);
    }

    @Override
    public int startAcceleratedRound(UserPrincipal currentUser, String tournamentId, AcceleratedRoundRequest request) {
        Tournament tournament = queryService.findTournament(tournamentId);
        queryService.validateOrganizerOwnership(tournament, currentUser);

        Double factor = (request != null) ? request.getRevisedBasePriceFactor() : null;
        int count = persistenceService.startAcceleratedRound(tournament, factor);
        broadcaster.broadcast(tournament.getPublicId(), "AUCTION_ACCELERATED_ROUND_STARTED", "{\"reintroducedCount\": " + count + "}");

        return count;
    }

    @Override
    public List<WishlistItemDto> getCaptainWishlist(UserPrincipal currentUser, String tournamentId) {
        Tournament tournament = queryService.findTournament(tournamentId);
        Team team = queryService.findCaptainTeam(tournament, currentUser);
        return queryService.getCaptainWishlist(tournament, team);
    }

    @Override
    public void updateCaptainWishlist(UserPrincipal currentUser, String tournamentId, WishlistActionRequest request) {
        Tournament tournament = queryService.findTournament(tournamentId);
        Team team = queryService.findCaptainTeam(tournament, currentUser);
        TournamentPlayer player = queryService.findTournamentPlayer(tournament, request.getPlayerId());

        persistenceService.updateCaptainWishlist(
                tournament,
                team,
                player,
                request.getAction(),
                request.getMaxBudget(),
                request.getPriority()
        );
    }
}
