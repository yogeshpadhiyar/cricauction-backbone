package com.cricauction.modules.profile.dto.response;

import com.cricauction.modules.profile.entity.PlayerProfile;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PlayerProfileResponse {

    private String userId;
    private String playingRole;
    private String battingStyle;
    private String bowlingStyle;
    private Integer jerseyNumber;
    private String cricHeroesProfileUrl;
    private Integer pastTournaments;
    private Integer highestScore;
    private String bestBowling;
    private Instant updatedAt;

    public static PlayerProfileResponse fromEntity(PlayerProfile entity) {
        if (entity == null) {
            return null;
        }
        return PlayerProfileResponse.builder()
                .userId("usr_" + entity.getUserId())
                .playingRole(entity.getPlayingRole())
                .battingStyle(entity.getBattingStyle())
                .bowlingStyle(entity.getBowlingStyle())
                .jerseyNumber(entity.getJerseyNumber())
                .cricHeroesProfileUrl(entity.getCricHeroesProfileUrl())
                .pastTournaments(entity.getPastTournaments())
                .highestScore(entity.getHighestScore())
                .bestBowling(entity.getBestBowling())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
