package com.cricauction.modules.team.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TournamentTeamsResponse {

    private int totalTeams;
    private int assignedCaptains;
    private List<TeamSummaryResponse> teams;
}
