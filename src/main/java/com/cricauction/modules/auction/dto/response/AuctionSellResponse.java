package com.cricauction.modules.auction.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuctionSellResponse {
    private Long playerId;
    private String playerName;
    private Long soldToTeamId;
    private String soldToTeamName;
    private Long finalPrice;
    private Long remainingPurse;
}
