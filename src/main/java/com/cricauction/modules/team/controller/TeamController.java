package com.cricauction.modules.team.controller;

import com.cricauction.common.api.ApiResponse;
import com.cricauction.modules.team.dto.request.*;
import com.cricauction.modules.team.dto.response.*;
import com.cricauction.modules.team.service.TeamService;
import com.cricauction.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

@Slf4j
@RestController
@Validated
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
@Tag(name = "Module 5: Team & Captain Management", description = "APIs 26–33 for team rosters, captain invites, squad purse, and branding")
public class TeamController {

    private final TeamService teamService;

    /**
     * API 26: List Tournament Teams
     */
    @Operation(summary = "List Tournament Teams (API 26)", security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/api/v1/tournaments/{tournamentId}/teams")
    public ResponseEntity<ApiResponse<TournamentTeamsResponse>> getTournamentTeams(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable("tournamentId") @NotBlank @Size(max = 100) String tournamentId) {
        TournamentTeamsResponse response = teamService.getTournamentTeams(currentUser, tournamentId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * API 27: Add New Team
     */
    @Operation(summary = "Add New Team (API 27)", security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping("/api/v1/tournaments/{tournamentId}/teams")
    public ResponseEntity<ApiResponse<AddTeamResponse>> addTeam(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable("tournamentId") @NotBlank @Size(max = 100) String tournamentId,
            @Valid @RequestBody AddTeamRequest request) {
        AddTeamResponse response = teamService.addTeam(currentUser, tournamentId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Team added successfully", response));
    }

    /**
     * API 28: Edit Team & Captain Assignment
     */
    @Operation(summary = "Edit Team & Captain Assignment (API 28)", security = @SecurityRequirement(name = "bearerAuth"))
    @RequestMapping(
            value = "/api/v1/tournaments/{tournamentId}/teams/{teamId}",
            method = {RequestMethod.PATCH, RequestMethod.PUT}
    )
    public ResponseEntity<ApiResponse<TeamSummaryResponse>> updateTeam(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable("tournamentId") @NotBlank @Size(max = 100) String tournamentId,
            @PathVariable("teamId") @Positive(message = "Team ID must be a positive number") Long teamId,
            @Valid @RequestBody UpdateTeamRequest request) {
        TeamSummaryResponse response = teamService.updateTeam(currentUser, tournamentId, teamId, request);
        return ResponseEntity.ok(ApiResponse.success("Team details updated successfully", response));
    }

    /**
     * API 29: Delete Team
     */
    @Operation(summary = "Delete Team (API 29)", security = @SecurityRequirement(name = "bearerAuth"))
    @DeleteMapping("/api/v1/tournaments/{tournamentId}/teams/{teamId}")
    public ResponseEntity<ApiResponse<Void>> deleteTeam(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable("tournamentId") @NotBlank @Size(max = 100) String tournamentId,
            @PathVariable("teamId") @Positive(message = "Team ID must be a positive number") Long teamId) {
        teamService.deleteTeam(currentUser, tournamentId, teamId);
        return ResponseEntity.ok(ApiResponse.success("Team removed from tournament successfully", null));
    }

    /**
     * API 30: Send Captain Invitation (WhatsApp / SMS / Email)
     */
    @Operation(summary = "Send Captain Invitation (API 30)", security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping("/api/v1/tournaments/{tournamentId}/teams/{teamId}/invite")
    public ResponseEntity<ApiResponse<InviteCaptainResponse>> sendCaptainInvite(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable("tournamentId") @NotBlank @Size(max = 100) String tournamentId,
            @PathVariable("teamId") @Positive(message = "Team ID must be a positive number") Long teamId,
            @Valid @RequestBody(required = false) InviteCaptainRequest request) {
        InviteCaptainRequest req = request != null ? request : InviteCaptainRequest.builder().build();
        InviteCaptainResponse response = teamService.sendCaptainInvite(currentUser, tournamentId, teamId, req);
        return ResponseEntity.ok(ApiResponse.success("Invite dispatched successfully", response));
    }

    /**
     * API 31: Captain Accepts Invite
     */
    @Operation(summary = "Captain Accepts Invite (API 31)", security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping("/api/v1/teams/join")
    public ResponseEntity<ApiResponse<JoinTeamResponse>> joinTeam(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @Valid @RequestBody JoinTeamRequest request) {
        JoinTeamResponse response = teamService.joinTeam(currentUser, request);
        return ResponseEntity.ok(ApiResponse.success("Captain join confirmed successfully", response));
    }

    /**
     * API 31b: Captain Self-Registration (Open / Public Tournament)
     */
    @Operation(summary = "Captain Self-Registration in Public Tournament (API 31b)", security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping("/api/v1/tournaments/{tournamentId}/teams/register-captain")
    public ResponseEntity<ApiResponse<JoinTeamResponse>> selfRegisterCaptain(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable("tournamentId") @NotBlank @Size(max = 100) String tournamentId,
            @Valid @RequestBody SelfRegisterCaptainRequest request) {
        JoinTeamResponse response = teamService.selfRegisterCaptain(currentUser, tournamentId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Captain registration confirmed successfully", response));
    }

    /**
     * API 31c: List Pending Captain Applications (Organizer Review Queue)
     */
    @Operation(summary = "List Pending Captain Applications (API 31c)", security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/api/v1/tournaments/{tournamentId}/teams/captain-requests")
    public ResponseEntity<ApiResponse<List<TeamSummaryResponse>>> getPendingCaptainRequests(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable("tournamentId") @NotBlank @Size(max = 100) String tournamentId) {
        List<TeamSummaryResponse> response = teamService.getPendingCaptainRequests(currentUser, tournamentId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * API 31d: Organizer Approves or Rejects Captain Application
     */
    @Operation(summary = "Organizer Approves or Rejects Captain Application (API 31d)", security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping("/api/v1/tournaments/{tournamentId}/teams/{teamId}/captain-approval")
    public ResponseEntity<ApiResponse<CaptainApprovalResponse>> reviewCaptainApplication(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable("tournamentId") @NotBlank @Size(max = 100) String tournamentId,
            @PathVariable("teamId") @Positive(message = "Team ID must be a positive number") Long teamId,
            @Valid @RequestBody CaptainApprovalRequest request) {
        CaptainApprovalResponse response = teamService.reviewCaptainApplication(currentUser, tournamentId, teamId, request);
        return ResponseEntity.ok(ApiResponse.success(response.getMessage(), response));
    }

    /**
     * API 32: Get Team Squad Roster & Purse
     */
    @Operation(summary = "Get Team Squad Roster & Purse (API 32)", security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/api/v1/teams/{teamId}/squad")
    public ResponseEntity<ApiResponse<TeamSquadResponse>> getTeamSquad(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable("teamId") @Positive(message = "Team ID must be a positive number") Long teamId) {
        TeamSquadResponse response = teamService.getTeamSquad(currentUser, teamId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * API 33: Captain Updates Team Branding (JSON)
     */
    @Operation(summary = "Captain Updates Team Branding (API 33)", security = @SecurityRequirement(name = "bearerAuth"))
    @RequestMapping(
            value = "/api/v1/teams/{teamId}/profile",
            method = {RequestMethod.PATCH, RequestMethod.PUT},
            consumes = {MediaType.APPLICATION_JSON_VALUE}
    )
    public ResponseEntity<ApiResponse<TeamSummaryResponse>> updateTeamProfileJson(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable("teamId") @Positive(message = "Team ID must be a positive number") Long teamId,
            @Valid @RequestBody TeamProfileUpdateRequest request) {
        TeamSummaryResponse response = teamService.updateTeamProfile(currentUser, teamId, request, null);
        return ResponseEntity.ok(ApiResponse.success("Team profile updated successfully", response));
    }

    /**
     * API 33: Captain Updates Team Branding with Logo File (Multipart)
     */
    @Operation(summary = "Captain Updates Team Branding with Logo File (API 33 Multipart)", security = @SecurityRequirement(name = "bearerAuth"))
    @RequestMapping(
            value = "/api/v1/teams/{teamId}/profile",
            method = {RequestMethod.PATCH, RequestMethod.PUT},
            consumes = {MediaType.MULTIPART_FORM_DATA_VALUE}
    )
    public ResponseEntity<ApiResponse<TeamSummaryResponse>> updateTeamProfileMultipart(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable("teamId") @Positive(message = "Team ID must be a positive number") Long teamId,
            @RequestPart(value = "logo", required = false) MultipartFile logo,
            @RequestParam(value = "shortCode", required = false) @Size(max = 10, message = "Short code cannot exceed 10 characters") String shortCode,
            @RequestParam(value = "name", required = false) @Size(max = 100, message = "Team name cannot exceed 100 characters") String name) {
        TeamProfileUpdateRequest request = TeamProfileUpdateRequest.builder()
                .name(name != null ? name.trim() : null)
                .shortCode(shortCode != null ? shortCode.trim() : null)
                .build();
        TeamSummaryResponse response = teamService.updateTeamProfile(currentUser, teamId, request, logo);
        return ResponseEntity.ok(ApiResponse.success("Team profile updated successfully", response));
    }
}
