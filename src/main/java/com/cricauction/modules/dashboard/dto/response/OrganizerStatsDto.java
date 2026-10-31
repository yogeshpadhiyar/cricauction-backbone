package com.cricauction.modules.dashboard.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrganizerStatsDto {
    private long myTournaments;
    private long teams;
    private long playersRegistered;
    private String upcomingAuctionDate;
}
