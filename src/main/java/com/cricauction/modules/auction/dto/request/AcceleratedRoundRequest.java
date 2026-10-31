package com.cricauction.modules.auction.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AcceleratedRoundRequest {

    @Schema(example = "true", description = "Whether to include unsold players")
    @Builder.Default
    private Boolean includeUnsold = true;

    @DecimalMin(value = "0.01", message = "revisedBasePriceFactor must be at least 0.01")
    @DecimalMax(value = "2.0", message = "revisedBasePriceFactor must not exceed 2.0")
    @Schema(example = "0.8", description = "Discount factor for revised base price (e.g. 0.8 = 20% discount)")
    private Double revisedBasePriceFactor;
}
