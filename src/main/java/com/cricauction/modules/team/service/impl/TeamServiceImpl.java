package com.cricauction.modules.team.service.impl;

import com.cricauction.common.exception.BadRequestException;
import com.cricauction.common.exception.ConflictException;
import com.cricauction.common.exception.ForbiddenException;
import com.cricauction.common.exception.ResourceNotFoundException;
import com.cricauction.modules.auth.entity.enums.UserRole;
import com.cricauction.modules.auth.repository.RoleRepository;
import com.cricauction.modules.auth.repository.UserRepository;
import com.cricauction.modules.profile.storage.FileStorageStrategy;
import com.cricauction.modules.team.dto.request.*;
import com.cricauction.modules.team.dto.response.*;
import com.cricauction.modules.team.entity.SquadMember;
import com.cricauction.modules.team.entity.Team;
import com.cricauction.modules.team.event.CaptainApprovalEvent;
import com.cricauction.modules.team.repository.SquadMemberRepository;
import com.cricauction.modules.team.repository.TeamRepository;
import com.cricauction.modules.team.service.CaptainNotificationService;
import com.cricauction.modules.team.service.TeamService;
import com.cricauction.modules.tournament.entity.Tournament;
import com.cricauction.modules.tournament.entity.TournamentSettings;
import com.cricauction.modules.tournament.repository.TournamentRepository;
import com.cricauction.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TeamServiceImpl implements TeamService {

    private final TeamRepository teamRepository;
    private final SquadMemberRepository squadMemberRepository;
    private final TournamentRepository tournamentRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final FileStorageStrategy fileStorageStrategy;
    private final CaptainNotificationService captainNotificationService;

    @Override
    @Transactional(readOnly = true)
    public TournamentTeamsResponse getTournamentTeams(UserPrincipal currentUser, String tournamentIdOrSlug) {
        Tournament tournament = findTournamentByIdentifier(tournamentIdOrSlug);
        List<Team> teams = teamRepository.findByTournamentIdOrderByIdAsc(tournament.getId());

        int totalTeamsLimit = (tournament.getSettings() != null && tournament.getSettings().getTotalTeams() > 0)
                ? tournament.getSettings().getTotalTeams()
                : Math.max(10, teams.size());

        int assignedCaptains = (int) teams.stream()
                .filter(t -> t.getCaptainId() != null || "Accepted".equalsIgnoreCase(t.getStatus()))
                .count();

        boolean isOrganizer = currentUser != null && tournament.getOrganizerId().equals(currentUser.getId());

        List<TeamSummaryResponse> teamResponses = teams.stream()
                .map(t -> {
                    int squadCount = (int) squadMemberRepository.countByTeamId(t.getId());
                    return TeamSummaryResponse.fromEntity(t, squadCount, isOrganizer);
                })
                .toList();

        return TournamentTeamsResponse.builder()
                .totalTeams(totalTeamsLimit)
                .assignedCaptains(assignedCaptains)
                .teams(teamResponses)
                .build();
    }

    @Override
    @Transactional
    public AddTeamResponse addTeam(UserPrincipal currentUser, String tournamentIdOrSlug, AddTeamRequest request) {
        validateOrganizerRole(currentUser);
        Tournament tournament = findTournamentByIdentifier(tournamentIdOrSlug);
        validateTournamentOwnership(tournament, currentUser);

        TournamentSettings settings = tournament.getSettings();
        int maxTeams = (settings != null && settings.getTotalTeams() > 0) ? settings.getTotalTeams() : 10;
        long currentTeamCount = teamRepository.countByTournamentId(tournament.getId());

        if (currentTeamCount >= maxTeams) {
            throw new BadRequestException("TEAM_CAPACITY_REACHED",
                    "Tournament team capacity reached (" + maxTeams + " teams maximum). Please increase team count in tournament rules to add more teams.");
        }

        Long budget = (settings != null) ? settings.getTeamBudget() : 1000000L;
        String inviteCode = generateUniqueInviteCode(tournament.getSlug(), currentTeamCount + 1);

        String shortCode = (request.getShortCode() != null && !request.getShortCode().isBlank())
                ? request.getShortCode().trim().toUpperCase(Locale.ENGLISH)
                : generateShortCode(request.getName());

        Team team = Team.builder()
                .tournament(tournament)
                .name(request.getName().trim())
                .shortCode(shortCode)
                .captainName(request.getCaptainName() != null ? request.getCaptainName().trim() : "")
                .captainMobile(request.getCaptainMobile() != null ? request.getCaptainMobile().trim() : "")
                .initialBudget(budget)
                .remainingBudget(budget)
                .inviteCode(inviteCode)
                .status("Pending")
                .build();

        Team saved = teamRepository.save(team);
        log.info("Team [{}] created in Tournament [{}] with inviteCode [{}]",
                saved.getName(), tournament.getPublicId(), saved.getInviteCode());

        return AddTeamResponse.builder()
                .id(saved.getId())
                .name(saved.getName())
                .status(saved.getStatus())
                .inviteCode(saved.getInviteCode())
                .remainingBudget(saved.getRemainingBudget())
                .build();
    }

    @Override
    @Transactional
    public TeamSummaryResponse updateTeam(UserPrincipal currentUser, String tournamentIdOrSlug, Long teamId, UpdateTeamRequest request) {
        validateOrganizerRole(currentUser);
        Tournament tournament = findTournamentByIdentifier(tournamentIdOrSlug);
        validateTournamentOwnership(tournament, currentUser);

        Team team = teamRepository.findByIdAndTournamentId(teamId, tournament.getId())
                .orElseThrow(() -> new ResourceNotFoundException("TEAM_NOT_FOUND", "Team [" + teamId + "] not found in tournament"));

        if (request.getName() != null && !request.getName().isBlank()) {
            team.setName(request.getName().trim());
        }
        if (request.getCaptainName() != null) {
            team.setCaptainName(request.getCaptainName().trim());
        }
        if (request.getCaptainMobile() != null) {
            team.setCaptainMobile(request.getCaptainMobile().trim());
        }
        if (request.getShortCode() != null && !request.getShortCode().isBlank()) {
            team.setShortCode(request.getShortCode().trim().toUpperCase(Locale.ENGLISH));
        }

        Team updated = teamRepository.save(team);
        int squadCount = (int) squadMemberRepository.countByTeamId(updated.getId());

        log.info("Team [{}] details updated by Organizer [{}]", updated.getId(), currentUser.getId());
        return TeamSummaryResponse.fromEntity(updated, squadCount, true);
    }

    @Override
    @Transactional
    public void deleteTeam(UserPrincipal currentUser, String tournamentIdOrSlug, Long teamId) {
        validateOrganizerRole(currentUser);
        Tournament tournament = findTournamentByIdentifier(tournamentIdOrSlug);
        validateTournamentOwnership(tournament, currentUser);

        Team team = teamRepository.findByIdAndTournamentId(teamId, tournament.getId())
                .orElseThrow(() -> new ResourceNotFoundException("TEAM_NOT_FOUND", "Team [" + teamId + "] not found in tournament"));

        long squadCount = squadMemberRepository.countByTeamId(teamId);
        if (squadCount > 0) {
            throw new BadRequestException("TEAM_HAS_SQUAD", "Cannot remove a team that already has drafted squad members.");
        }

        teamRepository.delete(team);
        log.info("Team [{}] deleted from Tournament [{}] by Organizer [{}]", teamId, tournament.getPublicId(), currentUser.getId());
    }

    @Override
    @Transactional
    public InviteCaptainResponse sendCaptainInvite(UserPrincipal currentUser, String tournamentIdOrSlug, Long teamId, InviteCaptainRequest request) {
        validateOrganizerRole(currentUser);
        Tournament tournament = findTournamentByIdentifier(tournamentIdOrSlug);
        validateTournamentOwnership(tournament, currentUser);

        Team team = teamRepository.findByIdAndTournamentId(teamId, tournament.getId())
                .orElseThrow(() -> new ResourceNotFoundException("TEAM_NOT_FOUND", "Team [" + teamId + "] not found in tournament"));

        team.setStatus("Invited");
        teamRepository.save(team);

        String inviteLink = "https://cricauction.app/join/" + team.getInviteCode();
        log.info("Captain invite dispatched via [{}] for Team [{}] with invite link: [{}]",
                request.getChannel(), team.getName(), inviteLink);

        return InviteCaptainResponse.builder()
                .teamId(team.getId())
                .status("Invited")
                .inviteLink(inviteLink)
                .build();
    }

    @Override
    @Transactional
    public JoinTeamResponse joinTeam(UserPrincipal currentUser, JoinTeamRequest request) {
        if (request.getInviteCode() == null || request.getInviteCode().isBlank()) {
            throw new BadRequestException("INVALID_INVITE_CODE", "Invite code cannot be empty.");
        }

        Team team = teamRepository.findByInviteCode(request.getInviteCode().trim())
                .orElseThrow(() -> new ResourceNotFoundException("TEAM_NOT_FOUND", "Invalid or expired team invite code."));

        if ("Accepted".equalsIgnoreCase(team.getStatus()) && team.getCaptainId() != null
                && !team.getCaptainId().equals(currentUser.getId())) {
            throw new ConflictException("TEAM_ALREADY_CLAIMED", "This team already has an active captain assigned.");
        }

        team.setCaptainId(currentUser.getId());
        team.setStatus("Accepted");
        team.setCaptainName(currentUser.getFullName());
        if (currentUser.getMobile() != null && !currentUser.getMobile().isBlank()) {
            team.setCaptainMobile(currentUser.getMobile());
        } else if (request.getCaptainMobile() != null && !request.getCaptainMobile().isBlank()) {
            team.setCaptainMobile(request.getCaptainMobile().trim());
        }

        teamRepository.save(team);

        // Multi-role RBAC: Grant CAPTAIN role to claiming user
        userRepository.findById(currentUser.getId()).ifPresent(user -> {
            roleRepository.findByName(UserRole.CAPTAIN).ifPresent(capRole -> {
                if (!user.getRoles().contains(capRole)) {
                    user.addRole(capRole);
                    userRepository.save(user);
                    log.info("User [{}] granted CAPTAIN role upon claiming team [{}]", user.getId(), team.getId());
                }
            });
        });

        // Ensure team captain exists in squad members roster
        if (squadMemberRepository.findByTeamIdAndIsCaptainTrue(team.getId()).isEmpty()) {
            SquadMember captainMember = SquadMember.builder()
                    .team(team)
                    .playerUserId(currentUser.getId())
                    .playerName(currentUser.getFullName())
                    .playingRole("All-Rounder")
                    .purchasePrice(0L)
                    .isCaptain(true)
                    .build();
            squadMemberRepository.save(captainMember);
        }

        log.info("Captain claim confirmed: User [{}] is now Captain of Team [{}] ({}) in Tournament [{}]",
                currentUser.getId(), team.getName(), team.getId(), team.getTournament().getPublicId());

        return JoinTeamResponse.builder()
                .teamId(team.getId())
                .teamName(team.getName())
                .tournamentId(team.getTournament().getPublicId())
                .tournamentName(team.getTournament().getName())
                .role("captain")
                .status("Accepted")
                .build();
    }

    @Override
    @Transactional
    public JoinTeamResponse selfRegisterCaptain(UserPrincipal currentUser, String tournamentIdOrSlug, SelfRegisterCaptainRequest request) {
        Tournament tournament = findTournamentByIdentifier(tournamentIdOrSlug);

        // 1. Verify tournament allows captain self-registration
        TournamentSettings settings = tournament.getSettings();
        String mode = (settings != null && settings.getCaptainRegistrationMode() != null)
                ? settings.getCaptainRegistrationMode()
                : "INVITE_ONLY";

        if (!"BOTH".equalsIgnoreCase(mode) && !"OPEN".equalsIgnoreCase(mode)) {
            throw new ForbiddenException("CAPTAIN_INVITE_ONLY",
                    "This tournament only permits invited captains. Self-registration is disabled by the organizer.");
        }

        // 2. Prevent a user from captaining multiple teams in the same tournament
        if (teamRepository.existsByTournamentIdAndCaptainId(tournament.getId(), currentUser.getId())) {
            throw new ConflictException("ALREADY_A_CAPTAIN",
                    "You are already registered as a captain for a team in this tournament.");
        }

        // Determine organizer approval policy
        String approvalMode = (settings != null && settings.getCaptainApprovalMode() != null)
                ? settings.getCaptainApprovalMode()
                : "ORGANIZER_APPROVAL";
        boolean isAutoApproved = "AUTO".equalsIgnoreCase(approvalMode);
        String initialStatus = isAutoApproved ? "Accepted" : "Pending_Approval";

        Team team;
        // 3. Mode A: Claiming an existing unassigned team
        if (request != null && request.getTeamId() != null) {
            team = teamRepository.findByIdAndTournamentId(request.getTeamId(), tournament.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("TEAM_NOT_FOUND", "Team [" + request.getTeamId() + "] not found in tournament"));

            if ("Accepted".equalsIgnoreCase(team.getStatus()) && team.getCaptainId() != null) {
                throw new ConflictException("TEAM_ALREADY_CLAIMED", "This team already has an active captain assigned.");
            }

            team.setCaptainId(currentUser.getId());
            team.setStatus(initialStatus);
            team.setCaptainName(currentUser.getFullName());
            if (currentUser.getMobile() != null && !currentUser.getMobile().isBlank()) {
                team.setCaptainMobile(currentUser.getMobile());
            } else if (request.getCaptainMobile() != null && !request.getCaptainMobile().isBlank()) {
                team.setCaptainMobile(request.getCaptainMobile().trim());
            }
            if (request.getTeamName() != null && !request.getTeamName().isBlank()) {
                team.setName(request.getTeamName().trim());
            }
            if (request.getShortCode() != null && !request.getShortCode().isBlank()) {
                team.setShortCode(request.getShortCode().trim().toUpperCase(Locale.ENGLISH));
            }
        } else {
            // Mode B: Creating and registering a new team as captain directly
            if (request == null || request.getTeamName() == null || request.getTeamName().isBlank()) {
                throw new BadRequestException("INVALID_TEAM_NAME", "Team name is required for registering a new team.");
            }

            int maxTeams = (settings != null && settings.getTotalTeams() > 0) ? settings.getTotalTeams() : 10;
            long currentTeamCount = teamRepository.countByTournamentId(tournament.getId());
            if (currentTeamCount >= maxTeams) {
                throw new BadRequestException("TEAM_CAPACITY_REACHED",
                        "Tournament team capacity reached (" + maxTeams + " teams maximum). No more teams can register.");
            }

            Long budget = (settings != null) ? settings.getTeamBudget() : 1000000L;
            String inviteCode = generateUniqueInviteCode(tournament.getSlug(), currentTeamCount + 1);
            String shortCode = (request.getShortCode() != null && !request.getShortCode().isBlank())
                    ? request.getShortCode().trim().toUpperCase(Locale.ENGLISH)
                    : generateShortCode(request.getTeamName());

            String mobile = (currentUser.getMobile() != null && !currentUser.getMobile().isBlank())
                    ? currentUser.getMobile()
                    : (request.getCaptainMobile() != null ? request.getCaptainMobile().trim() : "");

            team = Team.builder()
                    .tournament(tournament)
                    .name(request.getTeamName().trim())
                    .shortCode(shortCode)
                    .captainId(currentUser.getId())
                    .captainName(currentUser.getFullName())
                    .captainMobile(mobile)
                    .initialBudget(budget)
                    .remainingBudget(budget)
                    .inviteCode(inviteCode)
                    .status(initialStatus)
                    .build();
        }

        Team saved = teamRepository.save(team);

        // 4. If Auto-approved: Grant CAPTAIN role and inject squad member immediately
        if (isAutoApproved) {
            userRepository.findById(currentUser.getId()).ifPresent(user -> {
                roleRepository.findByName(UserRole.CAPTAIN).ifPresent(capRole -> {
                    if (!user.getRoles().contains(capRole)) {
                        user.addRole(capRole);
                        userRepository.save(user);
                        log.info("User [{}] granted CAPTAIN role upon auto-approved team [{}]", user.getId(), saved.getId());
                    }
                });
            });

            if (squadMemberRepository.findByTeamIdAndIsCaptainTrue(saved.getId()).isEmpty()) {
                SquadMember captainMember = SquadMember.builder()
                        .team(saved)
                        .playerUserId(currentUser.getId())
                        .playerName(currentUser.getFullName())
                        .playingRole("All-Rounder")
                        .purchasePrice(0L)
                        .isCaptain(true)
                        .build();
                squadMemberRepository.save(captainMember);
            }
            log.info("Captain self-registration AUTO-APPROVED: User [{}] is now Captain of Team [{}] ({}) in Tournament [{}]",
                    currentUser.getId(), saved.getName(), saved.getId(), tournament.getPublicId());
        } else {
            log.info("Captain self-registration SUBMITTED FOR ORGANIZER REVIEW: User [{}] applied for Team [{}] ({}) in Tournament [{}]",
                    currentUser.getId(), saved.getName(), saved.getId(), tournament.getPublicId());
        }

        return JoinTeamResponse.builder()
                .teamId(saved.getId())
                .teamName(saved.getName())
                .tournamentId(tournament.getPublicId())
                .tournamentName(tournament.getName())
                .role("captain")
                .status(initialStatus)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<TeamSummaryResponse> getPendingCaptainRequests(UserPrincipal currentUser, String tournamentIdOrSlug) {
        validateOrganizerRole(currentUser);
        Tournament tournament = findTournamentByIdentifier(tournamentIdOrSlug);
        validateTournamentOwnership(tournament, currentUser);

        List<Team> pendingTeams = teamRepository.findByTournamentIdAndStatusOrderByIdAsc(tournament.getId(), "Pending_Approval");

        return pendingTeams.stream()
                .map(t -> {
                    int squadCount = (int) squadMemberRepository.countByTeamId(t.getId());
                    return TeamSummaryResponse.fromEntity(t, squadCount, true);
                })
                .toList();
    }

    @Override
    @Transactional
    public CaptainApprovalResponse reviewCaptainApplication(UserPrincipal currentUser, String tournamentIdOrSlug,
                                                           Long teamId, CaptainApprovalRequest request) {
        validateOrganizerRole(currentUser);
        Tournament tournament = findTournamentByIdentifier(tournamentIdOrSlug);
        validateTournamentOwnership(tournament, currentUser);

        if (request == null || request.getAction() == null || request.getAction().isBlank()) {
            throw new BadRequestException("INVALID_ACTION", "Approval action must be APPROVED or REJECTED.");
        }

        String action = request.getAction().trim().toUpperCase(Locale.ENGLISH);
        if (!"APPROVED".equals(action) && !"REJECTED".equals(action)) {
            throw new BadRequestException("INVALID_ACTION", "Approval action must be APPROVED or REJECTED.");
        }

        Team team = teamRepository.findByIdAndTournamentId(teamId, tournament.getId())
                .orElseThrow(() -> new ResourceNotFoundException("TEAM_NOT_FOUND", "Team [" + teamId + "] not found in tournament"));

        if (!"Pending_Approval".equalsIgnoreCase(team.getStatus())) {
            throw new ConflictException("INVALID_APPLICATION_STATUS",
                    "Team application is not currently pending review. Current status: " + team.getStatus());
        }

        Long captainUserId = team.getCaptainId();
        String captainName = team.getCaptainName();

        if ("APPROVED".equals(action)) {
            team.setStatus("Accepted");
            teamRepository.save(team);

            // Grant CAPTAIN role to applicant
            if (captainUserId != null) {
                userRepository.findById(captainUserId).ifPresent(user -> {
                    roleRepository.findByName(UserRole.CAPTAIN).ifPresent(capRole -> {
                        if (!user.getRoles().contains(capRole)) {
                            user.addRole(capRole);
                            userRepository.save(user);
                            log.info("User [{}] granted CAPTAIN role upon Organizer approval of Team [{}]", user.getId(), team.getId());
                        }
                    });
                });

                // Add to squad roster
                if (squadMemberRepository.findByTeamIdAndIsCaptainTrue(team.getId()).isEmpty()) {
                    SquadMember captainMember = SquadMember.builder()
                            .team(team)
                            .playerUserId(captainUserId)
                            .playerName(captainName != null && !captainName.isBlank() ? captainName : "Captain")
                            .playingRole("All-Rounder")
                            .purchasePrice(0L)
                            .isCaptain(true)
                            .build();
                    squadMemberRepository.save(captainMember);
                }
            }

            // Dispatch notification event
            CaptainApprovalEvent event = CaptainApprovalEvent.builder()
                    .tournamentId(tournament.getId())
                    .tournamentPublicId(tournament.getPublicId())
                    .tournamentName(tournament.getName())
                    .teamId(team.getId())
                    .teamName(team.getName())
                    .captainUserId(captainUserId)
                    .captainName(captainName)
                    .captainMobile(team.getCaptainMobile())
                    .status("APPROVED")
                    .reason(request.getReason() != null ? request.getReason().trim() : "Approved by tournament organizer")
                    .build();
            captainNotificationService.notifyCaptainApprovalStatus(event);

            log.info("Organizer [{}] APPROVED Captain application for User [{}] Team [{}] in Tournament [{}]",
                    currentUser.getId(), captainUserId, team.getName(), tournament.getPublicId());

            return CaptainApprovalResponse.builder()
                    .teamId(team.getId())
                    .teamName(team.getName())
                    .captainUserId(captainUserId)
                    .status("Accepted")
                    .message("Captain application has been approved successfully.")
                    .notificationDispatched(true)
                    .build();
        } else {
            // REJECTED
            team.setStatus("Rejected");
            teamRepository.save(team);

            CaptainApprovalEvent event = CaptainApprovalEvent.builder()
                    .tournamentId(tournament.getId())
                    .tournamentPublicId(tournament.getPublicId())
                    .tournamentName(tournament.getName())
                    .teamId(team.getId())
                    .teamName(team.getName())
                    .captainUserId(captainUserId)
                    .captainName(captainName)
                    .captainMobile(team.getCaptainMobile())
                    .status("REJECTED")
                    .reason(request.getReason() != null ? request.getReason().trim() : "Application rejected by organizer")
                    .build();
            captainNotificationService.notifyCaptainApprovalStatus(event);

            log.info("Organizer [{}] REJECTED Captain application for User [{}] Team [{}] in Tournament [{}]",
                    currentUser.getId(), captainUserId, team.getName(), tournament.getPublicId());

            return CaptainApprovalResponse.builder()
                    .teamId(team.getId())
                    .teamName(team.getName())
                    .captainUserId(captainUserId)
                    .status("Rejected")
                    .message("Captain application has been rejected.")
                    .notificationDispatched(true)
                    .build();
        }
    }

    @Override
    @Transactional(readOnly = true)
    public TeamSquadResponse getTeamSquad(UserPrincipal currentUser, Long teamId) {
        Team team = teamRepository.findByIdWithSquad(teamId)
                .orElseThrow(() -> new ResourceNotFoundException("TEAM_NOT_FOUND", "Team [" + teamId + "] not found"));

        List<SquadMember> members = squadMemberRepository.findByTeamIdOrderByIdAsc(teamId);

        long spentPurse = members.stream()
                .mapToLong(SquadMember::getPurchasePrice)
                .sum();

        long remainingPurse = Math.max(0L, team.getInitialBudget() - spentPurse);

        int maxSquad = (team.getTournament().getSettings() != null)
                ? team.getTournament().getSettings().getSquadMaximum()
                : 15;

        List<SquadPlayerResponse> playerResponses = members.stream()
                .map(SquadPlayerResponse::fromEntity)
                .toList();

        return TeamSquadResponse.builder()
                .teamId(team.getId())
                .teamName(team.getName())
                .shortCode(team.getShortCode())
                .logoUrl(team.getLogoUrl())
                .totalPurse(team.getInitialBudget())
                .spentPurse(spentPurse)
                .remainingPurse(remainingPurse)
                .squadCount(members.size())
                .maxSquad(maxSquad)
                .players(playerResponses)
                .build();
    }

    @Override
    @Transactional
    public TeamSummaryResponse updateTeamProfile(UserPrincipal currentUser, Long teamId,
                                                TeamProfileUpdateRequest request, MultipartFile logo) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new ResourceNotFoundException("TEAM_NOT_FOUND", "Team [" + teamId + "] not found"));

        validateTeamManagementAccess(team, currentUser);

        if (logo != null && !logo.isEmpty()) {
            String uploadedUrl = fileStorageStrategy.uploadFile(logo, "teams/" + teamId);
            team.setLogoUrl(uploadedUrl);
            log.info("Uploaded new logo for Team [{}]: {}", team.getId(), uploadedUrl);
        }

        if (request != null) {
            if (request.getLogoUrl() != null && !request.getLogoUrl().isBlank()) {
                team.setLogoUrl(request.getLogoUrl().trim());
            }
            if (request.getName() != null && !request.getName().isBlank()) {
                team.setName(request.getName().trim());
            }
            if (request.getShortCode() != null && !request.getShortCode().isBlank()) {
                team.setShortCode(request.getShortCode().trim().toUpperCase(Locale.ENGLISH));
            }
        }

        Team updated = teamRepository.save(team);
        int squadCount = (int) squadMemberRepository.countByTeamId(updated.getId());

        log.info("Team [{}] branding updated by User [{}]", updated.getId(), currentUser.getId());
        boolean isOrganizer = currentUser != null && team.getTournament().getOrganizerId().equals(currentUser.getId());
        return TeamSummaryResponse.fromEntity(updated, squadCount, isOrganizer);
    }

    private Tournament findTournamentByIdentifier(String idOrSlug) {
        if (idOrSlug == null || idOrSlug.isBlank()) {
            throw new BadRequestException("INVALID_IDENTIFIER", "Tournament identifier cannot be empty.");
        }
        String trimmed = idOrSlug.trim();

        return tournamentRepository.findByPublicIdOrSlugWithSettings(trimmed)
                .or(() -> {
                    if (trimmed.matches("^\\d+$")) {
                        return tournamentRepository.findByIdWithSettings(Long.parseLong(trimmed));
                    }
                    return java.util.Optional.empty();
                })
                .orElseThrow(() -> new ResourceNotFoundException("TOURNAMENT_NOT_FOUND", "Tournament not found for: " + idOrSlug));
    }

    private void validateOrganizerRole(UserPrincipal currentUser) {
        if (!currentUser.isOrganizer()) {
            throw new ForbiddenException("ORGANIZER_ROLE_REQUIRED", "Access denied: Only registered ORGANIZERS can manage tournament teams.");
        }
    }

    private void validateTournamentOwnership(Tournament tournament, UserPrincipal currentUser) {
        if (!tournament.getOrganizerId().equals(currentUser.getId())) {
            throw new ForbiddenException("TOURNAMENT_ACCESS_DENIED", "Access denied: You are not the owner of this tournament.");
        }
    }

    private void validateTeamManagementAccess(Team team, UserPrincipal currentUser) {
        boolean isCaptain = team.getCaptainId() != null && team.getCaptainId().equals(currentUser.getId());
        boolean isOrganizer = team.getTournament().getOrganizerId().equals(currentUser.getId());

        if (!isCaptain && !isOrganizer) {
            throw new ForbiddenException("TEAM_ACCESS_DENIED", "Access denied: Only the team captain or tournament organizer can update team branding.");
        }
    }

    private String generateUniqueInviteCode(String tournamentSlug, long teamIndex) {
        String base = (tournamentSlug != null && !tournamentSlug.isBlank()) ? tournamentSlug : "spl";
        String randomSuffix = UUID.randomUUID().toString().replace("-", "").substring(0, 6);
        return base + "-team" + teamIndex + "-" + randomSuffix;
    }

    private String generateShortCode(String teamName) {
        if (teamName == null || teamName.isBlank()) {
            return "TEAM";
        }
        String[] words = teamName.trim().split("\\s+");
        if (words.length >= 2) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < Math.min(words.length, 3); i++) {
                if (!words[i].isEmpty()) {
                    sb.append(Character.toUpperCase(words[i].charAt(0)));
                }
            }
            return sb.toString();
        }
        String single = teamName.replaceAll("[^a-zA-Z]", "");
        return (single.length() >= 3 ? single.substring(0, 3) : single).toUpperCase(Locale.ENGLISH);
    }
}
