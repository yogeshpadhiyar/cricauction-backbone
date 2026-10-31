package com.cricauction.modules.auction.entity;

import com.cricauction.modules.player.entity.TournamentPlayer;
import com.cricauction.modules.team.entity.Team;
import com.cricauction.modules.tournament.entity.Tournament;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * Immutable Audit Ledger of All Placed Bids in an Auction.
 * Maps to auction_bids in PostgreSQL.
 */
@Entity
@Table(name = "auction_bids")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuctionBid {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tournament_id", nullable = false)
    private Tournament tournament;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tournament_player_id", nullable = false)
    private TournamentPlayer tournamentPlayer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    @Column(name = "bid_amount", nullable = false)
    private Long bidAmount;

    @Column(name = "bid_number", nullable = false)
    @Builder.Default
    private Integer bidNumber = 1;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
