package com.cricauction.modules.tournament.entity;

import com.cricauction.modules.tournament.entity.enums.TournamentStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Entity
@Table(name = "tournaments")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Tournament {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, length = 50)
    private String publicId;

    @Column(name = "organizer_id", nullable = false)
    private Long organizerId;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "slug", nullable = false, unique = true, length = 180)
    private String slug;

    @Column(name = "location", nullable = false, length = 150)
    private String location;

    @Column(name = "ground", nullable = false, length = 150)
    private String ground;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "auction_date")
    private LocalDate auctionDate;

    @Column(name = "auction_time", length = 20)
    private String auctionTime;

    @Column(name = "registration_start_date")
    private LocalDate registrationStartDate;

    @Column(name = "registration_end_date")
    private LocalDate registrationEndDate;

    @Column(name = "ball_type", nullable = false, length = 50)
    private String ballType;

    @Column(name = "format", nullable = false, length = 50)
    private String format;

    @Column(name = "entry_fee", length = 50)
    @Builder.Default
    private String entryFee = "Free";

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "banner_url", length = 500)
    private String bannerUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private TournamentStatus status = TournamentStatus.DRAFT;

    @Column(name = "step", nullable = false)
    @Builder.Default
    private int step = 1;

    @Column(name = "registration_link", length = 500)
    private String registrationLink;

    @Column(name = "captain_invite_link", length = 500)
    private String captainInviteLink;

    @OneToOne(mappedBy = "tournament", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    private TournamentSettings settings;

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

    public void setSettings(TournamentSettings settings) {
        this.settings = settings;
        if (settings != null) {
            settings.setTournament(this);
        }
    }

    public String formatAuctionAt() {
        if (auctionDate == null) {
            return null;
        }
        DateTimeFormatter dayFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);
        String datePart = auctionDate.format(dayFormatter);

        if (auctionTime == null || auctionTime.isBlank()) {
            return datePart;
        }

        try {
            String[] parts = auctionTime.split(":");
            int hour = Integer.parseInt(parts[0].trim());
            int min = parts.length > 1 ? Integer.parseInt(parts[1].trim()) : 0;
            String amPm = hour >= 12 ? "PM" : "AM";
            int hour12 = hour % 12 == 0 ? 12 : hour % 12;
            String timePart = String.format("%d:%02d %s", hour12, min, amPm);
            return datePart + " • " + timePart;
        } catch (Exception e) {
            return datePart + " • " + auctionTime;
        }
    }
}
