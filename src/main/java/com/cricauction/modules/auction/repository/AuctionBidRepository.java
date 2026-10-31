package com.cricauction.modules.auction.repository;

import com.cricauction.modules.auction.entity.AuctionBid;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AuctionBidRepository extends JpaRepository<AuctionBid, Long> {

    List<AuctionBid> findByTournamentIdAndTournamentPlayerIdOrderByIdDesc(Long tournamentId, Long playerId);

    @Query("SELECT COUNT(b) FROM AuctionBid b WHERE b.tournament.id = :tournamentId AND b.tournamentPlayer.id = :playerId")
    int countBidsForPlayer(@Param("tournamentId") Long tournamentId, @Param("playerId") Long playerId);
}
