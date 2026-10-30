package com.cricauction.modules.player.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdatePlayerBasePriceRequest {

    @NotNull(message = "Base price is required")
    @PositiveOrZero(message = "Base price must be greater than or equal to zero")
    private Long basePrice;
}
