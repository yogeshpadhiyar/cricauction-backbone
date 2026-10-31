package com.cricauction.modules.dashboard.service;

import com.cricauction.modules.dashboard.dto.response.*;
import com.cricauction.modules.dashboard.util.DashboardFormatter;
import com.cricauction.modules.match.entity.Match;
import com.cricauction.modules.match.repository.MatchRepository;
import com.cricauction.modules.player.entity.TournamentPlayer;
import com.cricauction.modules.player.repository.TournamentPlayerRepository;
import com.cricauction.modules.team.entity.SquadMember;
import com.cricauction.modules.team.entity.Team;
import com.cricauction.modules.team.repository.SquadMemberRepository;
import com.cricauction.modules.team.repository.TeamRepository;
import com.cricauction.modules.tournament.entity.Tournament;
import com.cricauction.modules.tournament.entity.enums.TournamentStatus;
import com.cricauction.modules.tournament.repository.TournamentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Strategy 1 (Read-Before-Write): Dedicated read-only query service.
 * Executed exclusively on CricAuction-Replica-Pool via @Transactional(readOnly = true).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardQueryService {

    private final TournamentRepository tournamentRepository;
    private final TeamRepository teamRepository;
    private final TournamentPlayerRepository tournamentPlayerRepository;
    private final SquadMemberRepository squadMemberRepository;
    private final MatchRepository matchRepository;

    /**
     * API 15: Organizer Dashboard Feed Aggregation
     */
    public OrganizerDashboardResponse getOrganizerDashboard(Long organizerId) {
        log.debug("Strategy 1: Fetching Organizer dashboard from replica pool for organizerId={}", organizerId);

        // 1. Check for active draft
        Optional<Tournament> draftOpt = tournamentRepository.findFirstByOrganizerIdAndStatusOrderByUpdatedAtDesc(
                organizerId, TournamentStatus.DRAFT);
        boolean hasActiveDraft = draftOpt.isPresent();
        DraftSummaryDto draftSummary = null;
        if (hasActiveDraft) {
            Tournament draft = draftOpt.get();
            draftSummary = DraftSummaryDto.builder()
                    .id(draft.getPublicId())
                    .name(draft.getName())
                    .step(draft.getStep())
                    .build();
        }

        // 2. Stats
        long myTournaments = tournamentRepository.countByOrganizerId(organizerId);
        long teams = teamRepository.countTotalTeamsByOrganizerId(organizerId);
        long playersRegistered = tournamentPlayerRepository.countTotalPlayersByOrganizerId(organizerId);

        List<Tournament> upcomingList = tournamentRepository.findUpcomingByOrganizerId(organizerId);
        Tournament upcomingTournamentEntity = !upcomingList.isEmpty()
                ? upcomingList.get(0)
                : tournamentRepository.findFirstByOrganizerIdOrderByCreatedAtDesc(organizerId).orElse(null);

        String upcomingAuctionDate = null;
        UpcomingTournamentDto upcomingTournamentDto = null;

        if (upcomingTournamentEntity != null) {
            upcomingAuctionDate = DashboardFormatter.formatAuctionDateOnly(upcomingTournamentEntity.getAuctionDate());

            long teamsCount = teamRepository.countByTournamentId(upcomingTournamentEntity.getId());
            long playersCount = tournamentPlayerRepository.countByTournamentId(upcomingTournamentEntity.getId());
            Instant auctionInstant = DashboardFormatter.toAuctionInstant(
                    upcomingTournamentEntity.getAuctionDate(), upcomingTournamentEntity.getAuctionTime());
            String auctionFormatted = upcomingTournamentEntity.formatAuctionAt();

            String displayStatus;
            if (upcomingTournamentEntity.getStatus() == TournamentStatus.PUBLISHED || upcomingTournamentEntity.getStatus() == TournamentStatus.UPCOMING) {
                displayStatus = "Registration Open";
            } else if (upcomingTournamentEntity.getStatus() == TournamentStatus.AUCTION_LIVE) {
                displayStatus = "Auction Live";
            } else {
                displayStatus = upcomingTournamentEntity.getStatus() != null ? upcomingTournamentEntity.getStatus().getValue() : "draft";
            }

            upcomingTournamentDto = UpcomingTournamentDto.builder()
                    .id(upcomingTournamentEntity.getPublicId())
                    .name(upcomingTournamentEntity.getName())
                    .status(displayStatus)
                    .teamsCount(teamsCount)
                    .playersCount(playersCount)
                    .auctionDate(auctionInstant)
                    .auctionFormatted(auctionFormatted)
                    .build();
        }

        OrganizerStatsDto stats = OrganizerStatsDto.builder()
                .myTournaments(myTournaments)
                .teams(teams)
                .playersRegistered(playersRegistered)
                .upcomingAuctionDate(upcomingAuctionDate)
                .build();

        // 3. Activity Feed
        List<DashboardActivityDto> recentActivity = new ArrayList<>();
        if (upcomingTournamentEntity != null) {
            if (stats.getPlayersRegistered() > 0) {
                recentActivity.add(DashboardActivityDto.builder()
                        .id("act_1")
                        .type("registration")
                        .title(stats.getPlayersRegistered() + " players registered")
                        .subtitle(upcomingTournamentEntity.getName())
                        .timestamp(Instant.now())
                        .build());
            }
            if (stats.getTeams() > 0) {
                recentActivity.add(DashboardActivityDto.builder()
                        .id("act_2")
                        .type("captain_joined")
                        .title("Captain joined your tournament")
                        .subtitle(upcomingTournamentEntity.getName())
                        .timestamp(Instant.now().minusSeconds(1800))
                        .build());
            }
            if (upcomingTournamentEntity.getAuctionDate() != null) {
                recentActivity.add(DashboardActivityDto.builder()
                        .id("act_3")
                        .type("auction_scheduled")
                        .title("Auction scheduled for " + upcomingAuctionDate)
                        .subtitle("Live auction room")
                        .timestamp(Instant.now().minusSeconds(3600))
                        .build());
            }
        }

        return OrganizerDashboardResponse.builder()
                .hasActiveDraft(hasActiveDraft)
                .draftSummary(draftSummary)
                .stats(stats)
                .upcomingTournament(upcomingTournamentDto)
                .recentActivity(recentActivity)
                .build();
    }

    /**
     * API 16: Captain Dashboard Feed Aggregation
     */
    public CaptainDashboardResponse getCaptainDashboard(Long captainId) {
        log.debug("Strategy 1: Fetching Captain dashboard from replica pool for captainId={}", captainId);

        List<Team> captainTeams = teamRepository.findByCaptainIdWithTournamentOrderByIdDesc(captainId);
        if (captainTeams.isEmpty()) {
            return CaptainDashboardResponse.builder()
                    .team(null)
                    .upcomingAuction(null)
                    .upcomingMatch(null)
                    .build();
        }

        Team team = captainTeams.get(0);
        Tournament tournament = team.getTournament();

        long squadCount = squadMemberRepository.countByTeamId(team.getId());
        int maxSquad = (tournament.getSettings() != null) ? tournament.getSettings().getSquadMaximum() : 15;

        CaptainTeamDto teamDto = CaptainTeamDto.builder()
                .id(team.getId().toString())
                .name(team.getName())
                .tournamentId(tournament.getPublicId())
                .tournamentName(tournament.getName())
                .totalBudget(BigDecimal.valueOf(team.getInitialBudget()))
                .remainingBudget(BigDecimal.valueOf(team.getRemainingBudget()))
                .budgetFormatted(DashboardFormatter.formatBudget(BigDecimal.valueOf(team.getRemainingBudget())))
                .squadSize(squadCount)
                .maxSquad(maxSquad)
                .build();

        UpcomingAuctionDto upcomingAuctionDto = null;
        if (tournament.getAuctionDate() != null) {
            Instant auctionInstant = DashboardFormatter.toAuctionInstant(
                    tournament.getAuctionDate(), tournament.getAuctionTime());
            String displayDate = tournament.formatAuctionAt();
            boolean isLive = tournament.getStatus() == TournamentStatus.AUCTION_LIVE;

            upcomingAuctionDto = UpcomingAuctionDto.builder()
                    .tournamentId(tournament.getPublicId())
                    .name(tournament.getName())
                    .auctionDate(auctionInstant)
                    .displayDate(displayDate)
                    .isLive(isLive)
                    .build();
        }

        UpcomingMatchDto upcomingMatchDto = null;
        List<Match> upcomingMatches = matchRepository.findUpcomingByTeamId(team.getId());
        if (!upcomingMatches.isEmpty()) {
            Match m = upcomingMatches.get(0);
            String opponentName = m.getTeam1().getId().equals(team.getId())
                    ? m.getTeam2().getName()
                    : m.getTeam1().getName();
            upcomingMatchDto = UpcomingMatchDto.builder()
                    .id(m.getPublicId())
                    .opponent(opponentName)
                    .schedule(DashboardFormatter.formatMatchSchedule(m.getScheduledAt()))
                    .ground(m.getGround())
                    .build();
        }

        return CaptainDashboardResponse.builder()
                .team(teamDto)
                .upcomingAuction(upcomingAuctionDto)
                .upcomingMatch(upcomingMatchDto)
                .build();
    }

    /**
     * API 17: Player Dashboard Feed Aggregation
     */
    public PlayerDashboardResponse getPlayerDashboard(Long userId) {
        log.debug("Strategy 1: Fetching Player dashboard from replica pool for userId={}", userId);

        List<TournamentPlayer> registrations = tournamentPlayerRepository.findByUserIdWithTournamentOrderByIdDesc(userId);
        List<SquadMember> squadMemberships = squadMemberRepository.findSquadMembersWithTeamAndTournamentByUserId(userId);

        PlayerJourneyDto activeJourney = null;
        if (!registrations.isEmpty()) {
            TournamentPlayer latestTp = registrations.get(0);
            Tournament tournament = latestTp.getTournament();

            String teamName = null;
            for (SquadMember sm : squadMemberships) {
                if (sm.getTeam() != null && sm.getTeam().getTournament() != null
                        && sm.getTeam().getTournament().getId().equals(tournament.getId())) {
                    teamName = sm.getTeam().getName();
                    break;
                }
            }

            Long basePriceVal = latestTp.getBasePrice();
            if (basePriceVal == null && tournament.getSettings() != null) {
                basePriceVal = tournament.getSettings().getDefaultBasePrice();
            }
            if (basePriceVal == null) {
                basePriceVal = 0L;
            }

            activeJourney = PlayerJourneyDto.builder()
                    .tournamentId(tournament.getPublicId())
                    .tournamentName(tournament.getName())
                    .status(latestTp.getStatus())
                    .auctionAt(tournament.formatAuctionAt())
                    .team(teamName)
                    .basePrice(BigDecimal.valueOf(basePriceVal))
                    .build();
        }

        List<PlayerTournamentItemDto> myTournaments = new ArrayList<>();
        for (TournamentPlayer tp : registrations) {
            Tournament t = tp.getTournament();
            String status = tp.getStatus();
            String badge;
            if ("SOLD".equalsIgnoreCase(status)) {
                badge = "Sold";
            } else if ("APPROVED".equalsIgnoreCase(status)) {
                badge = "Approved";
            } else if ("REGISTERED".equalsIgnoreCase(status) || "PENDING".equalsIgnoreCase(status)) {
                badge = "Pending";
            } else {
                badge = "Active";
            }

            myTournaments.add(PlayerTournamentItemDto.builder()
                    .tournamentId(t.getPublicId())
                    .name(t.getName())
                    .status(status)
                    .auctionDate(DashboardFormatter.formatAuctionDateOnly(t.getAuctionDate()))
                    .badge(badge)
                    .build());
        }

        List<UpcomingMatchDto> upcomingMatches = new ArrayList<>();
        List<Long> playerTeamIds = squadMemberships.stream()
                .map(SquadMember::getTeam)
                .filter(java.util.Objects::nonNull)
                .map(Team::getId)
                .distinct()
                .toList();

        if (!playerTeamIds.isEmpty()) {
            List<Match> matches = matchRepository.findUpcomingByTeamIds(playerTeamIds);
            for (Match m : matches) {
                boolean isTeam1 = playerTeamIds.contains(m.getTeam1().getId());
                String opponent = isTeam1 ? m.getTeam2().getName() : m.getTeam1().getName();
                upcomingMatches.add(UpcomingMatchDto.builder()
                        .id(m.getPublicId())
                        .opponent(opponent)
                        .schedule(DashboardFormatter.formatMatchSchedule(m.getScheduledAt()))
                        .ground(m.getGround())
                        .build());
            }
        }

        return PlayerDashboardResponse.builder()
                .activeJourney(activeJourney)
                .myTournaments(myTournaments)
                .upcomingMatches(upcomingMatches)
                .build();
    }
}
