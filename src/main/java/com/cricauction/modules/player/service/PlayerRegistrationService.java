package com.cricauction.modules.player.service;

import com.cricauction.modules.player.dto.request.PlayerRegistrationRequest;
import com.cricauction.modules.player.dto.request.UpdatePlayerBasePriceRequest;
import com.cricauction.modules.player.dto.request.UpdatePlayerStatusRequest;
import com.cricauction.modules.player.dto.response.*;
import com.cricauction.security.UserPrincipal;

/**
 * Service Contract for Module 6: Player Registration & Capacity Management (APIs 34 to 40).
 */
public interface PlayerRegistrationService {

    /**
     * API 34: Player registers for a tournament.
     */
    PlayerRegistrationResponse registerPlayer(UserPrincipal currentUser, String tournamentId, PlayerRegistrationRequest request);

    /**
     * API 35: Player checks own registration and auction status.
     */
    PlayerRegistrationStatusResponse getMyRegistrationStatus(UserPrincipal currentUser, String tournamentId);

    /**
     * API 36: Player withdraws registration from tournament.
     */
    void withdrawFromTournament(UserPrincipal currentUser, String tournamentId);

    /**
     * API 37: Tournament organizer lists registered players with filters and pagination.
     */
    PlayerListResponse listRegisteredPlayers(UserPrincipal currentUser, String tournamentId, String filter, String search, int page, int limit);

    /**
     * API 38: Real-time player registration capacity and role breakdown statistics.
     */
    PlayerCapacityStatsResponse getRegistrationCapacityStats(UserPrincipal currentUser, String tournamentId);

    /**
     * API 39: Tournament organizer approves or rejects player registration.
     */
    void updatePlayerStatus(UserPrincipal currentUser, String tournamentId, Long playerId, UpdatePlayerStatusRequest request);

    /**
     * API 40: Tournament organizer updates player base price.
     */
    void updatePlayerBasePrice(UserPrincipal currentUser, String tournamentId, Long playerId, UpdatePlayerBasePriceRequest request);
}
