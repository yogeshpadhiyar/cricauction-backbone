package com.cricauction.modules.match.entity;

import com.cricauction.modules.match.entity.enums.MatchStatus;
import com.cricauction.modules.team.entity.Team;
import com.cricauction.modules.tournament.entity.Tournament;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(
        name = "matches",
        indexes = {
                @Index(name = "idx_matches_tourn_status", columnList = "tournament_id, status"),
                @Index(name = "idx_matches_team1", columnList = "team1_id"),
                @Index(name = "idx_matches_team2", columnList = "team2_id"),
                @Index(name = "idx_matches_scheduled", columnList = "tournament_id, scheduled_at")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Match {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, length = 50)
    private String publicId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tournament_id", nullable = false)
    private Tournament tournament;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team1_id", nullable = false)
    private Team team1;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team2_id", nullable = false)
    private Team team2;

    @Column(name = "scheduled_at", nullable = false)
    private Instant scheduledAt;

    @Column(name = "ground", length = 150)
    private String ground;

    @Column(name = "overs", nullable = false)
    @Builder.Default
    private Integer overs = 20;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private MatchStatus status = MatchStatus.UPCOMING;

    @Column(name = "winner_team_id")
    private Long winnerTeamId;

    @Column(name = "result_description", length = 255)
    private String resultDescription;

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
}

