package com.cricauction.modules.tournament.service.impl;

import com.cricauction.common.cache.BoundedLruCache;
import com.cricauction.common.exception.BadRequestException;
import com.cricauction.common.exception.ForbiddenException;
import com.cricauction.modules.tournament.dto.request.CreateTournamentRequest;
import com.cricauction.modules.tournament.dto.request.TournamentSettingsRequest;
import com.cricauction.modules.tournament.dto.request.UpdateTournamentRequest;
import com.cricauction.modules.tournament.dto.response.*;
import com.cricauction.modules.tournament.entity.Tournament;
import com.cricauction.modules.tournament.entity.TournamentSettings;
import com.cricauction.modules.tournament.entity.enums.TournamentStatus;
import com.cricauction.modules.tournament.service.TournamentPersistenceService;
import com.cricauction.modules.tournament.service.TournamentQueryService;
import com.cricauction.modules.tournament.service.TournamentService;
import com.cricauction.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Enterprise Tournament Service Orchestrator implementing Strategy 1: "Read-Before-Write".
 *
 * <p>Flow Segregation:
 * 1. <b>Read Phase:</b> Handled by {@link TournamentQueryService} under {@code @Transactional(readOnly = true)}
 *    on the Read Replica pool (CricAuction-Replica-Pool). All validations, slug uniqueness, and ownership checks
 *    execute without holding a Master database connection.
 * 2. <b>Micro-Write Phase:</b> Handled by {@link TournamentPersistenceService} under {@code @Transactional}
 *    on the Master pool (CricAuction-Master-Pool) for the absolute minimum duration (sub-millisecond mutations).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TournamentServiceImpl implements TournamentService {

    private final TournamentQueryService tournamentQueryService;
    private final TournamentPersistenceService tournamentPersistenceService;

    /**
     * High-speed Bounded L1 In-Memory Cache for Public Landing Pages (API 25)
     * Max 500 items, 10-minute TTL to prevent JVM memory leaks under crawler or high load.
     */
    private final BoundedLruCache<String, PublicTournamentResponse> publicTournamentCache =
            new BoundedLruCache<>(500, 600);

    @Override
    public CreateTournamentResponse createTournament(UserPrincipal currentUser, CreateTournamentRequest request) {
        validateOrganizerRole(currentUser);

        if (request.getEndDate().isBefore(request.getStartDate())) {
            throw new BadRequestException("INVALID_DATES", "Tournament end date cannot be earlier than start date.");
        }
        if (request.getRegistrationStartDate() != null && request.getRegistrationEndDate() != null) {
            if (request.getRegistrationEndDate().isBefore(request.getRegistrationStartDate())) {
                throw new BadRequestException("INVALID_REGISTRATION_DATES", "Registration end date cannot be earlier than start date.");
            }
        }

        // 1. Read Phase on READ REPLICA: Check and generate unique slug
        String slug = tournamentQueryService.generateUniqueSlug(request.getName());
        String publicId = "tourn_" + slug.replace("-", "_");
        if (publicId.length() > 45) {
            publicId = publicId.substring(0, 40) + "_" + UUID.randomUUID().toString().substring(0, 4);
        }

        Tournament tournament = Tournament.builder()
                .publicId(publicId)
                .organizerId(currentUser.getId())
                .name(request.getName().trim())
                .slug(slug)
                .location(request.getLocation().trim())
                .ground(request.getGround().trim())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .auctionDate(request.getAuctionDate())
                .auctionTime(request.getAuctionTime())
                .registrationStartDate(request.getRegistrationStartDate())
                .registrationEndDate(request.getRegistrationEndDate())
                .ballType(request.getBallType().trim())
                .format(request.getFormat().trim())
                .entryFee(request.getEntryFee() != null ? request.getEntryFee().trim() : "Free")
                .description(request.getDescription())
                .bannerUrl(request.getBannerUrl())
                .status(TournamentStatus.DRAFT)
                .step(1)
                .build();

        // Initialize default settings for wizard step 1
        TournamentSettings settings = TournamentSettings.builder()
                .tournament(tournament)
                .totalTeams(10)
                .squadMinimum(12)
                .squadMaximum(15)
                .teamBudget(1000000L)
                .defaultBasePrice(20000L)
                .bidIncrement(10000L)
                .bidTimerSeconds(10)
                .autoStartNext(false)
                .matches(20)
                .matchDurationHours(3)
                .allowPlayersToWithdraw(true)
                .build();

        // 2. Micro-Write Phase on MASTER DB: Atomic save with collision retry
        Tournament saved;
        try {
            saved = tournamentPersistenceService.saveTournamentAndSettings(tournament, settings);
        } catch (DataIntegrityViolationException ex) {
            log.warn("Slug/PublicId collision detected during concurrent creation of tournament [{}]. Retrying with salted unique identifier...", slug);
            String saltedSlug = slug + "-" + UUID.randomUUID().toString().substring(0, 6);
            tournament.setSlug(saltedSlug);
            tournament.setPublicId("tourn_" + (saltedSlug.length() > 38 ? saltedSlug.substring(0, 38) : saltedSlug).replace("-", "_"));
            saved = tournamentPersistenceService.saveTournamentAndSettings(tournament, settings);
        }

        log.info("Tournament draft created: ID [{}] slug [{}] by Organizer [{}]",
                saved.getPublicId(), saved.getSlug(), currentUser.getId());

        return CreateTournamentResponse.builder()
                .id(saved.getPublicId())
                .slug(saved.getSlug())
                .step(saved.getStep())
                .createdAt(saved.getCreatedAt())
                .build();
    }

    @Override
    public List<TournamentSummaryResponse> getOrganizerTournaments(UserPrincipal currentUser, String status) {
        return tournamentQueryService.getOrganizerTournaments(currentUser, status);
    }

    @Override
    public TournamentDetailResponse getTournamentById(UserPrincipal currentUser, String idOrSlug) {
        return tournamentQueryService.getTournamentById(currentUser, idOrSlug);
    }

    @Override
    public TournamentDetailResponse updateTournament(UserPrincipal currentUser, String idOrSlug, UpdateTournamentRequest request) {
        validateOrganizerRole(currentUser);

        // 1. Read Phase on READ REPLICA: Fetch tournament and validate ownership
        Tournament tournament = tournamentQueryService.findTournamentByIdentifier(idOrSlug);
        validateTournamentOwnership(tournament, currentUser);

        if (request.getName() != null && !request.getName().isBlank()) {
            tournament.setName(request.getName().trim());
        }
        if (request.getLocation() != null && !request.getLocation().isBlank()) {
            tournament.setLocation(request.getLocation().trim());
        }
        if (request.getGround() != null && !request.getGround().isBlank()) {
            tournament.setGround(request.getGround().trim());
        }
        if (request.getStartDate() != null) {
            tournament.setStartDate(request.getStartDate());
        }
        if (request.getEndDate() != null) {
            tournament.setEndDate(request.getEndDate());
        }
        if (tournament.getEndDate().isBefore(tournament.getStartDate())) {
            throw new BadRequestException("INVALID_DATES", "Tournament end date cannot be earlier than start date.");
        }
        if (request.getAuctionDate() != null) {
            tournament.setAuctionDate(request.getAuctionDate());
        }
        if (request.getAuctionTime() != null) {
            tournament.setAuctionTime(request.getAuctionTime());
        }
        if (request.getRegistrationStartDate() != null) {
            tournament.setRegistrationStartDate(request.getRegistrationStartDate());
        }
        if (request.getRegistrationEndDate() != null) {
            tournament.setRegistrationEndDate(request.getRegistrationEndDate());
        }
        if (tournament.getRegistrationStartDate() != null && tournament.getRegistrationEndDate() != null) {
            if (tournament.getRegistrationEndDate().isBefore(tournament.getRegistrationStartDate())) {
                throw new BadRequestException("INVALID_REGISTRATION_DATES", "Registration end date cannot be earlier than start date.");
            }
        }
        if (request.getBallType() != null && !request.getBallType().isBlank()) {
            tournament.setBallType(request.getBallType().trim());
        }
        if (request.getFormat() != null && !request.getFormat().isBlank()) {
            tournament.setFormat(request.getFormat().trim());
        }
        if (request.getEntryFee() != null && !request.getEntryFee().isBlank()) {
            tournament.setEntryFee(request.getEntryFee().trim());
        }
        if (request.getDescription() != null) {
            tournament.setDescription(request.getDescription());
        }
        if (request.getBannerUrl() != null) {
            tournament.setBannerUrl(request.getBannerUrl());
        }

        // 2. Micro-Write Phase on MASTER DB
        Tournament updated = tournamentPersistenceService.saveTournament(tournament);
        publicTournamentCache.remove(tournament.getSlug());

        log.info("Tournament [{}] details updated by Organizer [{}]", updated.getPublicId(), currentUser.getId());
        return TournamentDetailResponse.fromEntity(updated);
    }

    @Override
    public TournamentSettingsResponse saveSettings(UserPrincipal currentUser, String idOrSlug, TournamentSettingsRequest request) {
        validateOrganizerRole(currentUser);

        // 1. Read Phase on READ REPLICA: Validate ownership and retrieve tournament settings
        Tournament tournament = tournamentQueryService.findTournamentByIdentifier(idOrSlug);
        validateTournamentOwnership(tournament, currentUser);

        TournamentSettings settings = tournament.getSettings();
        if (settings == null) {
            settings = TournamentSettings.builder().tournament(tournament).build();
            tournament.setSettings(settings);
        }

        if (request.getTeams() != null && request.getTeams() > 0) {
            settings.setTotalTeams(request.getTeams());
        }
        if (request.getSquadMinimum() != null && request.getSquadMinimum() > 0) {
            settings.setSquadMinimum(request.getSquadMinimum());
        }
        if (request.getSquadMaximum() != null && request.getSquadMaximum() > 0) {
            settings.setSquadMaximum(request.getSquadMaximum());
        }
        if (request.getBudget() != null && request.getBudget() >= 0) {
            settings.setTeamBudget(request.getBudget());
        }
        if (request.getBasePrice() != null && request.getBasePrice() >= 0) {
            settings.setDefaultBasePrice(request.getBasePrice());
        }
        if (request.getBidIncrement() != null && request.getBidIncrement() > 0) {
            settings.setBidIncrement(request.getBidIncrement());
        }
        if (request.getBidTimer() != null && request.getBidTimer() > 0) {
            settings.setBidTimerSeconds(request.getBidTimer());
        }
        if (request.getAutoStartNext() != null) {
            settings.setAutoStartNext(request.getAutoStartNext());
        }
        if (request.getMatches() != null && request.getMatches() >= 0) {
            settings.setMatches(request.getMatches());
        }
        if (request.getMatchDuration() != null && request.getMatchDuration() > 0) {
            settings.setMatchDurationHours(request.getMatchDuration());
        }
        if (request.getAllowPlayersToWithdraw() != null) {
            settings.setAllowPlayersToWithdraw(request.getAllowPlayersToWithdraw());
        }
        if (request.getCaptainRegistrationMode() != null && !request.getCaptainRegistrationMode().isBlank()) {
            String mode = request.getCaptainRegistrationMode().trim().toUpperCase(Locale.ENGLISH);
            if ("BOTH".equals(mode) || "OPEN".equals(mode)) {
                settings.setCaptainRegistrationMode("BOTH");
            } else {
                settings.setCaptainRegistrationMode("INVITE_ONLY");
            }
        }
        if (request.getCaptainApprovalMode() != null && !request.getCaptainApprovalMode().isBlank()) {
            String appMode = request.getCaptainApprovalMode().trim().toUpperCase(Locale.ENGLISH);
            if ("AUTO".equals(appMode)) {
                settings.setCaptainApprovalMode("AUTO");
            } else {
                settings.setCaptainApprovalMode("ORGANIZER_APPROVAL");
            }
        }
        if (request.getPlayerApprovalMode() != null && !request.getPlayerApprovalMode().isBlank()) {
            String pMode = request.getPlayerApprovalMode().trim().toUpperCase(Locale.ENGLISH);
            if ("AUTO".equals(pMode) || "AUTO_APPROVAL".equals(pMode)) {
                settings.setPlayerApprovalMode("AUTO_APPROVAL");
            } else {
                settings.setPlayerApprovalMode("ORGANIZER_APPROVAL");
            }
        }

        tournament.setStep(2);

        // 2. Micro-Write Phase on MASTER DB
        tournamentPersistenceService.saveSettings(settings, tournament);
        publicTournamentCache.remove(tournament.getSlug());

        log.info("Tournament [{}] rules and settings saved (Step 2) by Organizer [{}]",
                tournament.getPublicId(), currentUser.getId());

        return TournamentSettingsResponse.builder()
                .step(2)
                .teamsAllocated(settings.getTotalTeams())
                .build();
    }

    @Override
    public PublishTournamentResponse publishTournament(UserPrincipal currentUser, String idOrSlug) {
        validateOrganizerRole(currentUser);

        // 1. Read Phase on READ REPLICA
        Tournament tournament = tournamentQueryService.findTournamentByIdentifier(idOrSlug);
        validateTournamentOwnership(tournament, currentUser);

        tournament.setStatus(TournamentStatus.PUBLISHED);

        // Generate public share links
        String regLink = "https://cricauction.app/register/" + tournament.getSlug();
        String dateSuffix = (tournament.getAuctionDate() != null)
                ? tournament.getAuctionDate().format(DateTimeFormatter.ofPattern("ddMMM", Locale.ENGLISH)).toLowerCase()
                : "auction";
        String inviteLink = "https://cricauction.app/join/" + tournament.getSlug() + "-ca"
                + (tournament.getSettings() != null ? tournament.getSettings().getTotalTeams() : 10)
                + "-" + dateSuffix;

        tournament.setRegistrationLink(regLink);
        tournament.setCaptainInviteLink(inviteLink);

        // 2. Micro-Write Phase on MASTER DB
        tournamentPersistenceService.saveTournament(tournament);
        publicTournamentCache.remove(tournament.getSlug());

        log.info("Tournament [{}] published! Live registration link: [{}]", tournament.getPublicId(), regLink);

        return PublishTournamentResponse.builder()
                .status("published")
                .registrationLink(regLink)
                .captainInviteLink(inviteLink)
                .build();
    }

    @Override
    public void deleteTournament(UserPrincipal currentUser, String idOrSlug) {
        validateOrganizerRole(currentUser);

        // 1. Read Phase on READ REPLICA
        Tournament tournament = tournamentQueryService.findTournamentByIdentifier(idOrSlug);
        validateTournamentOwnership(tournament, currentUser);

        publicTournamentCache.remove(tournament.getSlug());

        // 2. Micro-Write Phase on MASTER DB
        tournamentPersistenceService.deleteTournament(tournament);

        log.info("Tournament [{}] deleted by Organizer [{}]", idOrSlug, currentUser.getId());
    }

    @Override
    public PublicTournamentResponse getPublicTournamentBySlug(String slug) {
        return tournamentQueryService.getPublicTournamentBySlug(slug, publicTournamentCache);
    }

    private void validateOrganizerRole(UserPrincipal currentUser) {
        if (!currentUser.isOrganizer()) {
            throw new ForbiddenException("ORGANIZER_ROLE_REQUIRED", "Access denied: Only registered ORGANIZERS can manage tournaments.");
        }
    }

    private void validateTournamentOwnership(Tournament tournament, UserPrincipal currentUser) {
        if (!tournament.getOrganizerId().equals(currentUser.getId())) {
            throw new ForbiddenException("TOURNAMENT_ACCESS_DENIED", "Access denied: You are not the owner of this tournament.");
        }
    }
}
