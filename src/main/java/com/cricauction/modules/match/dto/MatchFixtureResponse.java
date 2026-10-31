package com.cricauction.modules.match.dto;

import com.cricauction.modules.match.entity.enums.MatchStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MatchFixtureResponse {
    private String id;
    private String team1;
    private String team2;
    private Instant scheduledAt;
    private String ground;
    private MatchStatus status;
}

