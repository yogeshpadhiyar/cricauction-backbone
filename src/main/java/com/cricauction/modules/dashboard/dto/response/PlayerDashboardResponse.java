package com.cricauction.modules.dashboard.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlayerDashboardResponse {
    private PlayerJourneyDto activeJourney;
    private List<PlayerTournamentItemDto> myTournaments;
    private List<UpcomingMatchDto> upcomingMatches;
}
