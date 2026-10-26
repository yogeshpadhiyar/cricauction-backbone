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
public class TeamSquadResponse {

    private Long teamId;
    private String teamName;
    private String shortCode;
    private String logoUrl;
    private Long totalPurse;
    private Long spentPurse;
    private Long remainingPurse;
    private int squadCount;
    private int maxSquad;
    private List<SquadPlayerResponse> players;
}
