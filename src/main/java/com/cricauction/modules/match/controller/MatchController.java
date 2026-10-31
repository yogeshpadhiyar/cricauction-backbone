package com.cricauction.modules.match.controller;

import com.cricauction.common.api.ApiResponse;
import com.cricauction.modules.match.dto.*;
import com.cricauction.modules.match.service.MatchService;
import com.cricauction.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Enterprise REST Controller exposing Module 8: Matches, Fixtures & Scoring (APIs 54 - 58).
 */
@Slf4j
@Validated
@RestController
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
@Tag(name = "Module 8: Matches, Fixtures & Scoring", description = "Endpoints for fixture scheduling, live ball-by-ball scoring, scorecards, and standings")
public class MatchController {

    private final MatchService matchService;

    /**
     * API 54: List Tournament Fixtures
     */
    @GetMapping({"/api/v1/tournaments/{tournamentId}/matches", "/tournaments/{tournamentId}/matches"})
    @Operation(summary = "API 54: List Tournament Fixtures", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<List<MatchFixtureResponse>>> listTournamentFixtures(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable("tournamentId") @NotBlank @Size(max = 100) String tournamentId) {
        List<MatchFixtureResponse> fixtures = matchService.getTournamentFixtures(currentUser, tournamentId);
        return ResponseEntity.ok(ApiResponse.success(fixtures));
    }

    /**
     * API 55: Create Match Fixture
     */
    @PostMapping({"/api/v1/tournaments/{tournamentId}/matches", "/tournaments/{tournamentId}/matches"})
    @Operation(summary = "API 55: Create Match Fixture", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<MatchFixtureResponse>> createMatchFixture(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable("tournamentId") @NotBlank @Size(max = 100) String tournamentId,
            @Valid @RequestBody CreateMatchFixtureRequest request) {
        MatchFixtureResponse fixture = matchService.createFixture(currentUser, tournamentId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("Fixture scheduled", fixture));
    }

    /**
     * API 56: Get Match Scorecard
     */
    @GetMapping({"/api/v1/matches/{matchId}", "/matches/{matchId}"})
    @Operation(summary = "API 56: Get Match Scorecard", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<MatchScorecardResponse>> getMatchScorecard(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable("matchId") @NotBlank @Size(max = 100) String matchId) {
        MatchScorecardResponse scorecard = matchService.getMatchScorecard(currentUser, matchId);
        return ResponseEntity.ok(ApiResponse.success(scorecard));
    }

    /**
     * API 57: Live Match Ball-by-Ball Update
     */
    @PatchMapping({"/api/v1/matches/{matchId}/score", "/matches/{matchId}/score"})
    @Operation(summary = "API 57: Live Match Ball-by-Ball Update", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<BallUpdateResponse>> recordBall(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable("matchId") @NotBlank @Size(max = 100) String matchId,
            @Valid @RequestBody RecordBallRequest request) {
        BallUpdateResponse result = matchService.recordBall(currentUser, matchId, request);
        return ResponseEntity.ok(ApiResponse.success("Ball recorded", result));
    }

    /**
     * API 58: Get Tournament Standings (Points Table)
     */
    @GetMapping({"/api/v1/tournaments/{tournamentId}/standings", "/tournaments/{tournamentId}/standings"})
    @Operation(summary = "API 58: Get Tournament Standings (Points Table)", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<List<TournamentStandingsResponse>>> getTournamentStandings(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable("tournamentId") @NotBlank @Size(max = 100) String tournamentId) {
        List<TournamentStandingsResponse> standings = matchService.getTournamentStandings(currentUser, tournamentId);
        return ResponseEntity.ok(ApiResponse.success(standings));
    }
}

