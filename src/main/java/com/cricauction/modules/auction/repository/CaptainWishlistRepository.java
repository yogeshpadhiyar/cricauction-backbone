package com.cricauction.modules.auction.repository;

import com.cricauction.modules.auction.entity.CaptainWishlist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CaptainWishlistRepository extends JpaRepository<CaptainWishlist, Long> {

    List<CaptainWishlist> findByTeamIdOrderByCreatedAtDesc(Long teamId);

    Optional<CaptainWishlist> findByTeamIdAndTournamentPlayerId(Long teamId, Long tournamentPlayerId);

    void deleteByTeamIdAndTournamentPlayerId(Long teamId, Long tournamentPlayerId);
}
