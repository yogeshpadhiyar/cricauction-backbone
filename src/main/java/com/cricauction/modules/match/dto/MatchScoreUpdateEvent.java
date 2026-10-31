package com.cricauction.modules.match.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MatchScoreUpdateEvent {
    private String eventType;
    private String matchId;
    private MatchScorecardResponse scorecard;
    private BallUpdateResponse lastBall;
    private Instant timestamp;
}

