package com.cricauction.modules.team.service;

import com.cricauction.modules.team.dto.request.*;
import com.cricauction.modules.team.dto.response.*;
import com.cricauction.security.UserPrincipal;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface TeamService {

    TournamentTeamsResponse getTournamentTeams(UserPrincipal currentUser, String tournamentIdOrSlug);

    AddTeamResponse addTeam(UserPrincipal currentUser, String tournamentIdOrSlug, AddTeamRequest request);

    TeamSummaryResponse updateTeam(UserPrincipal currentUser, String tournamentIdOrSlug, Long teamId, UpdateTeamRequest request);

    void deleteTeam(UserPrincipal currentUser, String tournamentIdOrSlug, Long teamId);

    InviteCaptainResponse sendCaptainInvite(UserPrincipal currentUser, String tournamentIdOrSlug, Long teamId, InviteCaptainRequest request);

    JoinTeamResponse joinTeam(UserPrincipal currentUser, JoinTeamRequest request);

    JoinTeamResponse selfRegisterCaptain(UserPrincipal currentUser, String tournamentIdOrSlug, SelfRegisterCaptainRequest request);

    CaptainApprovalResponse reviewCaptainApplication(UserPrincipal currentUser, String tournamentIdOrSlug, Long teamId, CaptainApprovalRequest request);

    List<TeamSummaryResponse> getPendingCaptainRequests(UserPrincipal currentUser, String tournamentIdOrSlug);

    TeamSquadResponse getTeamSquad(UserPrincipal currentUser, Long teamId);

    TeamSummaryResponse updateTeamProfile(UserPrincipal currentUser, Long teamId, TeamProfileUpdateRequest request, MultipartFile logo);
}
