package com.cricauction.modules.team.entity;

import com.cricauction.modules.tournament.entity.Tournament;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "teams",
        indexes = {
                @Index(name = "idx_teams_tournament", columnList = "tournament_id"),
                @Index(name = "idx_teams_captain", columnList = "captain_id"),
                @Index(name = "idx_teams_invite_code", columnList = "invite_code"),
                @Index(name = "idx_teams_tournament_status", columnList = "tournament_id, status")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Team {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tournament_id", nullable = false)
    private Tournament tournament;

    @Column(name = "captain_id")
    private Long captainId;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "short_code", length = 10)
    private String shortCode;

    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    @Column(name = "captain_name", length = 100)
    private String captainName;

    @Column(name = "captain_mobile", length = 20)
    private String captainMobile;

    @Column(name = "initial_budget", nullable = false)
    @Builder.Default
    private Long initialBudget = 1000000L;

    @Column(name = "remaining_budget", nullable = false)
    @Builder.Default
    private Long remainingBudget = 1000000L;

    @Column(name = "invite_code", unique = true, nullable = false, length = 100)
    private String inviteCode;

    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private String status = "Pending";

    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Long version = 0L;

    @OneToMany(mappedBy = "team", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<SquadMember> squadMembers = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    public void addSquadMember(SquadMember member) {
        squadMembers.add(member);
        member.setTeam(this);
    }

    public void removeSquadMember(SquadMember member) {
        squadMembers.remove(member);
        member.setTeam(null);
    }
}
