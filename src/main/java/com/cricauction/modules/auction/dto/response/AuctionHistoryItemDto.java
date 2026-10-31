package com.cricauction.modules.auction.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuctionHistoryItemDto {
    private Long id;
    private Integer round;
    private Long playerId;
    private String playerName;
    private String role;
    private String soldTo;
    private Long soldToTeamId;
    private Long price;
    private String status;
    private Instant timestamp;
}
