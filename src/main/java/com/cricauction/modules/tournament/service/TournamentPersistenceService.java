package com.cricauction.modules.tournament.service;

import com.cricauction.modules.tournament.entity.Tournament;
import com.cricauction.modules.tournament.entity.TournamentSettings;
import com.cricauction.modules.tournament.repository.TournamentRepository;
import com.cricauction.modules.tournament.repository.TournamentSettingsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dedicated Micro-Write Persistence Service for Module 4 (Tournament).
 * Executes strictly under @Transactional (readOnly = false) on the Master pool
 * (CricAuction-Master-Pool) for the absolute minimum duration (sub-millisecond mutations).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class TournamentPersistenceService {

    private final TournamentRepository tournamentRepository;
    private final TournamentSettingsRepository settingsRepository;

    public Tournament saveTournament(Tournament tournament) {
        return tournamentRepository.save(tournament);
    }

    public Tournament saveTournamentAndSettings(Tournament tournament, TournamentSettings settings) {
        tournament.setSettings(settings);
        return tournamentRepository.save(tournament);
    }

    public TournamentSettings saveSettings(TournamentSettings settings, Tournament tournament) {
        settingsRepository.save(settings);
        tournamentRepository.save(tournament);
        return settings;
    }

    public void deleteTournament(Tournament tournament) {
        tournamentRepository.delete(tournament);
    }
}
