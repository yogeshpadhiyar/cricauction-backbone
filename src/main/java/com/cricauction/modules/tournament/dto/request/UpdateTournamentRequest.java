package com.cricauction.modules.tournament.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
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
    @Size(max = 100, message = "Tournament name cannot exceed 100 characters")
    private String name;

    @Schema(example = "Surat, Gujarat")
    @Size(max = 100, message = "Location cannot exceed 100 characters")
    private String location;

    @Schema(example = "Lalbhai Contractor Stadium")
    @Size(max = 100, message = "Ground / stadium cannot exceed 100 characters")
    private String ground;

    @Schema(example = "2026-09-05")
    private LocalDate startDate;

    @Schema(example = "2026-09-15")
    private LocalDate endDate;

    @Schema(example = "2026-08-28")
    private LocalDate auctionDate;

    @Schema(example = "19:00")
    @Size(max = 20, message = "Auction time cannot exceed 20 characters")
    private String auctionTime;

    @Schema(example = "2026-08-01")
    private LocalDate registrationStartDate;

    @Schema(example = "2026-08-25")
    private LocalDate registrationEndDate;

    @Schema(example = "Tennis")
    @Size(max = 30, message = "Ball type cannot exceed 30 characters")
    private String ballType;

    @Schema(example = "T20")
    @Size(max = 30, message = "Match format cannot exceed 30 characters")
    private String format;

    @Schema(example = "Free")
    @Size(max = 50, message = "Entry fee cannot exceed 50 characters")
    private String entryFee;

    @Schema(example = "Updated description of the tournament.")
    @Size(max = 2000, message = "Description cannot exceed 2000 characters")
    private String description;

    @Schema(example = "https://cdn.cricauction.app/banners/spl26-updated.jpg")
    @Size(max = 500, message = "Banner URL cannot exceed 500 characters")
    @Pattern(regexp = "^(https?://.*)?$", message = "Banner URL must be a valid HTTP or HTTPS URL")
    private String bannerUrl;
}
