package com.cricauction.modules.tournament.service;

import com.cricauction.modules.tournament.dto.request.CreateTournamentRequest;
import com.cricauction.modules.tournament.dto.request.TournamentSettingsRequest;
import com.cricauction.modules.tournament.dto.request.UpdateTournamentRequest;
import com.cricauction.modules.tournament.dto.response.*;
import com.cricauction.security.UserPrincipal;

import java.util.List;

public interface TournamentService {

    CreateTournamentResponse createTournament(UserPrincipal currentUser, CreateTournamentRequest request);

    List<TournamentSummaryResponse> getOrganizerTournaments(UserPrincipal currentUser, String status);

    TournamentDetailResponse getTournamentById(UserPrincipal currentUser, String idOrSlug);

    TournamentDetailResponse updateTournament(UserPrincipal currentUser, String idOrSlug, UpdateTournamentRequest request);

    TournamentSettingsResponse saveSettings(UserPrincipal currentUser, String idOrSlug, TournamentSettingsRequest request);

    PublishTournamentResponse publishTournament(UserPrincipal currentUser, String idOrSlug);

    void deleteTournament(UserPrincipal currentUser, String idOrSlug);

    PublicTournamentResponse getPublicTournamentBySlug(String slug);
}
