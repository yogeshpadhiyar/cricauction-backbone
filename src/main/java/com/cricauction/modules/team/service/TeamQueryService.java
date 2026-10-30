package com.cricauction.modules.team.service;

import com.cricauction.common.exception.BadRequestException;
import com.cricauction.common.exception.ConflictException;
import com.cricauction.common.exception.ForbiddenException;
import com.cricauction.common.exception.ResourceNotFoundException;
import com.cricauction.modules.auth.repository.RoleRepository;
import com.cricauction.modules.auth.repository.UserRepository;
import com.cricauction.modules.team.dto.request.AddTeamRequest;
import com.cricauction.modules.team.dto.request.CaptainApprovalRequest;
import com.cricauction.modules.team.dto.request.JoinTeamRequest;
import com.cricauction.modules.team.dto.request.SelfRegisterCaptainRequest;
import com.cricauction.modules.team.dto.response.*;
import com.cricauction.modules.team.entity.SquadMember;
import com.cricauction.modules.team.entity.Team;
import com.cricauction.modules.team.repository.SquadMemberRepository;
import com.cricauction.modules.team.repository.TeamRepository;
import com.cricauction.modules.team.repository.TeamSquadCountProjection;
import com.cricauction.modules.tournament.entity.Tournament;
import com.cricauction.modules.tournament.entity.TournamentSettings;
import com.cricauction.modules.tournament.repository.TournamentRepository;
import com.cricauction.modules.tournament.repository.TournamentSettingsRepository;
import com.cricauction.security.UserPrincipal;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Dedicated Read Query Service for Module 5 (Team & Captain Management).
 * Executes strictly under @Transactional(readOnly = true) to route 100% of queries
 * to the Read Replica pool (CricAuction-Replica-Pool).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TeamQueryService {

    private final TournamentRepository tournamentRepository;
    private final TournamentSettingsRepository settingsRepository;
    private final TeamRepository teamRepository;
    private final SquadMemberRepository squadMemberRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    @Getter
    @Builder
    public static class SelfRegisterValidationData {
        private final Tournament tournament;
        private final Team team;
        private final String initialStatus;
        private final boolean isAutoApproved;
    }

    public TournamentTeamsResponse getTournamentTeams(UserPrincipal currentUser, String tournamentIdOrSlug) {
        Tournament tournament = findTournamentByIdentifier(tournamentIdOrSlug);
        boolean isOrganizer = currentUser != null && tournament.getOrganizerId().equals(currentUser.getId());

        List<Team> teams = teamRepository.findByTournamentIdOrderByIdAsc(tournament.getId());
        TournamentSettings settings = tournament.getSettings();
        int totalTeamsLimit = settings != null && settings.getTotalTeams() > 0 ? settings.getTotalTeams() : 10;
        int assignedCaptains = (int) teams.stream().filter(t -> t.getCaptainId() != null).count();

        List<TeamSummaryResponse> teamResponses;
        if (teams.isEmpty()) {
            teamResponses = List.of();
        } else {
            List<Long> teamIds = teams.stream().map(Team::getId).toList();
            Map<Long, Integer> countMap = squadMemberRepository.countSquadMembersByTeamIds(teamIds)
                    .stream()
                    .collect(Collectors.toMap(
                            TeamSquadCountProjection::getTeamId,
                            p -> p.getSquadCount() != null ? p.getSquadCount().intValue() : 0,
                            (c1, c2) -> c1
                    ));

            teamResponses = teams.stream()
                    .map(t -> TeamSummaryResponse.fromEntity(t, countMap.getOrDefault(t.getId(), 0), isOrganizer))
                    .toList();
        }

        return TournamentTeamsResponse.builder()
                .totalTeams(totalTeamsLimit)
                .assignedCaptains(assignedCaptains)
                .teams(teamResponses)
                .build();
    }

    public List<TeamSummaryResponse> getPendingCaptainRequests(UserPrincipal currentUser, String tournamentIdOrSlug) {
        validateOrganizerRole(currentUser);
        Tournament tournament = findTournamentByIdentifier(tournamentIdOrSlug);
        validateTournamentOwnership(tournament, currentUser);

        List<Team> pendingTeams = teamRepository.findByTournamentIdAndStatusOrderByIdAsc(tournament.getId(), "Pending_Approval");
        if (pendingTeams.isEmpty()) {
            return List.of();
        }

        List<Long> teamIds = pendingTeams.stream().map(Team::getId).toList();
        Map<Long, Integer> countMap = squadMemberRepository.countSquadMembersByTeamIds(teamIds)
                .stream()
                .collect(Collectors.toMap(
                        TeamSquadCountProjection::getTeamId,
                        p -> p.getSquadCount() != null ? p.getSquadCount().intValue() : 0,
                        (c1, c2) -> c1
                ));

        return pendingTeams.stream()
                .map(t -> TeamSummaryResponse.fromEntity(t, countMap.getOrDefault(t.getId(), 0), true))
                .toList();
    }

    public TeamSquadResponse getTeamSquad(UserPrincipal currentUser, Long teamId) {
        Team team = teamRepository.findByIdWithSquad(teamId)
                .orElseThrow(() -> new ResourceNotFoundException("TEAM_NOT_FOUND", "Team [" + teamId + "] not found"));

        List<SquadMember> members = squadMemberRepository.findByTeamIdOrderByIdAsc(teamId);
        long spentPurse = members.stream().mapToLong(SquadMember::getPurchasePrice).sum();
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

    public Team validateTeamCreation(UserPrincipal currentUser, String tournamentIdOrSlug, AddTeamRequest request) {
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

        return Team.builder()
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
    }

    public Team validateUpdateTeam(UserPrincipal currentUser, String tournamentIdOrSlug, Long teamId) {
        validateOrganizerRole(currentUser);
        Tournament tournament = findTournamentByIdentifier(tournamentIdOrSlug);
        validateTournamentOwnership(tournament, currentUser);

        return teamRepository.findByIdAndTournamentIdWithTournament(teamId, tournament.getId())
                .orElseThrow(() -> new ResourceNotFoundException("TEAM_NOT_FOUND", "Team [" + teamId + "] not found in tournament"));
    }

    public Team validateDeleteTeam(UserPrincipal currentUser, String tournamentIdOrSlug, Long teamId) {
        validateOrganizerRole(currentUser);
        Tournament tournament = findTournamentByIdentifier(tournamentIdOrSlug);
        validateTournamentOwnership(tournament, currentUser);

        Team team = teamRepository.findByIdAndTournamentIdWithTournament(teamId, tournament.getId())
                .orElseThrow(() -> new ResourceNotFoundException("TEAM_NOT_FOUND", "Team [" + teamId + "] not found in tournament"));

        long squadCount = squadMemberRepository.countByTeamId(teamId);
        if (squadCount > 0) {
            throw new BadRequestException("TEAM_HAS_SQUAD", "Cannot remove a team that already has drafted squad members.");
        }

        return team;
    }

    public Team validateSendCaptainInvite(UserPrincipal currentUser, String tournamentIdOrSlug, Long teamId) {
        validateOrganizerRole(currentUser);
        Tournament tournament = findTournamentByIdentifier(tournamentIdOrSlug);
        validateTournamentOwnership(tournament, currentUser);

        return teamRepository.findByIdAndTournamentIdWithTournament(teamId, tournament.getId())
                .orElseThrow(() -> new ResourceNotFoundException("TEAM_NOT_FOUND", "Team [" + teamId + "] not found in tournament"));
    }

    public Team validateJoinTeam(UserPrincipal currentUser, JoinTeamRequest request) {
        if (request.getInviteCode() == null || request.getInviteCode().isBlank()) {
            throw new BadRequestException("INVALID_INVITE_CODE", "Invite code cannot be empty.");
        }

        Team team = teamRepository.findByInviteCodeWithTournament(request.getInviteCode().trim())
                .orElseThrow(() -> new ResourceNotFoundException("TEAM_NOT_FOUND", "Invalid or expired team invite code."));

        if ("Accepted".equalsIgnoreCase(team.getStatus()) && team.getCaptainId() != null
                && !team.getCaptainId().equals(currentUser.getId())) {
            throw new ConflictException("TEAM_ALREADY_CLAIMED", "This team already has an active captain assigned.");
        }

        // Prevent a user from captaining multiple teams in the same tournament
        if (teamRepository.existsByTournamentIdAndCaptainId(team.getTournament().getId(), currentUser.getId())
                && (team.getCaptainId() == null || !team.getCaptainId().equals(currentUser.getId()))) {
            throw new ConflictException("ALREADY_A_CAPTAIN",
                    "You are already registered as a captain for a team in this tournament.");
        }

        return team;
    }

    public SelfRegisterValidationData validateSelfRegisterCaptain(UserPrincipal currentUser, String tournamentIdOrSlug, SelfRegisterCaptainRequest request) {
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
        if (request != null && request.getTeamId() != null) {
            team = teamRepository.findByIdAndTournamentIdWithTournament(request.getTeamId(), tournament.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("TEAM_NOT_FOUND", "Team [" + request.getTeamId() + "] not found in tournament"));

            if ("Accepted".equalsIgnoreCase(team.getStatus()) && team.getCaptainId() != null) {
                throw new ConflictException("TEAM_ALREADY_CLAIMED", "This team already has an active captain assigned.");
            }
        } else {
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

        return SelfRegisterValidationData.builder()
                .tournament(tournament)
                .team(team)
                .initialStatus(initialStatus)
                .isAutoApproved(isAutoApproved)
                .build();
    }

    public Team validateReviewCaptainApplication(UserPrincipal currentUser, String tournamentIdOrSlug, Long teamId, CaptainApprovalRequest request) {
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

        Team team = teamRepository.findByIdAndTournamentIdWithTournament(teamId, tournament.getId())
                .orElseThrow(() -> new ResourceNotFoundException("TEAM_NOT_FOUND", "Team [" + teamId + "] not found in tournament"));

        if (!"Pending_Approval".equalsIgnoreCase(team.getStatus())) {
            throw new ConflictException("INVALID_APPLICATION_STATUS",
                    "Team application is not currently pending review. Current status: " + team.getStatus());
        }

        return team;
    }

    public Team validateUpdateTeamProfile(UserPrincipal currentUser, Long teamId) {
        Team team = teamRepository.findByIdWithTournament(teamId)
                .orElseThrow(() -> new ResourceNotFoundException("TEAM_NOT_FOUND", "Team [" + teamId + "] not found"));

        validateTeamManagementAccess(team, currentUser);
        return team;
    }

    public Tournament findTournamentByIdentifier(String idOrSlug) {
        if (idOrSlug == null || idOrSlug.isBlank()) {
            throw new BadRequestException("INVALID_IDENTIFIER", "Tournament identifier cannot be empty.");
        }
        String trimmed = idOrSlug.trim();
        return tournamentRepository.findByPublicIdOrSlugWithSettings(trimmed)
                .or(() -> {
                    if (trimmed.matches("^\\d+$")) {
                        return tournamentRepository.findByIdWithSettings(Long.parseLong(trimmed));
                    }
                    return Optional.empty();
                })
                .orElseThrow(() -> new ResourceNotFoundException("TOURNAMENT_NOT_FOUND", "Tournament not found for: " + idOrSlug));
    }

    public void validateOrganizerRole(UserPrincipal currentUser) {
        if (currentUser == null || !currentUser.isOrganizer()) {
            throw new ForbiddenException("ORGANIZER_ROLE_REQUIRED", "Access denied: Only registered ORGANIZERS can manage tournament teams.");
        }
    }

    public void validateTournamentOwnership(Tournament tournament, UserPrincipal currentUser) {
        if (currentUser == null || !tournament.getOrganizerId().equals(currentUser.getId())) {
            throw new ForbiddenException("TOURNAMENT_ACCESS_DENIED", "Access denied: You are not the owner of this tournament.");
        }
    }

    public void validateTeamManagementAccess(Team team, UserPrincipal currentUser) {
        if (currentUser == null) {
            throw new ForbiddenException("AUTHENTICATION_REQUIRED", "Authentication is required.");
        }
        boolean isCaptain = team.getCaptainId() != null && team.getCaptainId().equals(currentUser.getId());
        boolean isOrganizer = team.getTournament().getOrganizerId().equals(currentUser.getId());

        if (!isCaptain && !isOrganizer) {
            throw new ForbiddenException("ACCESS_DENIED", "Only the assigned captain or tournament organizer can update team branding.");
        }
    }

    public String generateUniqueInviteCode(String tournamentSlug, long sequence) {
        String base = toCleanSlug(tournamentSlug) + "-team" + sequence;
        String salt = UUID.randomUUID().toString().substring(0, 6);
        return base + "-" + salt;
    }

    public String generateShortCode(String teamName) {
        if (teamName == null || teamName.isBlank()) return "TM";
        String[] words = teamName.trim().split("\\s+");
        if (words.length >= 2) {
            StringBuilder sb = new StringBuilder();
            for (String w : words) {
                if (!w.isBlank()) sb.append(Character.toUpperCase(w.charAt(0)));
                if (sb.length() >= 4) break;
            }
            return sb.toString();
        }
        String clean = teamName.replaceAll("[^a-zA-Z]", "");
        return (clean.length() >= 3 ? clean.substring(0, 3) : clean).toUpperCase(Locale.ENGLISH);
    }

    private String toCleanSlug(String input) {
        if (input == null) return "tournament";
        String normalized = Normalizer.normalize(input.trim(), Normalizer.Form.NFD);
        String slug = normalized.replaceAll("[^\\w-]", "").replaceAll("-+", "-").toLowerCase(Locale.ENGLISH);
        return slug.isBlank() ? "tourn" : slug;
    }
}
