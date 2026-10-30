package com.cricauction.modules.player.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.ALWAYS)
public class PlayerRegistrationStatusResponse {
    private String tournamentId;
    private String tournamentName;
    private String status;
    private Long basePrice;
    private String auctionSchedule;
    private String soldToTeam;
    private Long soldPrice;
}
