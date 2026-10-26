package com.cricauction.modules.tournament.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateTournamentRequest {

    @Schema(example = "Surat Premier League 2026 - Season 2")
    private String name;

    @Schema(example = "Surat, Gujarat")
    private String location;

    @Schema(example = "Lalbhai Contractor Stadium")
    private String ground;

    @Schema(example = "2026-09-05")
    private LocalDate startDate;

    @Schema(example = "2026-09-15")
    private LocalDate endDate;

    @Schema(example = "2026-08-28")
    private LocalDate auctionDate;

    @Schema(example = "19:00")
    private String auctionTime;

    @Schema(example = "2026-08-01")
    private LocalDate registrationStartDate;

    @Schema(example = "2026-08-25")
    private LocalDate registrationEndDate;

    @Schema(example = "Tennis")
    private String ballType;

    @Schema(example = "T20")
    private String format;

    @Schema(example = "Free")
    private String entryFee;

    @Schema(example = "Updated description of the tournament.")
    private String description;

    @Schema(example = "https://cdn.cricauction.app/banners/spl26-updated.jpg")
    private String bannerUrl;
}
