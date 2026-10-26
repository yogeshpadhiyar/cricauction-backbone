package com.cricauction.modules.tournament.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateTournamentRequest {

    @NotBlank(message = "Tournament name is required")
    @Schema(example = "Surat Premier League 2026")
    private String name;

    @NotBlank(message = "Location is required")
    @Schema(example = "Surat, Gujarat")
    private String location;

    @NotBlank(message = "Ground / stadium is required")
    @Schema(example = "Lalbhai Contractor Stadium")
    private String ground;

    @NotNull(message = "Start date is required")
    @Schema(example = "2026-09-05")
    private LocalDate startDate;

    @NotNull(message = "End date is required")
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

    @NotBlank(message = "Ball type is required")
    @Schema(example = "Tennis")
    private String ballType;

    @NotBlank(message = "Match format is required")
    @Schema(example = "T20")
    private String format;

    @Schema(example = "Free")
    @Builder.Default
    private String entryFee = "Free";

    @Schema(example = "Surat Premier League annual tournament and live auction.")
    private String description;

    @Schema(example = "https://cdn.cricauction.app/banners/spl26.jpg")
    private String bannerUrl;
}
