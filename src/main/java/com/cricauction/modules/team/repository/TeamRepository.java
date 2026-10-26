package com.cricauction.modules.team.repository;

import com.cricauction.modules.team.entity.Team;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TeamRepository extends JpaRepository<Team, Long> {

    List<Team> findByTournamentIdOrderByIdAsc(Long tournamentId);

    List<Team> findByTournamentIdAndStatusOrderByIdAsc(Long tournamentId, String status);

    Optional<Team> findByInviteCode(String inviteCode);

    Optional<Team> findByIdAndTournamentId(Long id, Long tournamentId);

    boolean existsByInviteCode(String inviteCode);

    long countByTournamentId(Long tournamentId);

    long countByTournamentIdAndStatus(Long tournamentId, String status);

    Optional<Team> findByTournamentIdAndCaptainId(Long tournamentId, Long captainId);

    boolean existsByTournamentIdAndCaptainId(Long tournamentId, Long captainId);

    @Query("SELECT t FROM Team t LEFT JOIN FETCH t.squadMembers WHERE t.id = :id")
    Optional<Team> findByIdWithSquad(@Param("id") Long id);
}
