package com.cricauction.modules.match.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(
        name = "match_balls",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_match_innings_over_ball", columnNames = {"match_id", "innings", "over_number", "ball_number"})
        },
        indexes = {
                @Index(name = "idx_match_balls_timeline", columnList = "match_id, innings, id DESC")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MatchBall {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "match_id", nullable = false)
    private Match match;

    @Column(name = "innings", nullable = false)
    @Builder.Default
    private Integer innings = 1;

    @Column(name = "over_number", nullable = false)
    private Integer overNumber;

    @Column(name = "ball_number", nullable = false)
    private Integer ballNumber;

    @Column(name = "runs", nullable = false)
    @Builder.Default
    private Integer runs = 0;

    @Column(name = "is_wicket", nullable = false)
    @Builder.Default
    private Boolean isWicket = false;

    @Column(name = "wicket_type", length = 30)
    private String wicketType;

    @Column(name = "extra_runs", nullable = false)
    @Builder.Default
    private Integer extraRuns = 0;

    @Column(name = "extra_type", length = 20)
    private String extraType;

    @Column(name = "batsman_id")
    private Long batsmanId;

    @Column(name = "batsman_name", length = 100)
    private String batsmanName;

    @Column(name = "bowler_id")
    private Long bowlerId;

    @Column(name = "bowler_name", length = 100)
    private String bowlerName;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}

