package com.cricauction.modules.team.service;

import com.cricauction.common.exception.ConflictException;
import com.cricauction.common.exception.ResourceNotFoundException;
import com.cricauction.modules.auth.entity.enums.UserRole;
import com.cricauction.modules.auth.repository.RoleRepository;
import com.cricauction.modules.auth.repository.UserRepository;
import com.cricauction.modules.team.dto.request.SelfRegisterCaptainRequest;
import com.cricauction.modules.team.dto.request.TeamProfileUpdateRequest;
import com.cricauction.modules.team.dto.request.UpdateTeamRequest;
import com.cricauction.modules.team.entity.SquadMember;
import com.cricauction.modules.team.entity.Team;
import com.cricauction.modules.team.repository.SquadMemberRepository;
import com.cricauction.modules.team.repository.TeamRepository;
import com.cricauction.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/**
 * Dedicated Micro-Write Persistence Service for Module 5 (Team & Captain Management).
 * Executes strictly under @Transactional (readOnly = false) on the Master pool
 * (CricAuction-Master-Pool) for the absolute minimum duration (sub-millisecond mutations).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class TeamPersistenceService {

    private final TeamRepository teamRepository;
    private final SquadMemberRepository squadMemberRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    public Team createTeam(Team team) {
        return teamRepository.save(team);
    }

    public Team updateTeam(Team team, UpdateTeamRequest request) {
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

        return teamRepository.save(team);
    }

    public void deleteTeam(Team team) {
        teamRepository.delete(team);
    }

    public Team setTeamStatusInvited(Team team) {
        team.setStatus("Invited");
        return teamRepository.save(team);
    }

    public Team executeJoinTeam(Team team, UserPrincipal currentUser, String captainMobile) {
        // Master lock / verification: Ensure another user did not claim the team during the race window
        Team masterTeam = teamRepository.findById(team.getId())
                .orElseThrow(() -> new ResourceNotFoundException("TEAM_NOT_FOUND", "Team no longer exists."));

        if ("Accepted".equalsIgnoreCase(masterTeam.getStatus()) && masterTeam.getCaptainId() != null
                && !masterTeam.getCaptainId().equals(currentUser.getId())) {
            throw new ConflictException("TEAM_ALREADY_CLAIMED", "This team already has an active captain assigned.");
        }

        // Prevent a user from captaining multiple teams in the same tournament
        if (teamRepository.existsByTournamentIdAndCaptainId(masterTeam.getTournament().getId(), currentUser.getId())
                && (masterTeam.getCaptainId() == null || !masterTeam.getCaptainId().equals(currentUser.getId()))) {
            throw new ConflictException("ALREADY_A_CAPTAIN",
                    "You are already registered as a captain for a team in this tournament.");
        }

        masterTeam.setCaptainId(currentUser.getId());
        masterTeam.setStatus("Accepted");
        masterTeam.setCaptainName(currentUser.getFullName());
        if (currentUser.getMobile() != null && !currentUser.getMobile().isBlank()) {
            masterTeam.setCaptainMobile(currentUser.getMobile());
        } else if (captainMobile != null && !captainMobile.isBlank()) {
            masterTeam.setCaptainMobile(captainMobile.trim());
        }

        Team saved = teamRepository.save(masterTeam);

        // Multi-role RBAC: Grant CAPTAIN role to claiming user
        grantCaptainRole(currentUser.getId());

        // Ensure team captain exists in squad members roster
        injectCaptainSquadMember(saved, currentUser.getId(), currentUser.getFullName());

        return saved;
    }

    public Team executeSelfRegisterExistingTeam(Team team, UserPrincipal currentUser, SelfRegisterCaptainRequest request,
                                               String initialStatus, boolean isAutoApproved) {
        team.setCaptainId(currentUser.getId());
        team.setStatus(initialStatus);
        team.setCaptainName(currentUser.getFullName());
        if (currentUser.getMobile() != null && !currentUser.getMobile().isBlank()) {
            team.setCaptainMobile(currentUser.getMobile());
        } else if (request != null && request.getCaptainMobile() != null && !request.getCaptainMobile().isBlank()) {
            team.setCaptainMobile(request.getCaptainMobile().trim());
        }
        if (request != null && request.getTeamName() != null && !request.getTeamName().isBlank()) {
            team.setName(request.getTeamName().trim());
        }
        if (request != null && request.getShortCode() != null && !request.getShortCode().isBlank()) {
            team.setShortCode(request.getShortCode().trim().toUpperCase(Locale.ENGLISH));
        }

        Team saved = teamRepository.save(team);

        if (isAutoApproved) {
            grantCaptainRole(currentUser.getId());
            injectCaptainSquadMember(saved, currentUser.getId(), currentUser.getFullName());
        }

        return saved;
    }

    public Team executeSelfRegisterNewTeam(Team team, UserPrincipal currentUser, boolean isAutoApproved) {
        Team saved = teamRepository.save(team);

        if (isAutoApproved) {
            grantCaptainRole(currentUser.getId());
            injectCaptainSquadMember(saved, currentUser.getId(), currentUser.getFullName());
        }

        return saved;
    }

    public Team executeReviewCaptainApplication(Team team, String action, Long captainUserId, String captainName) {
        if ("APPROVED".equals(action)) {
            team.setStatus("Accepted");
            Team saved = teamRepository.save(team);

            if (captainUserId != null) {
                grantCaptainRole(captainUserId);
                String displayName = captainName != null && !captainName.isBlank() ? captainName : "Captain";
                injectCaptainSquadMember(saved, captainUserId, displayName);
            }
            return saved;
        } else {
            team.setStatus("Rejected");
            return teamRepository.save(team);
        }
    }

    public Team updateTeamBranding(Team team, TeamProfileUpdateRequest request, String uploadedLogoUrl) {
        if (uploadedLogoUrl != null && !uploadedLogoUrl.isBlank()) {
            team.setLogoUrl(uploadedLogoUrl);
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
        return teamRepository.save(team);
    }

    private void grantCaptainRole(Long userId) {
        userRepository.findById(userId).ifPresent(user -> {
            roleRepository.findByName(UserRole.CAPTAIN).ifPresent(capRole -> {
                if (!user.getRoles().contains(capRole)) {
                    user.addRole(capRole);
                    userRepository.save(user);
                    log.info("User [{}] granted CAPTAIN role", user.getId());
                }
            });
        });
    }

    private void injectCaptainSquadMember(Team team, Long userId, String playerName) {
        if (squadMemberRepository.findByTeamIdAndIsCaptainTrue(team.getId()).isEmpty()) {
            try {
                SquadMember captainMember = SquadMember.builder()
                        .team(team)
                        .playerUserId(userId)
                        .playerName(playerName != null && !playerName.isBlank() ? playerName : "Captain")
                        .playingRole("All-Rounder")
                        .purchasePrice(0L)
                        .isCaptain(true)
                        .build();
                squadMemberRepository.save(captainMember);
            } catch (DataIntegrityViolationException ex) {
                log.warn("Captain squad member already persisted concurrently for Team ID [{}]", team.getId());
            }
        }
    }
}
