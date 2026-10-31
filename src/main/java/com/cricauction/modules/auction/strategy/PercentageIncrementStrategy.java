package com.cricauction.modules.auction.strategy;

import com.cricauction.common.exception.BadRequestException;
import com.cricauction.modules.auction.dto.request.PlaceBidRequest;
import com.cricauction.modules.auction.entity.LiveAuctionState;
import com.cricauction.modules.tournament.entity.TournamentSettings;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Strategy 1: Next bid auto-increments based on a percentage provided by the organizer.
 * If captain specifies a custom bid/additional amount, it must be >= currentPrice * (1 + percentage/100).
 * If captain sends an auto-bid (omits bidAmount), system automatically calculates and applies the percentage increment.
 */
@Slf4j
@Component
public class PercentageIncrementStrategy implements BidIncrementStrategy {

    private static final double DEFAULT_PERCENTAGE = 10.0;

    @Override
    public BiddingStrategyType getStrategyType() {
        return BiddingStrategyType.PERCENTAGE_INCREMENT;
    }

    @Override
    public long calculateMinimumNextBid(LiveAuctionState state, TournamentSettings settings) {
        Long currentBid = state.getCurrentBid() != null ? state.getCurrentBid() : 0L;
        if (state.getLeadingTeam() == null) {
            // Opening bid is base price
            return currentBid;
        }

        double percentage = resolvePercentage(state);
        long increment = Math.max(1L, Math.round(currentBid * (percentage / 100.0)));
        return currentBid + increment;
    }

    @Override
    public BidCalculationResult calculateAndValidateBid(
            LiveAuctionState state,
            PlaceBidRequest request,
            TournamentSettings settings
    ) {
        Long currentBid = state.getCurrentBid() != null ? state.getCurrentBid() : 0L;
        double percentage = resolvePercentage(state);

        // 1. Opening Bid on Player
        if (state.getLeadingTeam() == null) {
            long finalBid;
            if (request.getAdditionalAmount() != null) {
                finalBid = currentBid + request.getAdditionalAmount();
            } else if (request.getBidAmount() != null) {
                if (request.getBidAmount() < currentBid) {
                    throw new BadRequestException("BID_TOO_LOW",
                            "Opening bid must be at least the base price of ₹" + currentBid + ".");
                }
                finalBid = request.getBidAmount();
            } else {
                finalBid = currentBid;
            }
            long incrementApplied = finalBid - currentBid;
            return BidCalculationResult.builder()
                    .finalBidAmount(finalBid)
                    .incrementApplied(incrementApplied)
                    .strategyApplied("PERCENTAGE_INCREMENT (Opening: " + percentage + "%)")
                    .build();
        }

        // 2. Subsequent Bids on Player
        long requiredIncrement = Math.max(1L, Math.round(currentBid * (percentage / 100.0)));
        long minimumNextBid = currentBid + requiredIncrement;
        long finalBid;
        long incrementApplied;

        if (request.getAdditionalAmount() != null) {
            if (request.getAdditionalAmount() < requiredIncrement) {
                throw new BadRequestException("BID_TOO_LOW",
                        "Additional bid amount (₹" + request.getAdditionalAmount() + ") must be at least ₹"
                                + requiredIncrement + " (" + percentage + "% increment on current price ₹" + currentBid + ").");
            }
            incrementApplied = request.getAdditionalAmount();
            finalBid = currentBid + incrementApplied;
        } else if (request.getBidAmount() != null) {
            if (request.getBidAmount() < minimumNextBid) {
                throw new BadRequestException("BID_TOO_LOW",
                        "Bid amount (₹" + request.getBidAmount() + ") must be at least ₹"
                                + minimumNextBid + " (" + percentage + "% increment of ₹" + requiredIncrement
                                + " on current price ₹" + currentBid + ").");
            }
            finalBid = request.getBidAmount();
            incrementApplied = finalBid - currentBid;
        } else {
            // Auto-increment mode: Captain clicks 'Bid' without specifying amount
            finalBid = minimumNextBid;
            incrementApplied = requiredIncrement;
        }

        return BidCalculationResult.builder()
                .finalBidAmount(finalBid)
                .incrementApplied(incrementApplied)
                .strategyApplied("PERCENTAGE_INCREMENT (" + percentage + "%)")
                .build();
    }

    private double resolvePercentage(LiveAuctionState state) {
        if (state.getIncrementPercentage() != null && state.getIncrementPercentage() > 0) {
            return state.getIncrementPercentage();
        }
        return DEFAULT_PERCENTAGE;
    }
}
