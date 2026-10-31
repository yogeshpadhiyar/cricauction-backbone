package com.cricauction.modules.auction.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuctionUndoResponse {
    private Long currentBid;
    private Long leadingTeamId;
    private String leadingTeamName;
}
