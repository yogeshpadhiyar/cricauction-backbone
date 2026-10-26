package com.cricauction.modules.tournament.dto.response;

import com.cricauction.modules.tournament.entity.Tournament;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TournamentDetailResponse {
    private String id;
    private String name;
    private String slug;
    private String location;
    private String ground;
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalDate auctionDate;
    private String auctionTime;
    private String auctionAt;
    private LocalDate registrationStartDate;
    private LocalDate registrationEndDate;
    private String ballType;
    private String format;
    private String entryFee;
    private String description;
    private String bannerUrl;
    private String status;
    private int step;
    private String registrationLink;
    private String captainInviteLink;
    private TournamentSettingsDto settings;

    public static TournamentDetailResponse fromEntity(Tournament tournament) {
        if (tournament == null) {
            return null;
        }

        return TournamentDetailResponse.builder()
                .id(tournament.getPublicId())
                .name(tournament.getName())
                .slug(tournament.getSlug())
                .location(tournament.getLocation())
                .ground(tournament.getGround())
                .startDate(tournament.getStartDate())
                .endDate(tournament.getEndDate())
                .auctionDate(tournament.getAuctionDate())
                .auctionTime(tournament.getAuctionTime())
                .auctionAt(tournament.formatAuctionAt())
                .registrationStartDate(tournament.getRegistrationStartDate())
                .registrationEndDate(tournament.getRegistrationEndDate())
                .ballType(tournament.getBallType())
                .format(tournament.getFormat())
                .entryFee(tournament.getEntryFee())
                .description(tournament.getDescription())
                .bannerUrl(tournament.getBannerUrl())
                .status(tournament.getStatus() != null ? tournament.getStatus().getValue() : "draft")
                .step(tournament.getStep())
                .registrationLink(tournament.getRegistrationLink())
                .captainInviteLink(tournament.getCaptainInviteLink())
                .settings(TournamentSettingsDto.fromEntity(tournament.getSettings()))
                .build();
    }
}
