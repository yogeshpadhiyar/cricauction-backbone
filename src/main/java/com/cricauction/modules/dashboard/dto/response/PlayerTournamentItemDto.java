package com.cricauction.modules.dashboard.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlayerTournamentItemDto {
    private String tournamentId;
    private String name;
    private String status;
    private String auctionDate;
    private String badge;
}
