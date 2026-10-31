package com.cricauction.modules.auction.entity;

import com.cricauction.modules.player.entity.TournamentPlayer;
import com.cricauction.modules.team.entity.Team;
import com.cricauction.modules.tournament.entity.Tournament;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * Captain's Target Player Wishlist.
 * Maps to captain_wishlists in PostgreSQL.
 */
@Entity
@Table(name = "captain_wishlists", uniqueConstraints = {
        @UniqueConstraint(name = "uq_captain_wishlist", columnNames = {"team_id", "tournament_player_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CaptainWishlist {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tournament_id", nullable = false)
    private Tournament tournament;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tournament_player_id", nullable = false)
    private TournamentPlayer tournamentPlayer;

    @Column(name = "max_budget")
    private Long maxBudget;

    @Column(name = "priority", length = 20)
    @Builder.Default
    private String priority = "Normal";

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
