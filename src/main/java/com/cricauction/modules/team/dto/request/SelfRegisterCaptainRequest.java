package com.cricauction.modules.team.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SelfRegisterCaptainRequest {

    @Schema(description = "Optional ID of an existing unassigned team in the tournament to claim", example = "5")
    private Long teamId;

    @Schema(description = "Team name (required if creating a new team)", example = "Western Warriors")
    @Size(max = 100, message = "Team name cannot exceed 100 characters")
    private String teamName;

    @Schema(description = "Optional short code for team (e.g. WW)", example = "WW")
    @Size(max = 10, message = "Short code cannot exceed 10 characters")
    private String shortCode;

    @Schema(description = "Captain mobile number (optional, defaults to profile mobile)", example = "+919876543210")
    private String captainMobile;
}
