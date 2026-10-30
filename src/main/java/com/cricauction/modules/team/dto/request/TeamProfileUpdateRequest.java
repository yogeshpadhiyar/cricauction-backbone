package com.cricauction.modules.team.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeamProfileUpdateRequest {

    @Schema(description = "Team display name", example = "Mumbai Paltan")
    @Size(max = 100, message = "Team name cannot exceed 100 characters")
    private String name;

    @Schema(description = "Team abbreviation or short code", example = "MI")
    @Size(max = 10, message = "Short code cannot exceed 10 characters")
    private String shortCode;

    @Schema(description = "Optional external team logo URL", example = "https://cdn.cricauction.app/teams/1/logo.png")
    @Size(max = 500, message = "Logo URL cannot exceed 500 characters")
    @Pattern(regexp = "^(https?://.*)?$", message = "Logo URL must be a valid HTTP or HTTPS URL")
    private String logoUrl;
}
