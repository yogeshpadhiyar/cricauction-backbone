package com.cricauction.modules.auction.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CurrentPlayerDto {
    private Long id;
    private String name;
    private String role;
    private String battingStyle;
    private String bowlingStyle;
    private Long basePrice;
    private Long currentBid;
    private Long leadingTeamId;
    private String leadingTeam;
    private Integer secondsRemaining;
    private String photo;
}
