package com.cricauction.modules.match.repository;

import com.cricauction.modules.match.entity.Match;
import com.cricauction.modules.match.entity.enums.MatchStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MatchRepository extends JpaRepository<Match, Long> {

    @Query("SELECT m FROM Match m " +
           "JOIN FETCH m.team1 " +
           "JOIN FETCH m.team2 " +
           "WHERE m.tournament.id = :tournamentId " +
           "ORDER BY m.scheduledAt ASC")
    List<Match> findByTournamentIdWithTeams(@Param("tournamentId") Long tournamentId);

    @Query("SELECT m FROM Match m " +
           "JOIN FETCH m.tournament " +
           "JOIN FETCH m.team1 " +
           "JOIN FETCH m.team2 " +
           "WHERE m.tournament.id = :tournamentId AND m.status = :status")
    List<Match> findByTournamentIdAndStatusWithTeams(@Param("tournamentId") Long tournamentId, @Param("status") MatchStatus status);

    @Query("SELECT m FROM Match m " +
           "JOIN FETCH m.tournament " +
           "JOIN FETCH m.team1 " +
           "JOIN FETCH m.team2 " +
           "WHERE m.publicId = :publicId")
    Optional<Match> findByPublicIdWithTeams(@Param("publicId") String publicId);

    @Query("SELECT m FROM Match m " +
           "JOIN FETCH m.tournament " +
           "JOIN FETCH m.team1 " +
           "JOIN FETCH m.team2 " +
           "WHERE m.id = :id")
    Optional<Match> findByIdWithTeams(@Param("id") Long id);
}
