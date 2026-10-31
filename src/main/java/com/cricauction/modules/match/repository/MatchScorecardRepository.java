package com.cricauction.modules.match.repository;

import com.cricauction.modules.match.entity.MatchScorecard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MatchScorecardRepository extends JpaRepository<MatchScorecard, Long> {

    @Query("SELECT s FROM MatchScorecard s " +
           "JOIN FETCH s.match m " +
           "JOIN FETCH m.team1 " +
           "JOIN FETCH m.team2 " +
           "WHERE s.matchId = :matchId")
    Optional<MatchScorecard> findByMatchIdWithMatchAndTeams(@Param("matchId") Long matchId);
}

