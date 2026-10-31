package com.cricauction.modules.match.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MatchScorecardResponse {
    private String matchId;
    private TeamInningScoreDto team1;
    private TeamInningScoreDto team2;
    private List<CurrentBatsmanDto> currentBatsmen;
    private CurrentBowlerDto currentBowler;
    private String status;
    private String resultDescription;
}

