package com.cricauction.modules.player.repository;

import com.cricauction.modules.player.entity.TournamentPlayer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TournamentPlayerRepository extends JpaRepository<TournamentPlayer, Long> {

    @Query("SELECT tp FROM TournamentPlayer tp JOIN FETCH tp.tournament t WHERE t.publicId = :tournamentPublicId AND tp.user.id = :userId")
    Optional<TournamentPlayer> findByTournamentPublicIdAndUserId(@Param("tournamentPublicId") String tournamentPublicId, @Param("userId") Long userId);

    @Query("SELECT tp FROM TournamentPlayer tp JOIN FETCH tp.tournament t WHERE tp.id = :id AND t.publicId = :tournamentPublicId")
    Optional<TournamentPlayer> findByIdAndTournamentPublicId(@Param("id") Long id, @Param("tournamentPublicId") String tournamentPublicId);

    @Query("SELECT CASE WHEN COUNT(tp) > 0 THEN TRUE ELSE FALSE END FROM TournamentPlayer tp WHERE tp.tournament.id = :tournamentId AND tp.user.id = :userId")
    boolean existsByTournamentIdAndUserId(@Param("tournamentId") Long tournamentId, @Param("userId") Long userId);

    @Query("SELECT COUNT(tp) FROM TournamentPlayer tp WHERE tp.tournament.id = :tournamentId")
    long countByTournamentId(@Param("tournamentId") Long tournamentId);

    @Query("SELECT tp FROM TournamentPlayer tp " +
           "WHERE tp.tournament.id = :tournamentId " +
           "AND (:status IS NULL OR LOWER(tp.status) = LOWER(:status)) " +
           "AND (:role IS NULL OR LOWER(REPLACE(tp.registeredRole, '-', ' ')) = LOWER(REPLACE(:role, '-', ' '))) " +
           "AND (:search IS NULL OR LOWER(tp.playerName) LIKE LOWER(CONCAT('%', :search, '%')) OR tp.mobile LIKE CONCAT('%', :search, '%')) " +
           "ORDER BY tp.id ASC")
    Page<TournamentPlayer> findPlayersFiltered(
            @Param("tournamentId") Long tournamentId,
            @Param("status") String status,
            @Param("role") String role,
            @Param("search") String search,
            Pageable pageable
    );

    @Query("SELECT tp.status, COUNT(tp) FROM TournamentPlayer tp WHERE tp.tournament.id = :tournamentId GROUP BY tp.status")
    List<Object[]> getStatusCountsByTournamentId(@Param("tournamentId") Long tournamentId);

    @Query("SELECT LOWER(tp.registeredRole), COUNT(tp) FROM TournamentPlayer tp WHERE tp.tournament.id = :tournamentId GROUP BY LOWER(tp.registeredRole)")
    List<Object[]> getRoleCountsByTournamentId(@Param("tournamentId") Long tournamentId);
}
