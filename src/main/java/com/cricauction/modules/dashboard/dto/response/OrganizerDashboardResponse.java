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
public class OrganizerDashboardResponse {
    private boolean hasActiveDraft;
    private DraftSummaryDto draftSummary;
    private OrganizerStatsDto stats;
    private UpcomingTournamentDto upcomingTournament;
    private List<DashboardActivityDto> recentActivity;
}
