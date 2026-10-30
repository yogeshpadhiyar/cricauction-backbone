package com.cricauction.modules.tournament.controller;

import com.cricauction.common.api.ApiResponse;
import com.cricauction.modules.tournament.dto.request.CreateTournamentRequest;
import com.cricauction.modules.tournament.dto.request.TournamentSettingsRequest;
import com.cricauction.modules.tournament.dto.request.UpdateTournamentRequest;
import com.cricauction.modules.tournament.dto.response.*;
import com.cricauction.modules.tournament.service.TournamentService;
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

@Slf4j
@RestController
@Validated
@PreAuthorize("isAuthenticated()")
@RequestMapping({"/api/v1/tournaments"})
@RequiredArgsConstructor
@Tag(name = "Tournament Creation & Rules Management", description = "Tournament lifecycle, auction settings, and public landing pages")
public class TournamentController {

    private final TournamentService tournamentService;

    // 18. Create Tournament Draft (Screen 16)
    @PostMapping
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "18. Create Tournament Draft", description = "Stores tournament basic details, location, venue, auction schedule, format, and dates for Organizer")
    public ResponseEntity<ApiResponse<CreateTournamentResponse>> createTournament(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @Valid @RequestBody CreateTournamentRequest request) {
        CreateTournamentResponse response = tournamentService.createTournament(currentUser, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Tournament draft created", response));
    }

    // 19. List Organizer Tournaments
    @GetMapping
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "19. List Organizer Tournaments", description = "Returns tournaments created by organizer, filterable by status (draft, upcoming, ongoing, completed)")
    public ResponseEntity<ApiResponse<List<TournamentSummaryResponse>>> getOrganizerTournaments(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @RequestParam(required = false) @Size(max = 30) String status) {
        List<TournamentSummaryResponse> response = tournamentService.getOrganizerTournaments(currentUser, status);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // 20. Get Tournament Details by ID or Slug
    @GetMapping("/{id}")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "20. Get Tournament Details by ID", description = "Retrieves complete tournament information, schedule, venue, and configured auction rules")
    public ResponseEntity<ApiResponse<TournamentDetailResponse>> getTournamentById(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable("id") @NotBlank @Size(max = 100) String id) {
        TournamentDetailResponse response = tournamentService.getTournamentById(currentUser, id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // 21. Update Tournament Basic Details
    @PatchMapping("/{id}")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "21. Update Tournament Basic Details", description = "Updates tournament dates, venue ground, banner image, or description with Organizer ownership check")
    public ResponseEntity<ApiResponse<TournamentDetailResponse>> updateTournament(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable("id") @NotBlank @Size(max = 100) String id,
            @Valid @RequestBody UpdateTournamentRequest request) {
        TournamentDetailResponse response = tournamentService.updateTournament(currentUser, id, request);
        return ResponseEntity.ok(ApiResponse.success("Tournament details updated", response));
    }

    // 22. Save Tournament Rules & Settings (Screen 17)
    @PutMapping("/{id}/settings")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "22. Save Tournament Rules & Settings", description = "Configures squad boundaries, auction purses, bid timer, auto-start, and match parameters")
    public ResponseEntity<ApiResponse<TournamentSettingsResponse>> saveSettings(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable("id") @NotBlank @Size(max = 100) String id,
            @Valid @RequestBody TournamentSettingsRequest request) {
        TournamentSettingsResponse response = tournamentService.saveSettings(currentUser, id, request);
        return ResponseEntity.ok(ApiResponse.success("Tournament rules and auction settings saved", response));
    }

    // 23. Publish / Launch Tournament
    @PostMapping("/{id}/publish")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "23. Publish / Launch Tournament", description = "Finalizes setup and publishes tournament. Generates public player registration link and captain invite links.")
    public ResponseEntity<ApiResponse<PublishTournamentResponse>> publishTournament(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable("id") @NotBlank @Size(max = 100) String id) {
        PublishTournamentResponse response = tournamentService.publishTournament(currentUser, id);
        return ResponseEntity.ok(ApiResponse.success("Tournament is now live for registrations and invitations", response));
    }

    // 24. Delete / Archive Tournament
    @DeleteMapping("/{id}")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "24. Delete / Archive Tournament", description = "Cancels or archives draft/upcoming tournament with Organizer ownership check")
    public ResponseEntity<ApiResponse<Void>> deleteTournament(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable("id") @NotBlank @Size(max = 100) String id) {
        tournamentService.deleteTournament(currentUser, id);
        return ResponseEntity.ok(ApiResponse.success("Tournament deleted successfully", null));
    }

    // 25. Get Public Tournament Details (Share Link Lookup)
    @GetMapping("/public/{slug}")
    @PreAuthorize("permitAll()")
    @Operation(summary = "25. Get Public Tournament Details", description = "Publicly viewable tournament overview for unregistered players and invited captains before logging in")
    public ResponseEntity<ApiResponse<PublicTournamentResponse>> getPublicTournament(
            @PathVariable("slug") @NotBlank @Size(max = 100) String slug) {
        PublicTournamentResponse response = tournamentService.getPublicTournamentBySlug(slug);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
