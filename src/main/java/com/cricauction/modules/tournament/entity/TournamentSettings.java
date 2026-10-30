package com.cricauction.modules.tournament.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "tournament_settings")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TournamentSettings {

    @Id
    @Column(name = "tournament_id")
    private Long tournamentId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "tournament_id")
    private Tournament tournament;

    @Column(name = "total_teams", nullable = false)
    @Builder.Default
    private int totalTeams = 10;

    @Column(name = "squad_minimum", nullable = false)
    @Builder.Default
    private int squadMinimum = 12;

    @Column(name = "squad_maximum", nullable = false)
    @Builder.Default
    private int squadMaximum = 15;

    @Column(name = "team_budget", nullable = false)
    @Builder.Default
    private long teamBudget = 1000000L;

    @Column(name = "default_base_price", nullable = false)
    @Builder.Default
    private long defaultBasePrice = 20000L;

    @Column(name = "bid_increment", nullable = false)
    @Builder.Default
    private long bidIncrement = 10000L;

    @Column(name = "bid_timer_seconds", nullable = false)
    @Builder.Default
    private int bidTimerSeconds = 10;

    @Column(name = "auto_start_next", nullable = false)
    @Builder.Default
    private boolean autoStartNext = false;

    @Column(name = "matches", nullable = false)
    @Builder.Default
    private int matches = 20;

    @Column(name = "match_duration_hours", nullable = false)
    @Builder.Default
    private int matchDurationHours = 3;

    @Column(name = "allow_players_to_withdraw", nullable = false)
    @Builder.Default
    private boolean allowPlayersToWithdraw = true;

    @Column(name = "captain_registration_mode", nullable = false)
    @Builder.Default
    private String captainRegistrationMode = "INVITE_ONLY";

    @Column(name = "captain_approval_mode", nullable = false)
    @Builder.Default
    private String captainApprovalMode = "ORGANIZER_APPROVAL";

    @Column(name = "player_approval_mode", nullable = false)
    @Builder.Default
    private String playerApprovalMode = "ORGANIZER_APPROVAL";

    @Column(name = "registered_players_count", nullable = false)
    @Builder.Default
    private int registeredPlayersCount = 0;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
