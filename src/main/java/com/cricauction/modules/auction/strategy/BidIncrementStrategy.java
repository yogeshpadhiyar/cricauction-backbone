package com.cricauction.modules.auction.strategy;

import com.cricauction.modules.auction.dto.request.PlaceBidRequest;
import com.cricauction.modules.auction.entity.LiveAuctionState;
import com.cricauction.modules.tournament.entity.TournamentSettings;

/**
 * Strategy interface defining how bid increments are calculated, validated,
 * and applied during a live auction.
 * Implementations are registered in Spring and dynamically resolved via {@link BidStrategyResolver}.
 */
public interface BidIncrementStrategy {

    /**
     * Unique identifier for this strategy.
     */
    BiddingStrategyType getStrategyType();

    /**
     * Calculates the minimum acceptable next bid amount for room state snapshots / client hints.
     *
     * @param state Current auction state
     * @param settings Tournament settings fallback
     * @return Minimum acceptable next bid
     */
    long calculateMinimumNextBid(LiveAuctionState state, TournamentSettings settings);

    /**
     * Calculates and validates the final bid amount for an incoming captain bid request.
     *
     * @param state Current auction state
     * @param request The incoming bid request (may contain explicit bidAmount, additionalAmount, or be empty for auto-increment)
     * @param settings Tournament settings fallback
     * @return Validated calculation result
     */
    BidCalculationResult calculateAndValidateBid(
            LiveAuctionState state,
            PlaceBidRequest request,
            TournamentSettings settings
    );
}
