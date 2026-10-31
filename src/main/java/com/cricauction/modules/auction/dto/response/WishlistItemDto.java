package com.cricauction.modules.auction.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WishlistItemDto {
    private Long id;
    private Long playerId;
    private String playerName;
    private String playingRole;
    private Long basePrice;
    private String priority;
    private Long maxBudget;
    private String playerStatus;
}
