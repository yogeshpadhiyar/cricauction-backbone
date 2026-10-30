package com.cricauction.modules.player.service;

import com.cricauction.common.exception.ConflictException;
import com.cricauction.common.exception.ForbiddenException;
import com.cricauction.common.exception.ResourceNotFoundException;
import com.cricauction.modules.auth.entity.User;
import com.cricauction.modules.auth.repository.UserRepository;
import com.cricauction.modules.player.dto.response.*;
import com.cricauction.modules.player.entity.TournamentPlayer;
import com.cricauction.modules.player.repository.TournamentPlayerRepository;
import com.cricauction.modules.tournament.entity.Tournament;
import com.cricauction.modules.tournament.entity.TournamentSettings;
import com.cricauction.modules.tournament.entity.enums.TournamentStatus;
import com.cricauction.modules.tournament.repository.TournamentRepository;
import com.cricauction.modules.tournament.repository.TournamentSettingsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Dedicated Read Service for Player Registration and Capacity tracking.
 * All queries are executed with readOnly = true routing directly to Read Replica (CricAuction-Replica-Pool).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlayerQueryService {

    private final TournamentRepository tournamentRepository;
    private final TournamentSettingsRepository tournamentSettingsRepository;
    private final TournamentPlayerRepository tournamentPlayerRepository;
    private final UserRepository userRepository;

    public Tournament findTournamentForRegistration(String tournamentPublicId) {
        Tournament tournament = tournamentRepository.findByPublicId(tournamentPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("TOURNAMENT_NOT_FOUND", "Tournament not found for ID: " + tournamentPublicId));

        if (tournament.getStatus() == TournamentStatus.COMPLETED ||
            tournament.getStatus() == TournamentStatus.ARCHIVED ||
            tournament.getStatus() == TournamentStatus.ONGOING) {
            throw new ConflictException("REGISTRATION_CLOSED",
                    "Player registration is closed because the tournament is currently " + tournament.getStatus() + ".");
        }
        return tournament;
    }

    public Tournament validateTournamentOwnership(String tournamentPublicId, Long organizerId) {
        Tournament tournament = tournamentRepository.findByPublicId(tournamentPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("TOURNAMENT_NOT_FOUND", "Tournament not found for ID: " + tournamentPublicId));

        if (tournament.getOrganizerId() == null || !tournament.getOrganizerId().equals(organizerId)) {
            throw new ForbiddenException("TOURNAMENT_ACCESS_DENIED", "Access denied: You are not the owner of this tournament.");
        }
        return tournament;
    }

    public TournamentSettings findTournamentSettings(Long tournamentId) {
        return tournamentSettingsRepository.findById(tournamentId)
                .orElseThrow(() -> new ResourceNotFoundException("SETTINGS_NOT_FOUND", "Tournament settings not found for ID: " + tournamentId));
    }

    public void validatePlayerNotAlreadyRegistered(Long tournamentId, Long userId) {
        if (tournamentPlayerRepository.existsByTournamentIdAndUserId(tournamentId, userId)) {
            throw new ConflictException("ALREADY_REGISTERED", "You are already registered for this tournament.");
        }
    }

    public User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND", "User profile not found."));
    }

    public PlayerRegistrationStatusResponse getPlayerStatus(String tournamentPublicId, Long userId) {
        TournamentPlayer player = tournamentPlayerRepository.findByTournamentPublicIdAndUserId(tournamentPublicId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("REGISTRATION_NOT_FOUND", "No registration record found for this tournament."));

        Tournament tournament = player.getTournament();
        String auctionSchedule = tournament.getAuctionDate() != null
                ? tournament.getAuctionDate().toString() + (tournament.getAuctionTime() != null ? " " + tournament.getAuctionTime() : "")
                : null;

        return PlayerRegistrationStatusResponse.builder()
                .tournamentId(tournament.getPublicId())
                .tournamentName(tournament.getName())
                .status(player.getStatus())
                .basePrice(player.getBasePrice())
                .auctionSchedule(auctionSchedule)
                .soldToTeam(null)
                .soldPrice(null)
                .build();
    }

    public PlayerListResponse listTournamentPlayers(String tournamentPublicId, String filter, String search, int page, int limit) {
        Tournament tournament = tournamentRepository.findByPublicId(tournamentPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("TOURNAMENT_NOT_FOUND", "Tournament not found for ID: " + tournamentPublicId));
        return listTournamentPlayers(tournament, filter, search, page, limit);
    }

    public PlayerListResponse listTournamentPlayers(Tournament tournament, String filter, String search, int page, int limit) {
        int pageIndex = Math.max(0, page - 1);
        int pageSize = (limit > 0 && limit <= 100) ? limit : (limit > 100 ? 100 : 20);
        Pageable pageable = PageRequest.of(pageIndex, pageSize);

        String normalizedStatus = null;
        String normalizedRole = null;

        if (filter != null && !filter.isBlank() && !filter.equalsIgnoreCase("All")) {
            String f = filter.trim();
            String clean = f.replaceAll("[-_ ]", "").toLowerCase(Locale.ROOT);
            if (clean.equals("batsman") || clean.equals("bowler") ||
                clean.equals("allrounder") || clean.equals("wicketkeeper")) {
                normalizedRole = f;
            } else {
                normalizedStatus = f;
            }
        }

        String normalizedSearch = (search != null && !search.isBlank()) ? search.trim() : null;

        Page<TournamentPlayer> playerPage = tournamentPlayerRepository.findPlayersFiltered(
                tournament.getId(),
                normalizedStatus,
                normalizedRole,
                normalizedSearch,
                pageable
        );

        List<PlayerSummaryResponse> summaries = playerPage.getContent().stream()
                .map(tp -> PlayerSummaryResponse.builder()
                        .id(tp.getId())
                        .name(tp.getPlayerName())
                        .role(tp.getRegisteredRole())
                        .basePrice(tp.getBasePrice() != null ? "₹" + String.format(Locale.US, "%,d", tp.getBasePrice()) : "₹0")
                        .status(tp.getStatus())
                        .mobile(tp.getMobile())
                        .photo(tp.getPhotoUrl())
                        .build()
                )
                .collect(Collectors.toList());

        return PlayerListResponse.builder()
                .total(playerPage.getTotalElements())
                .page(page)
                .limit(pageSize)
                .players(summaries)
                .build();
    }

    public PlayerCapacityStatsResponse getCapacityStats(String tournamentPublicId) {
        Tournament tournament = tournamentRepository.findByPublicId(tournamentPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("TOURNAMENT_NOT_FOUND", "Tournament not found for ID: " + tournamentPublicId));

        TournamentSettings settings = findTournamentSettings(tournament.getId());
        int capacity = settings.getTotalTeams() * settings.getSquadMaximum();

        List<Object[]> statusCounts = tournamentPlayerRepository.getStatusCountsByTournamentId(tournament.getId());
        Map<String, Long> statusMap = statusCounts.stream()
                .collect(Collectors.toMap(
                        row -> String.valueOf(row[0]).toLowerCase(Locale.ROOT),
                        row -> ((Number) row[1]).longValue(),
                        (existing, replacement) -> existing
                ));

        long registered = statusMap.getOrDefault("registered", 0L);
        long pending = statusMap.getOrDefault("pending", 0L);
        long approved = statusMap.getOrDefault("approved", 0L);

        long totalEnrolled = statusMap.entrySet().stream()
                .filter(e -> !e.getKey().equalsIgnoreCase("rejected"))
                .mapToLong(Map.Entry::getValue)
                .sum();

        List<Object[]> roleCounts = tournamentPlayerRepository.getRoleCountsByTournamentId(tournament.getId());
        Map<String, Long> roleMap = roleCounts.stream()
                .collect(Collectors.toMap(
                        row -> String.valueOf(row[0]).toLowerCase(Locale.ROOT).replaceAll("[-_ ]", ""),
                        row -> ((Number) row[1]).longValue(),
                        Long::sum
                ));

        long batsmen = roleMap.getOrDefault("batsman", 0L);
        long bowlers = roleMap.getOrDefault("bowler", 0L);
        long allRounders = roleMap.getOrDefault("allrounder", 0L);
        long wicketKeepers = roleMap.getOrDefault("wicketkeeper", 0L);

        int percentageFilled = capacity > 0 ? (int) Math.min(100, Math.round((totalEnrolled * 100.0) / capacity)) : 0;
        boolean isOpen = (tournament.getStatus() == TournamentStatus.UPCOMING || tournament.getStatus() == TournamentStatus.DRAFT || tournament.getStatus() == TournamentStatus.PUBLISHED)
                         && totalEnrolled < capacity;

        return PlayerCapacityStatsResponse.builder()
                .capacity(capacity)
                .registered(totalEnrolled)
                .pending(pending)
                .approved(approved)
                .percentageFilled(percentageFilled)
                .isOpen(isOpen)
                .roleBreakdown(RoleBreakdownResponse.builder()
                        .batsmen(batsmen)
                        .bowlers(bowlers)
                        .allRounders(allRounders)
                        .wicketKeepers(wicketKeepers)
                        .build())
                .build();
    }
}
