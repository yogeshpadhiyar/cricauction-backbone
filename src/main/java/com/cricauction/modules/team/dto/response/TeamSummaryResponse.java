package com.cricauction.modules.team.dto.response;

import com.cricauction.modules.team.entity.Team;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeamSummaryResponse {

    private Long id;
    private String name;
    private String shortCode;
    private String logoUrl;
    private String captainName;
    private String captainMobile;
    private String status;
    private int squadCount;
    private Long remainingBudget;
    private Long totalBudget;
    private String inviteCode;

    public static TeamSummaryResponse fromEntity(Team team, int squadCount) {
        return TeamSummaryResponse.builder()
                .id(team.getId())
                .name(team.getName())
                .shortCode(team.getShortCode())
                .logoUrl(team.getLogoUrl())
                .captainName(team.getCaptainName() != null ? team.getCaptainName() : "")
                .captainMobile(team.getCaptainMobile() != null ? team.getCaptainMobile() : "")
                .status(team.getStatus())
                .squadCount(squadCount)
                .remainingBudget(team.getRemainingBudget())
                .totalBudget(team.getInitialBudget())
                .inviteCode(team.getInviteCode())
                .build();
    }
}
