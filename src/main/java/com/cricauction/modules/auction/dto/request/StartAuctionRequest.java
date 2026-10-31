package com.cricauction.modules.auction.dto.request;

import com.cricauction.modules.auction.strategy.BiddingStrategyType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request payload when the organizer initializes and starts a live auction room.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StartAuctionRequest {

    @Schema(example = "PERCENTAGE_INCREMENT", description = "Bidding strategy type: PERCENTAGE_INCREMENT or ADDITIONAL_AMOUNT")
    private BiddingStrategyType bidStrategy;

    @Positive(message = "incrementPercentage must be positive")
    @Schema(example = "10.0", description = "Percentage increment for PERCENTAGE_INCREMENT strategy (e.g. 10.0 for 10%)")
    private Double incrementPercentage;

    @Positive(message = "minBidIncrement must be positive")
    @Schema(example = "1000", description = "Minimum additional increment amount for ADDITIONAL_AMOUNT strategy")
    private Long minBidIncrement;
}
