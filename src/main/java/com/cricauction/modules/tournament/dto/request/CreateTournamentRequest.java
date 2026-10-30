package com.cricauction.modules.tournament.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
public class CreateTournamentRequest {

    @NotBlank(message = "Tournament name is required")
    @Size(max = 100, message = "Tournament name cannot exceed 100 characters")
    @Schema(example = "Surat Premier League 2026")
    private String name;

    @NotBlank(message = "Location is required")
    @Size(max = 100, message = "Location cannot exceed 100 characters")
    @Schema(example = "Surat, Gujarat")
    private String location;

    @NotBlank(message = "Ground / stadium is required")
    @Size(max = 100, message = "Ground / stadium cannot exceed 100 characters")
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
    @Size(max = 20, message = "Auction time cannot exceed 20 characters")
    private String auctionTime;

    @Schema(example = "2026-08-01")
    private LocalDate registrationStartDate;

    @Schema(example = "2026-08-25")
    private LocalDate registrationEndDate;

    @NotBlank(message = "Ball type is required")
    @Size(max = 30, message = "Ball type cannot exceed 30 characters")
    @Schema(example = "Tennis")
    private String ballType;

    @NotBlank(message = "Match format is required")
    @Size(max = 30, message = "Match format cannot exceed 30 characters")
    @Schema(example = "T20")
    private String format;

    @Schema(example = "Free")
    @Size(max = 50, message = "Entry fee cannot exceed 50 characters")
    @Builder.Default
    private String entryFee = "Free";

    @Schema(example = "Surat Premier League annual tournament and live auction.")
    @Size(max = 2000, message = "Description cannot exceed 2000 characters")
    private String description;

    @Schema(example = "https://cdn.cricauction.app/banners/spl26.jpg")
    @Size(max = 500, message = "Banner URL cannot exceed 500 characters")
    @Pattern(regexp = "^(https?://.*)?$", message = "Banner URL must be a valid HTTP or HTTPS URL")
    private String bannerUrl;
}
