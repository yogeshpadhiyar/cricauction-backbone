package com.cricauction.modules.match.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BallUpdateResponse {
    private String matchId;
    private Integer innings;
    private Integer over;
    private Integer ball;
    private Integer runs;
    private Boolean isWicket;
    private Integer totalRuns;
    private Integer totalWickets;
    private String oversFormatted;
    private String matchStatus;
}

