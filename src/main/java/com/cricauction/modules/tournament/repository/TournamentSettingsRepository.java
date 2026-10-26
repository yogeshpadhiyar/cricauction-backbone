package com.cricauction.modules.tournament.repository;

import com.cricauction.modules.tournament.entity.TournamentSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TournamentSettingsRepository extends JpaRepository<TournamentSettings, Long> {

    Optional<TournamentSettings> findByTournamentId(Long tournamentId);
}
