package com.cricauction.modules.auction.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BidResponse {
    private Long currentBid;
    private Long leadingTeamId;
    private String leadingTeamName;
    private Integer timerReset;
    private Long incrementApplied;
    private String strategyApplied;
}
