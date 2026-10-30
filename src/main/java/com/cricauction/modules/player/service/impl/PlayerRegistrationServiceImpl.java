package com.cricauction.modules.player.service.impl;

import com.cricauction.common.exception.UnauthorizedException;
import com.cricauction.modules.auth.entity.User;
import com.cricauction.modules.player.dto.request.PlayerRegistrationRequest;
import com.cricauction.modules.player.dto.request.UpdatePlayerBasePriceRequest;
import com.cricauction.modules.player.dto.request.UpdatePlayerStatusRequest;
import com.cricauction.modules.player.dto.response.*;
import com.cricauction.modules.player.entity.TournamentPlayer;
import com.cricauction.modules.player.service.PlayerPersistenceService;
import com.cricauction.modules.player.service.PlayerQueryService;
import com.cricauction.modules.player.service.PlayerRegistrationService;
import com.cricauction.modules.tournament.entity.Tournament;
import com.cricauction.modules.tournament.entity.TournamentSettings;
import com.cricauction.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Orchestrator Implementation for Module 6: Player Registration & Capacity Management.
 * Implements Strategy 1 (Read-Before-Write):
 * - Validations and queries route to Read Replica (PlayerQueryService).
 * - Mutations route to Master DB via fast micro-transactions (PlayerPersistenceService).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlayerRegistrationServiceImpl implements PlayerRegistrationService {

    private final PlayerQueryService playerQueryService;
    private final PlayerPersistenceService playerPersistenceService;

    @Override
    public PlayerRegistrationResponse registerPlayer(UserPrincipal currentUser, String tournamentId, PlayerRegistrationRequest request) {
        validateAuthenticated(currentUser);

        // Step 1: Preparatory Reads on Read Replica
        Tournament tournament = playerQueryService.findTournamentForRegistration(tournamentId);
        TournamentSettings settings = playerQueryService.findTournamentSettings(tournament.getId());
        playerQueryService.validatePlayerNotAlreadyRegistered(tournament.getId(), currentUser.getId());
        User user = playerQueryService.findUser(currentUser.getId());

        // Step 2: Atomic Micro-Write on Master DB
        TournamentPlayer player = playerPersistenceService.executeRegisterPlayer(
                user,
                tournament,
                settings,
                request
        );

        return PlayerRegistrationResponse.builder()
                .registrationId(player.getPublicRegistrationId())
                .status(player.getStatus())
                .tournamentName(tournament.getName())
                .build();
    }

    @Override
    public PlayerRegistrationStatusResponse getMyRegistrationStatus(UserPrincipal currentUser, String tournamentId) {
        validateAuthenticated(currentUser);
        return playerQueryService.getPlayerStatus(tournamentId, currentUser.getId());
    }

    @Override
    public void withdrawFromTournament(UserPrincipal currentUser, String tournamentId) {
        validateAuthenticated(currentUser);
        playerPersistenceService.executeWithdraw(tournamentId, currentUser.getId());
    }

    @Override
    public PlayerListResponse listRegisteredPlayers(UserPrincipal currentUser, String tournamentId, String filter, String search, int page, int limit) {
        validateAuthenticated(currentUser);
        // Ownership verified on Read Replica
        Tournament tournament = playerQueryService.validateTournamentOwnership(tournamentId, currentUser.getId());

        return playerQueryService.listTournamentPlayers(tournament, filter, search, page, limit);
    }

    @Override
    public PlayerCapacityStatsResponse getRegistrationCapacityStats(UserPrincipal currentUser, String tournamentId) {
        // Stats can be viewed by authenticated users (players & organizers)
        validateAuthenticated(currentUser);
        return playerQueryService.getCapacityStats(tournamentId);
    }

    @Override
    public void updatePlayerStatus(UserPrincipal currentUser, String tournamentId, Long playerId, UpdatePlayerStatusRequest request) {
        validateAuthenticated(currentUser);
        // Step 1: Ownership check on Read Replica
        playerQueryService.validateTournamentOwnership(tournamentId, currentUser.getId());

        // Step 2: Micro-Write on Master DB
        playerPersistenceService.updatePlayerStatus(tournamentId, playerId, request.getStatus(), request.getReason());
    }

    @Override
    public void updatePlayerBasePrice(UserPrincipal currentUser, String tournamentId, Long playerId, UpdatePlayerBasePriceRequest request) {
        validateAuthenticated(currentUser);
        // Step 1: Ownership check on Read Replica
        playerQueryService.validateTournamentOwnership(tournamentId, currentUser.getId());

        // Step 2: Micro-Write on Master DB
        playerPersistenceService.updatePlayerBasePrice(tournamentId, playerId, request.getBasePrice());
    }

    private void validateAuthenticated(UserPrincipal currentUser) {
        if (currentUser == null || currentUser.getId() == null) {
            throw new UnauthorizedException("UNAUTHORIZED", "Authentication required to access player registration features.");
        }
    }
}
