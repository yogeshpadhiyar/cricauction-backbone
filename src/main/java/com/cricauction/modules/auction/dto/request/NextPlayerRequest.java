package com.cricauction.modules.auction.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NextPlayerRequest {
    @Positive(message = "playerId must be positive")
    @Schema(example = "1", description = "Optional player database ID or numeric identifier. If omitted, picks next player in queue.")
    private Long playerId;
}
