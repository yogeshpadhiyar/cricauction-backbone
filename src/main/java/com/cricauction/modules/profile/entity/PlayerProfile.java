package com.cricauction.modules.profile.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "player_profiles")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlayerProfile {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "playing_role", nullable = false, length = 50)
    private String playingRole;

    @Column(name = "batting_style", length = 50)
    private String battingStyle;

    @Column(name = "bowling_style", length = 50)
    private String bowlingStyle;

    @Column(name = "jersey_number")
    private Integer jerseyNumber;

    @Column(name = "cricheroes_link", length = 300)
    private String cricHeroesProfileUrl;

    @Column(name = "past_tournaments")
    @Builder.Default
    private Integer pastTournaments = 0;

    @Column(name = "highest_score")
    @Builder.Default
    private Integer highestScore = 0;

    @Column(name = "best_bowling", length = 20)
    private String bestBowling;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
