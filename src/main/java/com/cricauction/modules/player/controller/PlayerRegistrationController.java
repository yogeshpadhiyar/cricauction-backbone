package com.cricauction.modules.player.controller;

import com.cricauction.common.api.ApiResponse;
import com.cricauction.modules.player.dto.request.PlayerRegistrationRequest;
import com.cricauction.modules.player.dto.request.UpdatePlayerBasePriceRequest;
import com.cricauction.modules.player.dto.request.UpdatePlayerStatusRequest;
import com.cricauction.modules.player.dto.response.PlayerCapacityStatsResponse;
import com.cricauction.modules.player.dto.response.PlayerListResponse;
import com.cricauction.modules.player.dto.response.PlayerRegistrationResponse;
import com.cricauction.modules.player.dto.response.PlayerRegistrationStatusResponse;
import com.cricauction.modules.player.service.PlayerRegistrationService;
import com.cricauction.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Slf4j
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/tournaments/{tournamentId}/players")
@PreAuthorize("isAuthenticated()")
@Tag(name = "Module 6: Player Registration & Capacity Management", description = "Endpoints for player enrollment, capacity tracking, approval workflows, and base price management")
public class PlayerRegistrationController {

    private final PlayerRegistrationService playerRegistrationService;

    /**
     * API 34: Player Registers for Tournament
     */
    @Operation(summary = "Player Registers for Tournament (API 34)", security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<PlayerRegistrationResponse>> registerPlayer(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable("tournamentId") @NotBlank @Size(max = 100) String tournamentId,
            @Valid @RequestBody PlayerRegistrationRequest request
    ) {
        PlayerRegistrationResponse response = playerRegistrationService.registerPlayer(currentUser, tournamentId, request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Registration submitted successfully", response));
    }

    /**
     * API 35: Player Registration Status Tracking
     */
    @Operation(summary = "Player Registration Status Tracking (API 35)", security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/my-status")
    public ResponseEntity<ApiResponse<PlayerRegistrationStatusResponse>> getMyRegistrationStatus(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable("tournamentId") @NotBlank @Size(max = 100) String tournamentId
    ) {
        PlayerRegistrationStatusResponse response = playerRegistrationService.getMyRegistrationStatus(currentUser, tournamentId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * API 36: Player Withdraws from Tournament
     */
    @Operation(summary = "Player Withdraws from Tournament (API 36)", security = @SecurityRequirement(name = "bearerAuth"))
    @DeleteMapping("/withdraw")
    public ResponseEntity<ApiResponse<Void>> withdrawFromTournament(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable("tournamentId") @NotBlank @Size(max = 100) String tournamentId
    ) {
        playerRegistrationService.withdrawFromTournament(currentUser, tournamentId);
        return ResponseEntity.ok(ApiResponse.success("You have successfully withdrawn from this tournament", null));
    }

    /**
     * API 37: Organizer Lists Registered Players
     */
    @Operation(summary = "Organizer Lists Registered Players (API 37)", security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping
    public ResponseEntity<ApiResponse<PlayerListResponse>> listRegisteredPlayers(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable("tournamentId") @NotBlank @Size(max = 100) String tournamentId,
            @RequestParam(value = "filter", defaultValue = "All") @Size(max = 50, message = "Filter must not exceed 50 characters") String filter,
            @RequestParam(value = "search", required = false) @Size(max = 100, message = "Search query must not exceed 100 characters") String search,
            @RequestParam(value = "page", defaultValue = "1") @Min(value = 1, message = "Page must be greater than or equal to 1") int page,
            @RequestParam(value = "limit", defaultValue = "20") @Min(value = 1, message = "Limit must be greater than or equal to 1") @Max(value = 100, message = "Limit must not exceed 100") int limit
    ) {
        PlayerListResponse response = playerRegistrationService.listRegisteredPlayers(currentUser, tournamentId, filter, search, page, limit);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * API 38: Player Registration Capacity Stats
     */
    @Operation(summary = "Player Registration Capacity Stats (API 38)", security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<PlayerCapacityStatsResponse>> getCapacityStats(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable("tournamentId") @NotBlank @Size(max = 100) String tournamentId
    ) {
        PlayerCapacityStatsResponse response = playerRegistrationService.getRegistrationCapacityStats(currentUser, tournamentId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * API 39: Organizer Approves / Rejects Player
     */
    @Operation(summary = "Organizer Approves / Rejects Player (API 39)", security = @SecurityRequirement(name = "bearerAuth"))
    @PatchMapping("/{playerId}/status")
    public ResponseEntity<ApiResponse<Void>> updatePlayerStatus(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable("tournamentId") @NotBlank @Size(max = 100) String tournamentId,
            @PathVariable("playerId") @Positive(message = "Player ID must be positive") Long playerId,
            @Valid @RequestBody UpdatePlayerStatusRequest request
    ) {
        playerRegistrationService.updatePlayerStatus(currentUser, tournamentId, playerId, request);
        return ResponseEntity.ok(ApiResponse.success("Player status updated", null));
    }

    /**
     * API 40: Organizer Sets / Updates Player Base Price
     */
    @Operation(summary = "Organizer Sets / Updates Player Base Price (API 40)", security = @SecurityRequirement(name = "bearerAuth"))
    @PatchMapping("/{playerId}/base-price")
    public ResponseEntity<ApiResponse<Void>> updatePlayerBasePrice(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable("tournamentId") @NotBlank @Size(max = 100) String tournamentId,
            @PathVariable("playerId") @Positive(message = "Player ID must be positive") Long playerId,
            @Valid @RequestBody UpdatePlayerBasePriceRequest request
    ) {
        playerRegistrationService.updatePlayerBasePrice(currentUser, tournamentId, playerId, request);
        return ResponseEntity.ok(ApiResponse.success("Player base price updated", null));
    }
}
