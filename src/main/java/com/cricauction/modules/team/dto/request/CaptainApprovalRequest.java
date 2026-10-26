package com.cricauction.modules.team.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CaptainApprovalRequest {

    @Schema(description = "Approval action: APPROVED or REJECTED", example = "APPROVED")
    @NotBlank(message = "Action must be APPROVED or REJECTED")
    private String action;

    @Schema(description = "Optional rejection reason or note", example = "Team registration approved for auction.")
    private String reason;
}
