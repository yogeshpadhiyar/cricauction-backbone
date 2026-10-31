package com.cricauction.modules.auction.strategy;

import lombok.Builder;
import lombok.Getter;

/**
 * Result of bid calculation and strategy validation.
 */
@Getter
@Builder
public class BidCalculationResult {
    private final long finalBidAmount;
    private final long incrementApplied;
    private final String strategyApplied;
}
