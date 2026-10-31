package com.cricauction.modules.auction.entity;

import com.cricauction.modules.auction.entity.enums.AuctionStatus;
import com.cricauction.modules.auction.strategy.BiddingStrategyType;
import com.cricauction.modules.player.entity.TournamentPlayer;
import com.cricauction.modules.team.entity.Team;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * High-Speed Live Auction Fast State Entity.
 * Maps to live_auction_state in PostgreSQL.
 */
@Entity
@Table(name = "live_auction_state")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LiveAuctionState {

    @Id
    @Column(name = "tournament_id")
    private Long tournamentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private AuctionStatus status = AuctionStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_player_id")
    private TournamentPlayer currentPlayer;

    @Column(name = "current_bid", nullable = false)
    @Builder.Default
    private Long currentBid = 0L;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "leading_team_id")
    private Team leadingTeam;

    @Column(name = "timer_seconds", nullable = false)
    @Builder.Default
    private Integer timerSeconds = 10;

    @Column(name = "is_paused", nullable = false)
    @Builder.Default
    private Boolean isPaused = false;

    @Column(name = "going_twice_warning", nullable = false)
    @Builder.Default
    private Integer goingTwiceWarning = 0;

    @Column(name = "round", nullable = false)
    @Builder.Default
    private Integer round = 1;

    @Enumerated(EnumType.STRING)
    @Column(name = "bid_strategy", length = 50)
    @Builder.Default
    private BiddingStrategyType bidStrategy = BiddingStrategyType.ADDITIONAL_AMOUNT;

    @Column(name = "increment_percentage")
    @Builder.Default
    private Double incrementPercentage = 10.0;

    @Column(name = "min_bid_increment")
    @Builder.Default
    private Long minBidIncrement = 1000L;

    @Column(name = "started_at")
    private Instant startedAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
