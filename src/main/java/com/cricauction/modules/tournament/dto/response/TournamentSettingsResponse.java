package com.cricauction.modules.tournament.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TournamentSettingsResponse {
    private int step;
    private int teamsAllocated;
}
