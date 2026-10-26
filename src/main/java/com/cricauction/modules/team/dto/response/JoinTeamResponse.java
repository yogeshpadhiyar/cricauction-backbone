package com.cricauction.modules.team.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JoinTeamResponse {

    private Long teamId;
    private String teamName;
    private String tournamentId;
    private String tournamentName;
    private String role;
    private String status;
}
