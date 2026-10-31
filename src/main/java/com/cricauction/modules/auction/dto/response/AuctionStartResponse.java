package com.cricauction.modules.auction.dto.response;

import com.cricauction.modules.auction.strategy.BiddingStrategyType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuctionStartResponse {
    private String auctionId;
    private String status;
    private Instant startedAt;
    private BiddingStrategyType bidStrategy;
    private Double incrementPercentage;
    private Long minBidIncrement;
}
