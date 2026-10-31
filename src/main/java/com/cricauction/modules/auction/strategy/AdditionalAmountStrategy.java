package com.cricauction.modules.auction.strategy;

import com.cricauction.common.exception.BadRequestException;
import com.cricauction.modules.auction.dto.request.PlaceBidRequest;
import com.cricauction.modules.auction.entity.LiveAuctionState;
import com.cricauction.modules.tournament.entity.TournamentSettings;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Strategy 2: Current price + additional bid amount increased by captain.
 * The minimum additional increment amount is configured by the organizer when starting the auction.
 */
@Slf4j
@Component
public class AdditionalAmountStrategy implements BidIncrementStrategy {

    private static final long DEFAULT_MIN_INCREMENT = 1000L;

    @Override
    public BiddingStrategyType getStrategyType() {
        return BiddingStrategyType.ADDITIONAL_AMOUNT;
    }

    @Override
    public long calculateMinimumNextBid(LiveAuctionState state, TournamentSettings settings) {
        Long currentBid = state.getCurrentBid() != null ? state.getCurrentBid() : 0L;
        if (state.getLeadingTeam() == null) {
            return currentBid;
        }
        long minIncrement = resolveMinIncrement(state, settings);
        return currentBid + minIncrement;
    }

    @Override
    public BidCalculationResult calculateAndValidateBid(
            LiveAuctionState state,
            PlaceBidRequest request,
            TournamentSettings settings
    ) {
        Long currentBid = state.getCurrentBid() != null ? state.getCurrentBid() : 0L;
        long minIncrement = resolveMinIncrement(state, settings);

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
                    .strategyApplied("ADDITIONAL_AMOUNT (Opening)")
                    .build();
        }

        // 2. Subsequent Bids on Player
        long minimumNextBid = currentBid + minIncrement;
        long finalBid;
        long incrementApplied;

        if (request.getAdditionalAmount() != null) {
            if (request.getAdditionalAmount() < minIncrement) {
                throw new BadRequestException("ADDITIONAL_BID_TOO_LOW",
                        "Additional bid amount (₹" + request.getAdditionalAmount()
                                + ") must be at least the minimum increment of ₹" + minIncrement + ".");
            }
            incrementApplied = request.getAdditionalAmount();
            finalBid = currentBid + incrementApplied;
        } else if (request.getBidAmount() != null) {
            long actualIncrease = request.getBidAmount() - currentBid;
            if (actualIncrease < minIncrement) {
                throw new BadRequestException("BID_TOO_LOW",
                        "Bid amount (₹" + request.getBidAmount()
                                + ") must increase current price (₹" + currentBid
                                + ") by at least ₹" + minIncrement
                                + " (minimum next bid: ₹" + minimumNextBid + ").");
            }
            finalBid = request.getBidAmount();
            incrementApplied = actualIncrease;
        } else {
            // Auto increment by minimum increment
            finalBid = minimumNextBid;
            incrementApplied = minIncrement;
        }

        return BidCalculationResult.builder()
                .finalBidAmount(finalBid)
                .incrementApplied(incrementApplied)
                .strategyApplied("ADDITIONAL_AMOUNT (Min: ₹" + minIncrement + ")")
                .build();
    }

    private long resolveMinIncrement(LiveAuctionState state, TournamentSettings settings) {
        if (state.getMinBidIncrement() != null && state.getMinBidIncrement() > 0) {
            return state.getMinBidIncrement();
        }
        if (settings != null && settings.getBidIncrement() > 0) {
            return settings.getBidIncrement();
        }
        return DEFAULT_MIN_INCREMENT;
    }
}
