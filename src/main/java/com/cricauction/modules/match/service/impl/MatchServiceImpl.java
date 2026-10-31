package com.cricauction.modules.match.service.impl;

import com.cricauction.modules.match.dto.*;
import com.cricauction.modules.match.entity.Match;
import com.cricauction.modules.match.service.MatchBroadcaster;
import com.cricauction.modules.match.service.MatchPersistenceService;
import com.cricauction.modules.match.service.MatchQueryService;
import com.cricauction.modules.match.service.MatchService;
import com.cricauction.modules.team.entity.Team;
import com.cricauction.modules.tournament.entity.Tournament;
import com.cricauction.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * Enterprise Orchestrator implementing Strategy 1: "Read-Before-Write" for Module 8.
 * Segregates read validations to Read Replica pool and serializes state mutations on Master pool.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MatchServiceImpl implements MatchService {

    private final MatchQueryService queryService;
    private final MatchPersistenceService persistenceService;
    private final MatchBroadcaster broadcaster;

    @Override
    public List<MatchFixtureResponse> getTournamentFixtures(UserPrincipal currentUser, String tournamentId) {
        return queryService.getTournamentFixtures(tournamentId);
    }

    @Override
    public MatchFixtureResponse createFixture(UserPrincipal currentUser, String tournamentId, CreateMatchFixtureRequest request) {
        // Step 1: Read phase & authorization on Read Replica
        Tournament tournament = queryService.findTournament(tournamentId);
        queryService.validateOrganizerOwnership(tournament, currentUser);

        Team team1 = queryService.findTeamInTournament(tournament, request.getTeam1Id());
        Team team2 = queryService.findTeamInTournament(tournament, request.getTeam2Id());

        // Step 2: Micro-write on Master DB
        Match savedMatch = persistenceService.createFixture(tournament, team1, team2, request);

        // Step 3: Invalidate cache and broadcast
        queryService.invalidateStandingsCache(tournament.getId());
        MatchFixtureResponse response = MatchFixtureResponse.builder()
                .id(savedMatch.getPublicId())
                .team1(team1.getName())
                .team2(team2.getName())
                .scheduledAt(savedMatch.getScheduledAt())
                .ground(savedMatch.getGround())
                .status(savedMatch.getStatus())
                .build();
        broadcaster.broadcastTournamentMatchUpdate(tournament.getPublicId(), "FIXTURE_CREATED", response);

        return response;
    }

    @Override
    public MatchScorecardResponse getMatchScorecard(UserPrincipal currentUser, String matchId) {
        return queryService.getMatchScorecard(matchId);
    }

    @Override
    public BallUpdateResponse recordBall(UserPrincipal currentUser, String matchId, RecordBallRequest request) {
        // Step 1: Read phase & authorization on Read Replica
        Match match = queryService.findMatch(matchId);
        queryService.validateOrganizerOwnership(match.getTournament(), currentUser);

        // Step 2: Serialized write on Master DB under advisory lock
        BallUpdateResponse updateResponse = persistenceService.recordBall(match.getId(), request);

        // Step 3: Broadcast score update via WebSocket STOMP
        try {
            MatchScorecardResponse scorecard = queryService.getMatchScorecard(match.getPublicId());
            MatchScoreUpdateEvent scoreEvent = MatchScoreUpdateEvent.builder()
                    .eventType("BALL_RECORDED")
                    .matchId(match.getPublicId())
                    .scorecard(scorecard)
                    .lastBall(updateResponse)
                    .timestamp(Instant.now())
                    .build();
            broadcaster.broadcastScoreUpdate(match.getPublicId(), scoreEvent);

            if ("COMPLETED".equals(updateResponse.getMatchStatus())) {
                queryService.invalidateStandingsCache(match.getTournament().getId());
                broadcaster.broadcastTournamentMatchUpdate(match.getTournament().getPublicId(), "MATCH_COMPLETED", scorecard);
            }
        } catch (Exception ex) {
            log.warn("Non-fatal: Failed to broadcast score update for match [{}]: {}", match.getPublicId(), ex.getMessage());
        }

        return updateResponse;
    }

    @Override
    public List<TournamentStandingsResponse> getTournamentStandings(UserPrincipal currentUser, String tournamentId) {
        return queryService.getTournamentStandings(tournamentId);
    }
}
