package com.cricauction.modules.match.service;

import com.cricauction.modules.match.dto.*;
import com.cricauction.security.UserPrincipal;

import java.util.List;

public interface MatchService {

    List<MatchFixtureResponse> getTournamentFixtures(UserPrincipal currentUser, String tournamentId);

    MatchFixtureResponse createFixture(UserPrincipal currentUser, String tournamentId, CreateMatchFixtureRequest request);

    MatchScorecardResponse getMatchScorecard(UserPrincipal currentUser, String matchId);

    BallUpdateResponse recordBall(UserPrincipal currentUser, String matchId, RecordBallRequest request);

    List<TournamentStandingsResponse> getTournamentStandings(UserPrincipal currentUser, String tournamentId);
}

