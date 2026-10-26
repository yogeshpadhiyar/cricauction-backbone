package com.cricauction.modules.team.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddTeamResponse {

    private Long id;
    private String name;
    private String status;
    private String inviteCode;
    private Long remainingBudget;
}
