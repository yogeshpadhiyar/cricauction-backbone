package com.cricauction.modules.dashboard.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlayerJourneyDto {
    private String tournamentId;
    private String tournamentName;
    private String status;
    private String auctionAt;
    private String team;
    private BigDecimal basePrice;
}
