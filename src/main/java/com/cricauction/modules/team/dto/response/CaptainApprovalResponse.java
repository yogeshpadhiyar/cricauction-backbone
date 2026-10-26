package com.cricauction.modules.team.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CaptainApprovalResponse {

    private Long teamId;
    private String teamName;
    private Long captainUserId;
    private String status;
    private String message;
    private boolean notificationDispatched;
}
