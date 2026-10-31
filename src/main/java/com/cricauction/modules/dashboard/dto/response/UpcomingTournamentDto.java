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
public class UpcomingTournamentDto {
    private String id;
    private String name;
    private String status;
    private long teamsCount;
    private long playersCount;
    private Instant auctionDate;
    private String auctionFormatted;
}
