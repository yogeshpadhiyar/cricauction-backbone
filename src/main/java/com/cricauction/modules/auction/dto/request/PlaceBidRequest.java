package com.cricauction.modules.auction.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlaceBidRequest {

    @NotNull(message = "teamId is required")
    @Schema(example = "1", description = "ID of the bidding team")
    private Long teamId;

    @Positive(message = "bidAmount must be greater than zero")
    @Schema(example = "15000", description = "Total bid valuation amount (optional if additionalAmount is provided or auto-increment applies)")
    private Long bidAmount;

    @Positive(message = "additionalAmount must be greater than zero")
    @Schema(example = "2000", description = "Additional amount to increase beyond current price (for ADDITIONAL_AMOUNT strategy)")
    private Long additionalAmount;
}
