package com.cricauction.modules.dashboard.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpcomingAuctionDto {
    private String tournamentId;
    private String name;
    private Instant auctionDate;
    private String displayDate;
    private boolean isLive;
}
