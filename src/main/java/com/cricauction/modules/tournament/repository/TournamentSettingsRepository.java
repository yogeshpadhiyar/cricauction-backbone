package com.cricauction.modules.tournament.repository;

import com.cricauction.modules.tournament.entity.TournamentSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TournamentSettingsRepository extends JpaRepository<TournamentSettings, Long> {

    @Modifying
    @Query("UPDATE TournamentSettings ts SET ts.registeredPlayersCount = ts.registeredPlayersCount + 1 " +
           "WHERE ts.tournamentId = :tournamentId AND ts.registeredPlayersCount < :maxCapacity")
    int incrementRegisteredCountIfUnderCapacity(@Param("tournamentId") Long tournamentId, @Param("maxCapacity") int maxCapacity);

    @Modifying
    @Query("UPDATE TournamentSettings ts SET ts.registeredPlayersCount = CASE WHEN ts.registeredPlayersCount > 0 THEN ts.registeredPlayersCount - 1 ELSE 0 END " +
           "WHERE ts.tournamentId = :tournamentId")
    int decrementRegisteredCount(@Param("tournamentId") Long tournamentId);
}
