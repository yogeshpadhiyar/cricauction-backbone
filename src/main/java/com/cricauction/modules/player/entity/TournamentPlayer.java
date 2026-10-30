package com.cricauction.modules.player.entity;

import com.cricauction.modules.auth.entity.User;
import com.cricauction.modules.tournament.entity.Tournament;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * Entity representing a player's official registration in a tournament.
 */
@Entity
@Table(
        name = "tournament_players",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_tournament_player", columnNames = {"tournament_id", "user_id"})
        },
        indexes = {
                @Index(name = "idx_tourn_players_tournament", columnList = "tournament_id"),
                @Index(name = "idx_tourn_players_user", columnList = "user_id"),
                @Index(name = "idx_tourn_players_status", columnList = "tournament_id, status"),
                @Index(name = "idx_tourn_players_role", columnList = "tournament_id, registered_role")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TournamentPlayer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tournament_id", nullable = false)
    private Tournament tournament;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "player_name", nullable = false, length = 100)
    private String playerName;

    @Column(name = "registered_role", nullable = false, length = 50)
    private String registeredRole;

    @Column(name = "batting_style", length = 50)
    private String battingStyle;

    @Column(name = "bowling_style", length = 50)
    private String bowlingStyle;

    @Column(name = "base_price")
    @Builder.Default
    private Long basePrice = 5000L;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "mobile", length = 20)
    private String mobile;

    @Column(name = "jersey_size", length = 10)
    private String jerseySize;

    @Column(name = "cric_heroes_link", length = 500)
    private String cricHeroesLink;

    @Column(name = "photo_url", length = 500)
    private String photoUrl;

    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private String status = "Registered";

    @Column(name = "review_reason", length = 500)
    private String reviewReason;

    @Column(name = "lot_number")
    private Integer lotNumber;

    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Long version = 0L;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public String getPublicRegistrationId() {
        return "reg_" + id;
    }
}
