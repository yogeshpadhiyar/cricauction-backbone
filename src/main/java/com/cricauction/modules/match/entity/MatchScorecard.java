package com.cricauction.modules.match.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.data.domain.Persistable;

import java.time.Instant;

@Entity
@Table(name = "match_scorecards")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MatchScorecard implements Persistable<Long> {

    @Id
    @Column(name = "match_id")
    private Long matchId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "match_id", insertable = false, updatable = false)
    private Match match;

    @Column(name = "team1_runs", nullable = false)
    @Builder.Default
    private Integer team1Runs = 0;

    @Column(name = "team1_wickets", nullable = false)
    @Builder.Default
    private Integer team1Wickets = 0;

    @Column(name = "team1_balls", nullable = false)
    @Builder.Default
    private Integer team1Balls = 0;

    @Column(name = "team2_runs", nullable = false)
    @Builder.Default
    private Integer team2Runs = 0;

    @Column(name = "team2_wickets", nullable = false)
    @Builder.Default
    private Integer team2Wickets = 0;

    @Column(name = "team2_balls", nullable = false)
    @Builder.Default
    private Integer team2Balls = 0;

    @Column(name = "current_innings", nullable = false)
    @Builder.Default
    private Integer currentInnings = 1;

    @Column(name = "current_batsman_id")
    private Long currentBatsmanId;

    @Column(name = "current_batsman_name", length = 100)
    private String currentBatsmanName;

    @Column(name = "current_batsman_runs", nullable = false)
    @Builder.Default
    private Integer currentBatsmanRuns = 0;

    @Column(name = "current_batsman_balls", nullable = false)
    @Builder.Default
    private Integer currentBatsmanBalls = 0;

    @Column(name = "current_bowler_id")
    private Long currentBowlerId;

    @Column(name = "current_bowler_name", length = 100)
    private String currentBowlerName;

    @Column(name = "current_bowler_balls", nullable = false)
    @Builder.Default
    private Integer currentBowlerBalls = 0;

    @Column(name = "current_bowler_wickets", nullable = false)
    @Builder.Default
    private Integer currentBowlerWickets = 0;

    @Column(name = "current_bowler_runs", nullable = false)
    @Builder.Default
    private Integer currentBowlerRuns = 0;

    @Version
    @Column(name = "version")
    private Long version;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Transient
    @Builder.Default
    private boolean isNewRecord = true;

    @Override
    public Long getId() {
        return matchId;
    }

    @Override
    public boolean isNew() {
        return isNewRecord || version == null;
    }

    @PostPersist
    @PostLoad
    void markNotNew() {
        this.isNewRecord = false;
    }

    public static String formatBallsToOversString(int totalBalls) {
        return (totalBalls / 6) + "." + (totalBalls % 6);
    }

    public static Number formatBallsToOversNumber(int totalBalls) {
        int overs = totalBalls / 6;
        int balls = totalBalls % 6;
        if (balls == 0) {
            return overs;
        }
        return Double.parseDouble(overs + "." + balls);
    }

    public String getTeam1OversFormatted() {
        return formatBallsToOversString(team1Balls);
    }

    public String getTeam2OversFormatted() {
        return formatBallsToOversString(team2Balls);
    }

    public String getCurrentBowlerOversFormatted() {
        return formatBallsToOversString(currentBowlerBalls);
    }
}
