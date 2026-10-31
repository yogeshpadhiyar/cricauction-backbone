package com.cricauction.modules.auction.dto.response;

import com.cricauction.modules.auction.strategy.BiddingStrategyType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuctionStateResponse {
    private String status;
    private Boolean isPaused;
    private Integer round;
    private CurrentPlayerDto currentPlayer;
    private List<AuctionTeamDto> teams;
    private long upcomingQueueCount;
    private long soldCount;
    private long unsoldCount;
    private BiddingStrategyType bidStrategy;
    private Double incrementPercentage;
    private Long minBidIncrement;
    private Long nextMinimumBid;
}
