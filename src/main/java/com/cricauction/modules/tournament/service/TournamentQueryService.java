package com.cricauction.modules.tournament.service;

import com.cricauction.common.cache.BoundedLruCache;
import com.cricauction.common.exception.BadRequestException;
import com.cricauction.common.exception.ForbiddenException;
import com.cricauction.common.exception.ResourceNotFoundException;
import com.cricauction.modules.tournament.dto.response.*;
import com.cricauction.modules.tournament.entity.Tournament;
import com.cricauction.modules.tournament.entity.TournamentSettings;
import com.cricauction.modules.tournament.entity.enums.TournamentStatus;
import com.cricauction.modules.tournament.repository.TournamentRepository;
import com.cricauction.modules.tournament.repository.TournamentSettingsRepository;
import com.cricauction.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

/**
 * Dedicated Read Query Service for Module 4 (Tournament).
 * Executes exclusively under @Transactional(readOnly = true) to route 100% of queries
 * to the Read Replica pool (CricAuction-Replica-Pool).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TournamentQueryService {

    private final TournamentRepository tournamentRepository;
    private final TournamentSettingsRepository settingsRepository;

    public boolean existsBySlug(String slug) {
        return tournamentRepository.existsBySlug(slug);
    }

    public Tournament findTournamentByIdentifier(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            throw new BadRequestException("INVALID_IDENTIFIER", "Tournament identifier cannot be empty.");
        }
        String trimmed = identifier.trim();

        return tournamentRepository.findByPublicIdOrSlugWithSettings(trimmed)
                .or(() -> {
                    if (trimmed.matches("^\\d+$")) {
                        return tournamentRepository.findByIdWithSettings(Long.parseLong(trimmed));
                    }
                    return java.util.Optional.empty();
                })
                .orElseThrow(() -> new ResourceNotFoundException("TOURNAMENT_NOT_FOUND", "Tournament not found for: " + identifier));
    }

    public Tournament validateAndGetTournamentForUpdate(String idOrSlug, UserPrincipal currentUser) {
        validateOrganizerRole(currentUser);
        Tournament tournament = findTournamentByIdentifier(idOrSlug);
        validateTournamentOwnership(tournament, currentUser);
        return tournament;
    }

    public TournamentSettings findSettingsByTournamentId(Long tournamentId) {
        return settingsRepository.findById(tournamentId).orElse(null);
    }

    public List<TournamentSummaryResponse> getOrganizerTournaments(UserPrincipal currentUser, String status) {
        validateOrganizerRole(currentUser);

        List<Tournament> list;
        if (status != null && !status.isBlank()) {
            TournamentStatus filterStatus = TournamentStatus.fromString(status);
            if (filterStatus != null) {
                list = tournamentRepository.findByOrganizerIdAndStatusOrderByCreatedAtDesc(currentUser.getId(), filterStatus);
            } else {
                list = tournamentRepository.findByOrganizerIdOrderByCreatedAtDesc(currentUser.getId());
            }
        } else {
            list = tournamentRepository.findByOrganizerIdOrderByCreatedAtDesc(currentUser.getId());
        }

        return list.stream()
                .map(t -> TournamentSummaryResponse.fromEntity(t, 0, 0))
                .toList();
    }

    public TournamentDetailResponse getTournamentById(UserPrincipal currentUser, String idOrSlug) {
        Tournament tournament = findTournamentByIdentifier(idOrSlug);
        return TournamentDetailResponse.fromEntity(tournament);
    }

    public PublicTournamentResponse getPublicTournamentBySlug(String slug, BoundedLruCache<String, PublicTournamentResponse> publicCache) {
        if (slug == null || slug.isBlank()) {
            throw new BadRequestException("INVALID_SLUG", "Tournament slug cannot be empty.");
        }

        String normalizedSlug = slug.trim().toLowerCase(Locale.ENGLISH);

        // 1. Check L1 cache
        PublicTournamentResponse cached = publicCache.get(normalizedSlug);
        if (cached != null) {
            return cached;
        }

        // 2. Read from Read Replica
        Tournament tournament = tournamentRepository.findByPublicIdOrSlugWithSettings(normalizedSlug)
                .orElseThrow(() -> new ResourceNotFoundException("TOURNAMENT_NOT_FOUND", "Tournament not found for slug: " + slug));

        PublicTournamentResponse response = PublicTournamentResponse.fromEntity(tournament, 0);
        publicCache.put(normalizedSlug, response);
        return response;
    }

    public void validateOrganizerRole(UserPrincipal currentUser) {
        if (!currentUser.isOrganizer()) {
            throw new ForbiddenException("ORGANIZER_ROLE_REQUIRED", "Access denied: Only registered ORGANIZERS can manage tournaments.");
        }
    }

    public void validateTournamentOwnership(Tournament tournament, UserPrincipal currentUser) {
        if (!tournament.getOrganizerId().equals(currentUser.getId())) {
            throw new ForbiddenException("TOURNAMENT_ACCESS_DENIED", "Access denied: You are not the owner of this tournament.");
        }
    }

    public String generateUniqueSlug(String name) {
        String base = toSlug(name);
        String candidate = base;
        int counter = 1;
        while (tournamentRepository.existsBySlug(candidate)) {
            candidate = base + "-" + counter++;
        }
        return candidate;
    }

    private String toSlug(String input) {
        if (input == null) return "tournament";
        String nowhitespace = input.trim().replaceAll("\\s+", "-");
        String normalized = Normalizer.normalize(nowhitespace, Normalizer.Form.NFD);
        String slug = normalized.replaceAll("[^\\w-]", "").replaceAll("-+", "-").toLowerCase(Locale.ENGLISH);
        return slug.isBlank() ? "tournament" : slug;
    }
}
