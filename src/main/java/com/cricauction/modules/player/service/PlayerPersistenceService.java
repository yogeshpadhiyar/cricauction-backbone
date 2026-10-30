package com.cricauction.modules.player.service;

import com.cricauction.common.exception.ConflictException;
import com.cricauction.common.exception.ForbiddenException;
import com.cricauction.common.exception.ResourceNotFoundException;
import com.cricauction.modules.auth.entity.User;
import com.cricauction.modules.player.dto.request.PlayerRegistrationRequest;
import com.cricauction.modules.player.entity.TournamentPlayer;
import com.cricauction.modules.player.repository.TournamentPlayerRepository;
import com.cricauction.modules.tournament.entity.Tournament;
import com.cricauction.modules.tournament.entity.TournamentSettings;
import com.cricauction.modules.tournament.repository.TournamentSettingsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dedicated Persistence Service for Player Registration mutations.
 * All operations execute on Master DB (CricAuction-Master-Pool) with minimal hold duration.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class PlayerPersistenceService {

    private final TournamentPlayerRepository tournamentPlayerRepository;
    private final TournamentSettingsRepository tournamentSettingsRepository;

    public TournamentPlayer executeRegisterPlayer(
            User user,
            Tournament tournament,
            TournamentSettings settings,
            PlayerRegistrationRequest request
    ) {
        Long tournamentId = tournament.getId();
        Long userId = user.getId();
        int maxCapacity = settings.getTotalTeams() * settings.getSquadMaximum();

        // 1. Atomic compare-and-swap reservation counter on Master DB
        int rowsUpdated = tournamentSettingsRepository.incrementRegisteredCountIfUnderCapacity(tournamentId, maxCapacity);
        if (rowsUpdated == 0) {
            log.warn("Registration rejected: Tournament [{}] reached capacity of [{}] players", tournamentId, maxCapacity);
            throw new ConflictException("CAPACITY_REACHED",
                    "Tournament player capacity reached (" + maxCapacity + " players maximum). Registration is now full.");
        }

        Long basePrice = (request.getBasePrice() != null && request.getBasePrice() > 0)
                ? request.getBasePrice()
                : settings.getDefaultBasePrice();

        String mobile = (request.getMobile() != null && !request.getMobile().isBlank())
                ? request.getMobile().trim()
                : user.getMobile();

        String city = (request.getCity() != null && !request.getCity().isBlank())
                ? request.getCity().trim()
                : user.getCity();

        boolean isAutoApproval = settings != null && settings.getPlayerApprovalMode() != null &&
                (settings.getPlayerApprovalMode().equalsIgnoreCase("AUTO") ||
                 settings.getPlayerApprovalMode().equalsIgnoreCase("AUTO_APPROVAL"));
        String initialStatus = isAutoApproval ? "Approved" : "Registered";

        TournamentPlayer player = TournamentPlayer.builder()
                .tournament(tournament)
                .user(user)
                .playerName(user.getFullName())
                .registeredRole(request.getPlayingRole().trim())
                .battingStyle(request.getBattingStyle() != null ? request.getBattingStyle().trim() : null)
                .bowlingStyle(request.getBowlingStyle() != null ? request.getBowlingStyle().trim() : null)
                .basePrice(basePrice)
                .city(city)
                .mobile(mobile)
                .jerseySize(request.getJerseySize() != null ? request.getJerseySize().trim() : null)
                .cricHeroesLink(request.getCricHeroesLink() != null ? request.getCricHeroesLink().trim() : null)
                .photoUrl(user.getProfilePhotoUrl())
                .status(initialStatus)
                .build();

        try {
            TournamentPlayer saved = tournamentPlayerRepository.save(player);
            log.info("Master micro-write: Registered player ID [{}] (User {}) in Tournament [{}] with status [{}]",
                    saved.getId(), userId, tournament.getPublicId(), initialStatus);
            return saved;
        } catch (DataIntegrityViolationException ex) {
            log.warn("Duplicate registration caught by DB constraint for Tournament [{}] User [{}]", tournamentId, userId);
            throw new ConflictException("ALREADY_REGISTERED", "You are already registered for this tournament.");
        }
    }

    public void executeWithdraw(String tournamentPublicId, Long userId) {
        TournamentPlayer player = tournamentPlayerRepository.findByTournamentPublicIdAndUserId(tournamentPublicId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("REGISTRATION_NOT_FOUND", "No registration record found for this tournament."));

        Long tournamentId = player.getTournament().getId();
        TournamentSettings settings = tournamentSettingsRepository.findById(tournamentId).orElse(null);
        if (settings != null && !settings.isAllowPlayersToWithdraw()) {
            throw new ForbiddenException("WITHDRAWAL_NOT_ALLOWED", "Player withdrawal is not permitted for this tournament.");
        }

        if ("Sold".equalsIgnoreCase(player.getStatus())) {
            throw new ConflictException("CANNOT_WITHDRAW", "Cannot withdraw once bought by a team in the auction.");
        }

        tournamentPlayerRepository.delete(player);
        if (!"Rejected".equalsIgnoreCase(player.getStatus())) {
            tournamentSettingsRepository.decrementRegisteredCount(tournamentId);
        }
        log.info("Master micro-write: Player [{}] withdrawn from Tournament [{}]", userId, tournamentPublicId);
    }

    public void updatePlayerStatus(String tournamentPublicId, Long playerId, String newStatus, String reason) {
        TournamentPlayer player = tournamentPlayerRepository.findByIdAndTournamentPublicId(playerId, tournamentPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("PLAYER_NOT_FOUND", "Player not found for ID: " + playerId));

        String oldStatus = player.getStatus() != null ? player.getStatus().trim() : "";
        if ("Sold".equalsIgnoreCase(oldStatus)) {
            throw new ConflictException("CANNOT_CHANGE_STATUS", "Cannot alter status of a player who has already been sold in the auction.");
        }

        String trimmedNewStatus = newStatus.trim();

        boolean wasRejected = "Rejected".equalsIgnoreCase(oldStatus);
        boolean isNowRejected = "Rejected".equalsIgnoreCase(trimmedNewStatus);

        Long tournamentId = player.getTournament().getId();

        if (!wasRejected && isNowRejected) {
            // Player rejected: free up the capacity slot
            tournamentSettingsRepository.decrementRegisteredCount(tournamentId);
        } else if (wasRejected && !isNowRejected) {
            // Player restored from rejection: re-reserve capacity slot
            TournamentSettings settings = tournamentSettingsRepository.findById(tournamentId)
                    .orElse(null);
            int maxCapacity = settings != null ? settings.getTotalTeams() * settings.getSquadMaximum() : Integer.MAX_VALUE;
            int updated = tournamentSettingsRepository.incrementRegisteredCountIfUnderCapacity(tournamentId, maxCapacity);
            if (updated == 0) {
                throw new ConflictException("CAPACITY_REACHED",
                        "Cannot restore player to " + trimmedNewStatus + "; tournament player capacity reached (" + maxCapacity + " max).");
            }
        }

        player.setStatus(trimmedNewStatus);
        if (reason != null) {
            player.setReviewReason(reason.trim());
        }
        tournamentPlayerRepository.save(player);
        log.info("Master micro-write: Player [{}] status updated from [{}] to [{}] in Tournament [{}]",
                playerId, oldStatus, trimmedNewStatus, tournamentPublicId);
    }

    public void updatePlayerBasePrice(String tournamentPublicId, Long playerId, Long newBasePrice) {
        TournamentPlayer player = tournamentPlayerRepository.findByIdAndTournamentPublicId(playerId, tournamentPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("PLAYER_NOT_FOUND", "Player not found for ID: " + playerId));

        player.setBasePrice(newBasePrice);
        tournamentPlayerRepository.save(player);
        log.info("Master micro-write: Player [{}] base price updated to [{}] in Tournament [{}]", playerId, newBasePrice, tournamentPublicId);
    }
}
