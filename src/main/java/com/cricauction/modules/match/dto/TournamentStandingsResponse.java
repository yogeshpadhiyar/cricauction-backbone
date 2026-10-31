package com.cricauction.modules.match.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TournamentStandingsResponse {
    private Integer rank;
    private String team;
    private Long teamId;
    private Integer played;
    private Integer won;
    private Integer lost;
    private Integer tied;
    private Integer noResult;
    private String nrr;
    private Integer points;
}

