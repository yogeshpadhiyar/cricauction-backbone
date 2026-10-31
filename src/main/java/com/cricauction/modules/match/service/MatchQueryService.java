package com.cricauction.modules.match.service;

import com.cricauction.common.cache.BoundedLruCache;
import com.cricauction.common.exception.BadRequestException;
import com.cricauction.common.exception.ForbiddenException;
import com.cricauction.common.exception.ResourceNotFoundException;
import com.cricauction.modules.match.dto.*;
import com.cricauction.modules.match.entity.Match;
import com.cricauction.modules.match.entity.MatchScorecard;
import com.cricauction.modules.match.entity.enums.MatchStatus;
import com.cricauction.modules.match.repository.MatchBallRepository;
import com.cricauction.modules.match.repository.MatchRepository;
import com.cricauction.modules.match.repository.MatchScorecardRepository;
import com.cricauction.modules.team.entity.Team;
import com.cricauction.modules.team.repository.TeamRepository;
import com.cricauction.modules.tournament.entity.Tournament;
import com.cricauction.modules.tournament.repository.TournamentRepository;
import com.cricauction.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Enterprise Query Service implementing Strategy 1: "Read-Before-Write" for Module 8.
 * Executes strictly on the Read Replica pool (@Transactional(readOnly = true)).
 * Handles fixture listings, scorecard queries, authorization checks, and cached tournament standings.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MatchQueryService {

    private final MatchRepository matchRepository;
    private final MatchScorecardRepository matchScorecardRepository;
    private final MatchBallRepository matchBallRepository;
    private final TournamentRepository tournamentRepository;
    private final TeamRepository teamRepository;

    // High-performance Bounded LRU Cache for Tournament Standings (TTL: 10s, capacity: 500)
    private final BoundedLruCache<Long, List<TournamentStandingsResponse>> standingsCache =
            new BoundedLruCache<>(500, 10);

    public void invalidateStandingsCache(Long tournamentId) {
        if (tournamentId != null) {
            standingsCache.remove(tournamentId);
        }
    }

    public Tournament findTournament(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            throw new BadRequestException("INVALID_IDENTIFIER", "Tournament identifier cannot be empty.");
        }
        String trimmed = identifier.trim();
        return tournamentRepository.findByPublicIdOrSlugWithSettings(trimmed)
                .or(() -> {
                    if (trimmed.matches("^\\d+$")) {
                        return tournamentRepository.findByIdWithSettings(Long.parseLong(trimmed));
                    }
                    return Optional.empty();
                })
                .orElseThrow(() -> new ResourceNotFoundException("TOURNAMENT_NOT_FOUND", "Tournament not found for identifier: " + identifier));
    }

    public void validateOrganizerOwnership(Tournament tournament, UserPrincipal currentUser) {
        if (currentUser == null || !currentUser.isOrganizer()) {
            throw new ForbiddenException("ORGANIZER_ROLE_REQUIRED", "Access denied: Only tournament organizers can perform this action.");
        }
        if (!tournament.getOrganizerId().equals(currentUser.getId())) {
            throw new ForbiddenException("TOURNAMENT_ACCESS_DENIED", "Access denied: You are not the owner of this tournament.");
        }
    }

    public Match findMatch(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            throw new BadRequestException("INVALID_IDENTIFIER", "Match identifier cannot be empty.");
        }
        String trimmed = identifier.trim();
        return matchRepository.findByPublicIdWithTeams(trimmed)
                .or(() -> {
                    if (trimmed.matches("^\\d+$")) {
                        return matchRepository.findByIdWithTeams(Long.parseLong(trimmed));
                    }
                    return Optional.empty();
                })
                .orElseThrow(() -> new ResourceNotFoundException("MATCH_NOT_FOUND", "Match not found for identifier: " + identifier));
    }

    public Team findTeamInTournament(Tournament tournament, Long teamId) {
        return teamRepository.findByIdAndTournamentId(teamId, tournament.getId())
                .orElseThrow(() -> new ResourceNotFoundException("TEAM_NOT_FOUND", "Team ID " + teamId + " does not belong to tournament " + tournament.getName()));
    }

    public List<MatchFixtureResponse> getTournamentFixtures(String tournamentIdentifier) {
        Tournament tournament = findTournament(tournamentIdentifier);
        List<Match> matches = matchRepository.findByTournamentIdWithTeams(tournament.getId());
        return matches.stream()
                .map(this::mapToFixtureResponse)
                .toList();
    }

    public MatchScorecardResponse getMatchScorecard(String matchIdentifier) {
        Match match = findMatch(matchIdentifier);
        MatchScorecard scorecard = matchScorecardRepository.findByMatchIdWithMatchAndTeams(match.getId())
                .orElseGet(() -> MatchScorecard.builder()
                        .match(match)
                        .matchId(match.getId())
                        .team1Runs(0)
                        .team1Wickets(0)
                        .team1Balls(0)
                        .team2Runs(0)
                        .team2Wickets(0)
                        .team2Balls(0)
                        .currentInnings(1)
                        .build());

        return mapToScorecardResponse(match, scorecard);
    }

    public List<TournamentStandingsResponse> getTournamentStandings(String tournamentIdentifier) {
        Tournament tournament = findTournament(tournamentIdentifier);
        List<TournamentStandingsResponse> cached = standingsCache.get(tournament.getId());
        if (cached != null) {
            return cached;
        }

        List<Team> teams = teamRepository.findByTournamentIdOrderByIdAsc(tournament.getId());
        List<Match> completedMatches = matchRepository.findByTournamentIdAndStatusWithTeams(tournament.getId(), MatchStatus.COMPLETED);

        // Batch-fetch all completed match scorecards to eliminate N+1 queries
        List<Long> completedMatchIds = completedMatches.stream().map(Match::getId).toList();
        Map<Long, MatchScorecard> scorecardsByMatchId = completedMatchIds.isEmpty()
                ? Collections.emptyMap()
                : matchScorecardRepository.findAllById(completedMatchIds).stream()
                .collect(Collectors.toMap(MatchScorecard::getMatchId, Function.identity()));

        // Track stats per team
        Map<Long, TeamStats> statsMap = new HashMap<>();
        for (Team team : teams) {
            statsMap.put(team.getId(), new TeamStats(team.getId(), team.getName()));
        }

        for (Match match : completedMatches) {
            MatchScorecard sc = scorecardsByMatchId.get(match.getId());
            Long t1Id = match.getTeam1().getId();
            Long t2Id = match.getTeam2().getId();

            TeamStats t1Stats = statsMap.get(t1Id);
            TeamStats t2Stats = statsMap.get(t2Id);

            if (t1Stats != null) t1Stats.played++;
            if (t2Stats != null) t2Stats.played++;

            if (match.getWinnerTeamId() != null) {
                if (match.getWinnerTeamId().equals(t1Id)) {
                    if (t1Stats != null) { t1Stats.won++; t1Stats.points += 2; }
                    if (t2Stats != null) { t2Stats.lost++; }
                } else if (match.getWinnerTeamId().equals(t2Id)) {
                    if (t2Stats != null) { t2Stats.won++; t2Stats.points += 2; }
                    if (t1Stats != null) { t1Stats.lost++; }
                }
            } else {
                // Tied or No Result
                if (t1Stats != null) { t1Stats.tied++; t1Stats.points += 1; }
                if (t2Stats != null) { t2Stats.tied++; t2Stats.points += 1; }
            }

            // NRR component accumulation (ICC rule: all-out teams are assessed the full match overs allotment)
            if (sc != null) {
                int maxBallsPerInnings = (match.getOvers() != null ? match.getOvers() : 20) * 6;
                int t1EffectiveBallsFaced = sc.getTeam1Wickets() >= 10 ? maxBallsPerInnings : Math.max(sc.getTeam1Balls(), 1);
                int t2EffectiveBallsFaced = sc.getTeam2Wickets() >= 10 ? maxBallsPerInnings : Math.max(sc.getTeam2Balls(), 1);

                if (t1Stats != null) {
                    t1Stats.runsScored += sc.getTeam1Runs();
                    t1Stats.ballsFaced += t1EffectiveBallsFaced;
                    t1Stats.runsConceded += sc.getTeam2Runs();
                    t1Stats.ballsBowled += t2EffectiveBallsFaced;
                }
                if (t2Stats != null) {
                    t2Stats.runsScored += sc.getTeam2Runs();
                    t2Stats.ballsFaced += t2EffectiveBallsFaced;
                    t2Stats.runsConceded += sc.getTeam1Runs();
                    t2Stats.ballsBowled += t1EffectiveBallsFaced;
                }
            }
        }

        List<TeamStats> sortedStats = new ArrayList<>(statsMap.values());
        sortedStats.sort((a, b) -> {
            int cmpPoints = Integer.compare(b.points, a.points);
            if (cmpPoints != 0) return cmpPoints;
            int cmpNrr = Double.compare(b.calculateNrr(), a.calculateNrr());
            if (cmpNrr != 0) return cmpNrr;
            return a.teamName.compareToIgnoreCase(b.teamName);
        });

        List<TournamentStandingsResponse> responseList = new ArrayList<>();
        int rank = 1;
        for (TeamStats s : sortedStats) {
            responseList.add(TournamentStandingsResponse.builder()
                    .rank(rank++)
                    .team(s.teamName)
                    .teamId(s.teamId)
                    .played(s.played)
                    .won(s.won)
                    .lost(s.lost)
                    .tied(s.tied)
                    .noResult(s.noResult)
                    .nrr(s.formatNrr())
                    .points(s.points)
                    .build());
        }

        standingsCache.put(tournament.getId(), responseList);
        return responseList;
    }

    public MatchFixtureResponse mapToFixtureResponse(Match match) {
        return MatchFixtureResponse.builder()
                .id(match.getPublicId())
                .team1(match.getTeam1().getName())
                .team2(match.getTeam2().getName())
                .scheduledAt(match.getScheduledAt())
                .ground(match.getGround())
                .status(match.getStatus())
                .build();
    }

    public MatchScorecardResponse mapToScorecardResponse(Match match, MatchScorecard scorecard) {
        TeamInningScoreDto team1Score = TeamInningScoreDto.builder()
                .name(match.getTeam1().getName())
                .runs(scorecard.getTeam1Runs())
                .wickets(scorecard.getTeam1Wickets())
                .overs(MatchScorecard.formatBallsToOversNumber(scorecard.getTeam1Balls()))
                .build();

        TeamInningScoreDto team2Score = TeamInningScoreDto.builder()
                .name(match.getTeam2().getName())
                .runs(scorecard.getTeam2Runs())
                .wickets(scorecard.getTeam2Wickets())
                .overs(MatchScorecard.formatBallsToOversNumber(scorecard.getTeam2Balls()))
                .build();

        List<CurrentBatsmanDto> currentBatsmen = new ArrayList<>();
        if (scorecard.getCurrentBatsmanName() != null && !scorecard.getCurrentBatsmanName().isBlank()) {
            currentBatsmen.add(CurrentBatsmanDto.builder()
                    .name(scorecard.getCurrentBatsmanName())
                    .runs(scorecard.getCurrentBatsmanRuns())
                    .balls(scorecard.getCurrentBatsmanBalls())
                    .build());
        }

        CurrentBowlerDto currentBowler = null;
        if (scorecard.getCurrentBowlerName() != null && !scorecard.getCurrentBowlerName().isBlank()) {
            currentBowler = CurrentBowlerDto.builder()
                    .name(scorecard.getCurrentBowlerName())
                    .overs(MatchScorecard.formatBallsToOversNumber(scorecard.getCurrentBowlerBalls()))
                    .wickets(scorecard.getCurrentBowlerWickets())
                    .runs(scorecard.getCurrentBowlerRuns())
                    .build();
        }

        return MatchScorecardResponse.builder()
                .matchId(match.getPublicId())
                .team1(team1Score)
                .team2(team2Score)
                .currentBatsmen(currentBatsmen)
                .currentBowler(currentBowler)
                .status(match.getStatus().name())
                .resultDescription(match.getResultDescription())
                .build();
    }

    private static class TeamStats {
        final Long teamId;
        final String teamName;
        int played = 0;
        int won = 0;
        int lost = 0;
        int tied = 0;
        int noResult = 0;
        int points = 0;
        int runsScored = 0;
        int ballsFaced = 0;
        int runsConceded = 0;
        int ballsBowled = 0;

        TeamStats(Long teamId, String teamName) {
            this.teamId = teamId;
            this.teamName = teamName;
        }

        double calculateNrr() {
            if (ballsFaced == 0 || ballsBowled == 0) {
                return 0.0;
            }
            double oversFaced = ballsFaced / 6.0;
            double oversBowled = ballsBowled / 6.0;
            return (runsScored / oversFaced) - (runsConceded / oversBowled);
        }

        String formatNrr() {
            double nrr = calculateNrr();
            if (Math.abs(nrr) < 0.0001) {
                return "+0.000";
            }
            return String.format("%+.3f", nrr);
        }
    }
}
