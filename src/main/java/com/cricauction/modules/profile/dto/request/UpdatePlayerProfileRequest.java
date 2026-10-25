package com.cricauction.modules.profile.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdatePlayerProfileRequest {

    /**
     * Optional userId in payload for explicit ownership check against authenticated token.
     */
    private Long userId;

    @NotBlank(message = "Playing role is required (e.g. Batsman, Bowler, All-Rounder, Wicket Keeper)")
    @Size(max = 50, message = "Playing role cannot exceed 50 characters")
    private String playingRole;

    @Size(max = 50, message = "Batting style cannot exceed 50 characters")
    private String battingStyle;

    @Size(max = 50, message = "Bowling style cannot exceed 50 characters")
    private String bowlingStyle;

    @Min(value = 0, message = "Jersey number cannot be negative")
    @Max(value = 999, message = "Jersey number cannot exceed 999")
    private Integer jerseyNumber;

    @Size(max = 300, message = "CricHeroes profile URL cannot exceed 300 characters")
    private String cricHeroesProfileUrl;

    @Min(value = 0, message = "Past tournaments count cannot be negative")
    private Integer pastTournaments;

    @Min(value = 0, message = "Highest score cannot be negative")
    private Integer highestScore;

    @Size(max = 20, message = "Best bowling figures cannot exceed 20 characters")
    private String bestBowling;
}
