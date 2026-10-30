package com.cricauction.modules.team.service.impl;

import com.cricauction.modules.team.event.CaptainApprovalEvent;
import com.cricauction.modules.team.dto.request.*;
import com.cricauction.modules.team.dto.response.*;
import com.cricauction.modules.team.entity.Team;
import com.cricauction.modules.team.repository.SquadMemberRepository;
import com.cricauction.modules.team.service.CaptainNotificationService;
import com.cricauction.modules.team.service.TeamPersistenceService;
import com.cricauction.modules.team.service.TeamQueryService;
import com.cricauction.modules.team.service.TeamService;
import com.cricauction.modules.profile.storage.FileStorageStrategy;
import com.cricauction.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Locale;

/**
 * Enterprise Team Service Orchestrator implementing Strategy 1: "Read-Before-Write".
 *
 * <p>Flow Segregation:
 * 1. <b>Read Phase:</b> Handled by {@link TeamQueryService} under {@code @Transactional(readOnly = true)}
 *    on the Read Replica pool (CricAuction-Replica-Pool). All validations, capacity checks, rules checks,
 *    and duplicate code/name checks execute without holding a Master database connection.
 * 2. <b>Micro-Write Phase:</b> Handled by {@link TeamPersistenceService} under {@code @Transactional}
 *    on the Master pool (CricAuction-Master-Pool) for the absolute minimum duration (sub-millisecond mutations).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TeamServiceImpl implements TeamService {

    private final TeamQueryService teamQueryService;
    private final TeamPersistenceService teamPersistenceService;
    private final SquadMemberRepository squadMemberRepository;
    private final CaptainNotificationService captainNotificationService;
    private final FileStorageStrategy fileStorageStrategy;

    @Override
    public TournamentTeamsResponse getTournamentTeams(UserPrincipal currentUser, String tournamentIdOrSlug) {
        return teamQueryService.getTournamentTeams(currentUser, tournamentIdOrSlug);
    }

    @Override
    public AddTeamResponse addTeam(UserPrincipal currentUser, String tournamentIdOrSlug, AddTeamRequest request) {
        // 1. Read Phase on READ REPLICA: Validate ownership, settings, and capacity
        Team teamToCreate = teamQueryService.validateTeamCreation(currentUser, tournamentIdOrSlug, request);

        // 2. Micro-Write Phase on MASTER DB: Atomic team creation
        Team saved = teamPersistenceService.createTeam(teamToCreate);

        log.info("Team [{}] created in Tournament [{}] with inviteCode [{}]",
                saved.getName(), teamToCreate.getTournament().getPublicId(), saved.getInviteCode());

        return AddTeamResponse.builder()
                .id(saved.getId())
                .name(saved.getName())
                .status(saved.getStatus())
                .inviteCode(saved.getInviteCode())
                .remainingBudget(saved.getRemainingBudget())
                .build();
    }

    @Override
    public TeamSummaryResponse updateTeam(UserPrincipal currentUser, String tournamentIdOrSlug, Long teamId, UpdateTeamRequest request) {
        // 1. Read Phase on READ REPLICA: Validate organizer role and team existence
        Team team = teamQueryService.validateUpdateTeam(currentUser, tournamentIdOrSlug, teamId);

        // 2. Micro-Write Phase on MASTER DB
        Team updated = teamPersistenceService.updateTeam(team, request);
        int squadCount = (int) squadMemberRepository.countByTeamId(updated.getId());

        log.info("Team [{}] details updated by Organizer [{}]", updated.getId(), currentUser.getId());
        return TeamSummaryResponse.fromEntity(updated, squadCount, true);
    }

    @Override
    public void deleteTeam(UserPrincipal currentUser, String tournamentIdOrSlug, Long teamId) {
        // 1. Read Phase on READ REPLICA: Validate ownership and squad vacancy
        Team team = teamQueryService.validateDeleteTeam(currentUser, tournamentIdOrSlug, teamId);

        // 2. Micro-Write Phase on MASTER DB
        teamPersistenceService.deleteTeam(team);
        log.info("Team [{}] deleted from Tournament by Organizer [{}]", teamId, currentUser.getId());
    }

    @Override
    public InviteCaptainResponse sendCaptainInvite(UserPrincipal currentUser, String tournamentIdOrSlug, Long teamId, InviteCaptainRequest request) {
        // 1. Read Phase on READ REPLICA
        Team team = teamQueryService.validateSendCaptainInvite(currentUser, tournamentIdOrSlug, teamId);

        // 2. Micro-Write Phase on MASTER DB
        Team updated = teamPersistenceService.setTeamStatusInvited(team);

        String inviteLink = "https://cricauction.app/join/" + updated.getInviteCode();
        log.info("Captain invite dispatched via [{}] for Team [{}] with invite link: [{}]",
                request != null ? request.getChannel() : "Default", updated.getName(), inviteLink);

        return InviteCaptainResponse.builder()
                .teamId(updated.getId())
                .status("Invited")
                .inviteLink(inviteLink)
                .build();
    }

    @Override
    public JoinTeamResponse joinTeam(UserPrincipal currentUser, JoinTeamRequest request) {
        // 1. Read Phase on READ REPLICA: Validate invite code and check existing claim
        Team team = teamQueryService.validateJoinTeam(currentUser, request);
        String tournamentPublicId = team.getTournament().getPublicId();
        String tournamentName = team.getTournament().getName();

        // 2. Micro-Write Phase on MASTER DB: Claim team, grant role, inject squad member
        Team saved = teamPersistenceService.executeJoinTeam(team, currentUser, request.getCaptainMobile());

        log.info("Captain claim confirmed: User [{}] is now Captain of Team [{}] ({}) in Tournament [{}]",
                currentUser.getId(), saved.getName(), saved.getId(), tournamentPublicId);

        return JoinTeamResponse.builder()
                .teamId(saved.getId())
                .teamName(saved.getName())
                .tournamentId(tournamentPublicId)
                .tournamentName(tournamentName)
                .role("captain")
                .status("Accepted")
                .build();
    }

    @Override
    public JoinTeamResponse selfRegisterCaptain(UserPrincipal currentUser, String tournamentIdOrSlug, SelfRegisterCaptainRequest request) {
        // 1. Read Phase on READ REPLICA: Verify registration rules, duplicate captain check, and capacity
        TeamQueryService.SelfRegisterValidationData val = teamQueryService.validateSelfRegisterCaptain(currentUser, tournamentIdOrSlug, request);
        String tournamentPublicId = val.getTournament().getPublicId();
        String tournamentName = val.getTournament().getName();

        // 2. Micro-Write Phase on MASTER DB: Atomic registration and optional squad injection
        Team saved;
        if (request != null && request.getTeamId() != null) {
            saved = teamPersistenceService.executeSelfRegisterExistingTeam(val.getTeam(), currentUser, request, val.getInitialStatus(), val.isAutoApproved());
        } else {
            saved = teamPersistenceService.executeSelfRegisterNewTeam(val.getTeam(), currentUser, val.isAutoApproved());
        }

        if (val.isAutoApproved()) {
            log.info("Captain self-registration AUTO-APPROVED: User [{}] is now Captain of Team [{}] ({}) in Tournament [{}]",
                    currentUser.getId(), saved.getName(), saved.getId(), tournamentPublicId);
        } else {
            log.info("Captain self-registration SUBMITTED FOR ORGANIZER REVIEW: User [{}] applied for Team [{}] ({}) in Tournament [{}]",
                    currentUser.getId(), saved.getName(), saved.getId(), tournamentPublicId);
        }

        return JoinTeamResponse.builder()
                .teamId(saved.getId())
                .teamName(saved.getName())
                .tournamentId(tournamentPublicId)
                .tournamentName(tournamentName)
                .role("captain")
                .status(val.getInitialStatus())
                .build();
    }

    @Override
    public List<TeamSummaryResponse> getPendingCaptainRequests(UserPrincipal currentUser, String tournamentIdOrSlug) {
        return teamQueryService.getPendingCaptainRequests(currentUser, tournamentIdOrSlug);
    }

    @Override
    public CaptainApprovalResponse reviewCaptainApplication(UserPrincipal currentUser, String tournamentIdOrSlug,
                                                           Long teamId, CaptainApprovalRequest request) {
        // 1. Read Phase on READ REPLICA: Validate ownership, action, and application state
        Team team = teamQueryService.validateReviewCaptainApplication(currentUser, tournamentIdOrSlug, teamId, request);
        String action = request.getAction().trim().toUpperCase(Locale.ENGLISH);

        Long captainUserId = team.getCaptainId();
        String captainName = team.getCaptainName();
        Long tournamentId = team.getTournament().getId();
        String tournamentPublicId = team.getTournament().getPublicId();
        String tournamentName = team.getTournament().getName();
        String captainMobile = team.getCaptainMobile();
        String teamName = team.getName();

        // 2. Micro-Write Phase on MASTER DB: Atomic approval or rejection
        Team saved = teamPersistenceService.executeReviewCaptainApplication(team, action, captainUserId, captainName);

        // 3. Dispatch notification event outside DB transaction
        boolean isApproved = "APPROVED".equals(action);
        CaptainApprovalEvent event = CaptainApprovalEvent.builder()
                .tournamentId(tournamentId)
                .tournamentPublicId(tournamentPublicId)
                .tournamentName(tournamentName)
                .teamId(saved.getId())
                .teamName(teamName)
                .captainUserId(captainUserId)
                .captainName(captainName)
                .captainMobile(captainMobile)
                .status(isApproved ? "APPROVED" : "REJECTED")
                .reason(request.getReason() != null ? request.getReason().trim() : (isApproved ? "Approved by organizer" : "Rejected by organizer"))
                .build();
        captainNotificationService.notifyCaptainApprovalStatus(event);

        log.info("Organizer [{}] {} Captain application for User [{}] Team [{}] in Tournament [{}]",
                currentUser.getId(), action, captainUserId, teamName, tournamentPublicId);

        return CaptainApprovalResponse.builder()
                .teamId(saved.getId())
                .teamName(teamName)
                .captainUserId(captainUserId)
                .status(saved.getStatus())
                .message(isApproved ? "Captain application has been approved successfully." : "Captain application has been rejected.")
                .notificationDispatched(true)
                .build();
    }

    @Override
    public TeamSquadResponse getTeamSquad(UserPrincipal currentUser, Long teamId) {
        return teamQueryService.getTeamSquad(currentUser, teamId);
    }

    @Override
    public TeamSummaryResponse updateTeamProfile(UserPrincipal currentUser, Long teamId,
                                                TeamProfileUpdateRequest request, MultipartFile logo) {
        // 1. Read Phase on READ REPLICA: Validate access permissions and team existence
        Team team = teamQueryService.validateUpdateTeamProfile(currentUser, teamId);
        boolean isOrganizer = currentUser != null && team.getTournament().getOrganizerId().equals(currentUser.getId());

        // 2. File Storage Upload outside DB transaction (0 DB connections held during I/O)
        String uploadedUrl = null;
        if (logo != null && !logo.isEmpty()) {
            uploadedUrl = fileStorageStrategy.uploadFile(logo, "teams/" + teamId);
            log.info("Uploaded new logo for Team [{}]: {}", team.getId(), uploadedUrl);
        }

        // 3. Micro-Write Phase on MASTER DB
        Team updated = teamPersistenceService.updateTeamBranding(team, request, uploadedUrl);
        int squadCount = (int) squadMemberRepository.countByTeamId(updated.getId());

        log.info("Team [{}] branding updated by User [{}]", updated.getId(), currentUser.getId());
        return TeamSummaryResponse.fromEntity(updated, squadCount, isOrganizer);
    }
}
