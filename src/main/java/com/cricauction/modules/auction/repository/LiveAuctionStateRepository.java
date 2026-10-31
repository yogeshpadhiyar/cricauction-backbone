package com.cricauction.modules.auction.repository;

import com.cricauction.modules.auction.entity.LiveAuctionState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LiveAuctionStateRepository extends JpaRepository<LiveAuctionState, Long> {

    @Query("SELECT s FROM LiveAuctionState s LEFT JOIN FETCH s.currentPlayer LEFT JOIN FETCH s.leadingTeam WHERE s.tournamentId = :tournamentId")
    Optional<LiveAuctionState> findByTournamentId(@Param("tournamentId") Long tournamentId);
}
