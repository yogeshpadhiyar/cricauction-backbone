package com.cricauction.modules.match.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateMatchFixtureRequest {

    @NotNull(message = "Team 1 ID is required")
    private Long team1Id;

    @NotNull(message = "Team 2 ID is required")
    private Long team2Id;

    @NotNull(message = "Scheduled time is required")
    private Instant scheduledAt;

    @Size(max = 150, message = "Ground name cannot exceed 150 characters")
    private String ground;

    @Min(value = 1, message = "Overs must be at least 1")
    @Max(value = 100, message = "Overs cannot exceed 100")
    @Builder.Default
    private Integer overs = 20;
}

