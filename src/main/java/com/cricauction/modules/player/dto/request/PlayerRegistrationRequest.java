package com.cricauction.modules.player.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlayerRegistrationRequest {

    @NotBlank(message = "Playing role is required (e.g., Batsman, Bowler, All-Rounder, Wicket Keeper)")
    @Size(max = 50, message = "Playing role must not exceed 50 characters")
    private String playingRole;

    @Size(max = 50, message = "Batting style must not exceed 50 characters")
    private String battingStyle;

    @Size(max = 50, message = "Bowling style must not exceed 50 characters")
    private String bowlingStyle;

    @PositiveOrZero(message = "Base price must be greater than or equal to zero")
    private Long basePrice;

    @Size(max = 100, message = "City must not exceed 100 characters")
    private String city;

    @Size(max = 20, message = "Mobile number must not exceed 20 characters")
    private String mobile;

    @Size(max = 10, message = "Jersey size must not exceed 10 characters")
    private String jerseySize;

    @Pattern(regexp = "^(https?://.*)?$", message = "CricHeroes link must be a valid HTTP or HTTPS URL")
    @Size(max = 500, message = "CricHeroes link must not exceed 500 characters")
    private String cricHeroesLink;
}
