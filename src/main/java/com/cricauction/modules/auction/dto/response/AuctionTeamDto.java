package com.cricauction.modules.auction.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuctionTeamDto {
    private Long id;
    private String name;
    private String shortCode;
    private Long remainingBudget;
    private Long initialBudget;
    private long squadCount;
    private int maxSquad;
}
