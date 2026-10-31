package com.cricauction.modules.team.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(
        name = "squad_members",
        indexes = {
                @Index(name = "idx_squad_members_team", columnList = "team_id"),
                @Index(name = "idx_squad_members_user", columnList = "player_user_id"),
                @Index(name = "idx_squad_members_tourn_player", columnList = "tournament_player_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SquadMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    @Column(name = "tournament_player_id")
    private Long tournamentPlayerId;

    @Column(name = "player_user_id")
    private Long playerUserId;

    @Column(name = "player_name", nullable = false, length = 100)
    private String playerName;

    @Column(name = "playing_role", length = 50)
    @Builder.Default
    private String playingRole = "All-Rounder";

    @Column(name = "purchase_price", nullable = false)
    @Builder.Default
    private Long purchasePrice = 0L;

    @Column(name = "is_captain", nullable = false)
    @Builder.Default
    private boolean isCaptain = false;

    @Column(name = "photo_url", length = 500)
    private String photoUrl;

    @CreationTimestamp
    @Column(name = "bought_at", updatable = false)
    private Instant boughtAt;
}
