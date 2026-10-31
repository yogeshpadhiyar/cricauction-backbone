package com.cricauction.modules.match.service;

import com.cricauction.common.exception.BadRequestException;
import com.cricauction.common.exception.ResourceNotFoundException;
import com.cricauction.modules.match.dto.BallUpdateResponse;
import com.cricauction.modules.match.dto.CreateMatchFixtureRequest;
import com.cricauction.modules.match.dto.RecordBallRequest;
import com.cricauction.modules.match.entity.Match;
import com.cricauction.modules.match.entity.MatchBall;
import com.cricauction.modules.match.entity.MatchScorecard;
import com.cricauction.modules.match.entity.enums.MatchStatus;
import com.cricauction.modules.match.repository.MatchBallRepository;
import com.cricauction.modules.match.repository.MatchRepository;
import com.cricauction.modules.match.repository.MatchScorecardRepository;
import com.cricauction.modules.team.entity.Team;
import com.cricauction.modules.tournament.entity.Tournament;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Enterprise Persistence Service for Module 8 (Matches, Fixtures & Scoring).
 * Executes strictly on the Master DB with minimal transaction hold times.
 * Employs PostgreSQL Transaction Advisory Locks for high-concurrency ball recording serialization.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MatchPersistenceService {

    private final MatchRepository matchRepository;
    private final MatchScorecardRepository matchScorecardRepository;
    private final MatchBallRepository matchBallRepository;
    private final JdbcTemplate jdbcTemplate;

    /**
     * Serializes concurrent scoring operations per match using PostgreSQL Transaction Advisory Locks.
     * Prevents race conditions, scorecard over-increments, or ledger collisions under high concurrency.
     */
    public void acquireMatchAdvisoryLock(Long matchId) {
        try {
            jdbcTemplate.query("SELECT pg_advisory_xact_lock(?)", ps -> ps.setLong(1, matchId), rs -> null);
        } catch (BadSqlGrammarException ex) {
            // Graceful fallback for H2 in-memory test database where PostgreSQL dialect functions don't exist
            log.debug("pg_advisory_xact_lock bypassed on non-PostgreSQL database: {}", ex.getMessage());
        }
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Match createFixture(Tournament tournament, Team team1, Team team2, CreateMatchFixtureRequest request) {
        if (team1.getId().equals(team2.getId())) {
            throw new BadRequestException("INVALID_TEAMS", "A match cannot be scheduled between the same team.");
        }

        String publicId = "match_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);

        Match match = Match.builder()
                .publicId(publicId)
                .tournament(tournament)
                .team1(team1)
                .team2(team2)
                .scheduledAt(request.getScheduledAt() != null ? request.getScheduledAt() : Instant.now())
                .ground(request.getGround() != null ? request.getGround().trim() : null)
                .overs(request.getOvers() != null ? request.getOvers() : 20)
                .status(MatchStatus.UPCOMING)
                .build();

        Match savedMatch = matchRepository.saveAndFlush(match);

        MatchScorecard scorecard = MatchScorecard.builder()
                .match(savedMatch)
                .matchId(savedMatch.getId())
                .team1Runs(0)
                .team1Wickets(0)
                .team1Balls(0)
                .team2Runs(0)
                .team2Wickets(0)
                .team2Balls(0)
                .currentInnings(1)
                .build();

        matchScorecardRepository.save(scorecard);
        log.info("Created match fixture [{}] ({} vs {}) for tournament [{}]",
                savedMatch.getPublicId(), team1.getName(), team2.getName(), tournament.getName());

        return savedMatch;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public BallUpdateResponse recordBall(Long matchId, RecordBallRequest request) {
        // 1. Acquire transaction advisory lock on match
        acquireMatchAdvisoryLock(matchId);

        // 2. Fetch fresh state within the lock
        Match match = matchRepository.findByIdWithTeams(matchId)
                .orElseThrow(() -> new ResourceNotFoundException("MATCH_NOT_FOUND", "Match not found with id: " + matchId));

        if (match.getStatus() == MatchStatus.COMPLETED || match.getStatus() == MatchStatus.ABANDONED) {
            throw new BadRequestException("MATCH_FINISHED", "Cannot record ball: Match is already " + match.getStatus());
        }

        if (match.getStatus() == MatchStatus.UPCOMING) {
            match.setStatus(MatchStatus.LIVE);
            matchRepository.save(match);
        }

        MatchScorecard scorecard = matchScorecardRepository.findById(matchId)
                .orElseGet(() -> {
                    MatchScorecard sc = MatchScorecard.builder()
                            .match(match)
                            .matchId(match.getId())
                            .team1Runs(0)
                            .team1Wickets(0)
                            .team1Balls(0)
                            .team2Runs(0)
                            .team2Wickets(0)
                            .team2Balls(0)
                            .currentInnings(1)
                            .build();
                    return matchScorecardRepository.save(sc);
                });

        int innings = request.getInnings() != null ? request.getInnings() : scorecard.getCurrentInnings();

        // 3. Idempotency Check: if ball is already recorded, return current state idempotently
        Optional<MatchBall> existingBall = matchBallRepository
                .findByMatchIdAndInningsAndOverNumberAndBallNumber(matchId, innings, request.getOver(), request.getBall());
        if (existingBall.isPresent()) {
            log.info("Idempotent ball record hit: matchId={}, innings={}, over={}, ball={}",
                    matchId, innings, request.getOver(), request.getBall());
            return BallUpdateResponse.builder()
                    .matchId(match.getPublicId())
                    .innings(innings)
                    .over(request.getOver())
                    .ball(request.getBall())
                    .runs(existingBall.get().getRuns() + existingBall.get().getExtraRuns())
                    .isWicket(existingBall.get().getIsWicket())
                    .totalRuns(innings == 1 ? scorecard.getTeam1Runs() : scorecard.getTeam2Runs())
                    .totalWickets(innings == 1 ? scorecard.getTeam1Wickets() : scorecard.getTeam2Wickets())
                    .oversFormatted(innings == 1 ? scorecard.getTeam1OversFormatted() : scorecard.getTeam2OversFormatted())
                    .matchStatus(match.getStatus().name())
                    .build();
        }

        // 4. Calculate runs and updates
        int runs = request.getRuns() != null ? request.getRuns() : 0;
        int extraRuns = request.getExtraRuns() != null ? request.getExtraRuns() : 0;
        int totalBallRuns = runs + extraRuns;
        boolean isWicket = Boolean.TRUE.equals(request.getIsWicket());

        if (innings == 1) {
            scorecard.setTeam1Runs(scorecard.getTeam1Runs() + totalBallRuns);
            scorecard.setTeam1Balls(scorecard.getTeam1Balls() + 1);
            if (isWicket) {
                scorecard.setTeam1Wickets(scorecard.getTeam1Wickets() + 1);
            }
        } else {
            scorecard.setTeam2Runs(scorecard.getTeam2Runs() + totalBallRuns);
            scorecard.setTeam2Balls(scorecard.getTeam2Balls() + 1);
            if (isWicket) {
                scorecard.setTeam2Wickets(scorecard.getTeam2Wickets() + 1);
            }
        }

        // Batsman tracking: reset counters on batsman change
        Long parsedBatsmanId = parsePlayerId(request.getBatsmanId());
        String effectiveBatsmanName = request.getBatsmanName();
        if ((effectiveBatsmanName == null || effectiveBatsmanName.isBlank()) && request.getBatsmanId() != null) {
            effectiveBatsmanName = "Batsman " + request.getBatsmanId();
        }

        boolean batsmanChanged = false;
        if (parsedBatsmanId != null && scorecard.getCurrentBatsmanId() != null) {
            batsmanChanged = !parsedBatsmanId.equals(scorecard.getCurrentBatsmanId());
        } else if (effectiveBatsmanName != null && scorecard.getCurrentBatsmanName() != null) {
            batsmanChanged = !effectiveBatsmanName.equalsIgnoreCase(scorecard.getCurrentBatsmanName());
        }

        if (batsmanChanged) {
            scorecard.setCurrentBatsmanRuns(0);
            scorecard.setCurrentBatsmanBalls(0);
        }
        if (effectiveBatsmanName != null && !effectiveBatsmanName.isBlank()) {
            scorecard.setCurrentBatsmanName(effectiveBatsmanName);
        }
        if (parsedBatsmanId != null) {
            scorecard.setCurrentBatsmanId(parsedBatsmanId);
        }
        scorecard.setCurrentBatsmanRuns(scorecard.getCurrentBatsmanRuns() + runs);
        scorecard.setCurrentBatsmanBalls(scorecard.getCurrentBatsmanBalls() + 1);

        // Bowler tracking: reset counters on bowler change
        Long parsedBowlerId = parsePlayerId(request.getBowlerId());
        String effectiveBowlerName = request.getBowlerName();
        if ((effectiveBowlerName == null || effectiveBowlerName.isBlank()) && request.getBowlerId() != null) {
            effectiveBowlerName = "Bowler " + request.getBowlerId();
        }

        boolean bowlerChanged = false;
        if (parsedBowlerId != null && scorecard.getCurrentBowlerId() != null) {
            bowlerChanged = !parsedBowlerId.equals(scorecard.getCurrentBowlerId());
        } else if (effectiveBowlerName != null && scorecard.getCurrentBowlerName() != null) {
            bowlerChanged = !effectiveBowlerName.equalsIgnoreCase(scorecard.getCurrentBowlerName());
        }

        if (bowlerChanged) {
            scorecard.setCurrentBowlerBalls(0);
            scorecard.setCurrentBowlerRuns(0);
            scorecard.setCurrentBowlerWickets(0);
        }
        if (effectiveBowlerName != null && !effectiveBowlerName.isBlank()) {
            scorecard.setCurrentBowlerName(effectiveBowlerName);
        }
        if (parsedBowlerId != null) {
            scorecard.setCurrentBowlerId(parsedBowlerId);
        }
        scorecard.setCurrentBowlerBalls(scorecard.getCurrentBowlerBalls() + 1);
        scorecard.setCurrentBowlerRuns(scorecard.getCurrentBowlerRuns() + totalBallRuns);
        if (isWicket) {
            scorecard.setCurrentBowlerWickets(scorecard.getCurrentBowlerWickets() + 1);
        }

        // 5. Append to Match Ball Audit Ledger
        MatchBall ballRecord = MatchBall.builder()
                .match(match)
                .innings(innings)
                .overNumber(request.getOver())
                .ballNumber(request.getBall())
                .runs(runs)
                .isWicket(isWicket)
                .wicketType(request.getWicketType())
                .extraRuns(extraRuns)
                .extraType(request.getExtraType())
                .batsmanId(parsedBatsmanId)
                .batsmanName(scorecard.getCurrentBatsmanName())
                .bowlerId(parsedBowlerId)
                .bowlerName(scorecard.getCurrentBowlerName())
                .build();
        matchBallRepository.save(ballRecord);

        // 6. Check Innings Transition & Match Completion
        int matchOvers = match.getOvers() != null ? match.getOvers() : 20;
        int maxBalls = matchOvers * 6;

        if (innings == 1) {
            if (scorecard.getTeam1Wickets() >= 10 || scorecard.getTeam1Balls() >= maxBalls) {
                log.info("Innings 1 concluded for match [{}]: {}-{}", match.getPublicId(), scorecard.getTeam1Runs(), scorecard.getTeam1Wickets());
                scorecard.setCurrentInnings(2);
                scorecard.setCurrentBatsmanName(null);
                scorecard.setCurrentBatsmanRuns(0);
                scorecard.setCurrentBatsmanBalls(0);
                scorecard.setCurrentBowlerName(null);
                scorecard.setCurrentBowlerBalls(0);
                scorecard.setCurrentBowlerRuns(0);
                scorecard.setCurrentBowlerWickets(0);
            }
        } else if (innings == 2) {
            boolean targetChased = scorecard.getTeam2Runs() > scorecard.getTeam1Runs();
            boolean allOut = scorecard.getTeam2Wickets() >= 10;
            boolean oversFinished = scorecard.getTeam2Balls() >= maxBalls;

            if (targetChased || allOut || oversFinished) {
                match.setStatus(MatchStatus.COMPLETED);
                if (scorecard.getTeam2Runs() > scorecard.getTeam1Runs()) {
                    match.setWinnerTeamId(match.getTeam2().getId());
                    int wicketsLeft = 10 - scorecard.getTeam2Wickets();
                    match.setResultDescription(match.getTeam2().getName() + " won by " + wicketsLeft + " wickets");
                } else if (scorecard.getTeam1Runs() > scorecard.getTeam2Runs()) {
                    match.setWinnerTeamId(match.getTeam1().getId());
                    int runDiff = scorecard.getTeam1Runs() - scorecard.getTeam2Runs();
                    match.setResultDescription(match.getTeam1().getName() + " won by " + runDiff + " runs");
                } else {
                    match.setWinnerTeamId(null);
                    match.setResultDescription("Match tied");
                }
                matchRepository.save(match);
                log.info("Match [{}] completed. Result: {}", match.getPublicId(), match.getResultDescription());
            }
        }

        matchScorecardRepository.save(scorecard);

        return BallUpdateResponse.builder()
                .matchId(match.getPublicId())
                .innings(innings)
                .over(request.getOver())
                .ball(request.getBall())
                .runs(totalBallRuns)
                .isWicket(isWicket)
                .totalRuns(innings == 1 ? scorecard.getTeam1Runs() : scorecard.getTeam2Runs())
                .totalWickets(innings == 1 ? scorecard.getTeam1Wickets() : scorecard.getTeam2Wickets())
                .oversFormatted(innings == 1 ? scorecard.getTeam1OversFormatted() : scorecard.getTeam2OversFormatted())
                .matchStatus(match.getStatus().name())
                .build();
    }

    private Long parsePlayerId(String idStr) {
        if (idStr == null || idStr.isBlank()) {
            return null;
        }
        String digits = idStr.replaceAll("\\D+", "");
        if (!digits.isEmpty()) {
            try {
                return Long.parseLong(digits);
            } catch (NumberFormatException ignored) {}
        }
        return null;
    }
}
