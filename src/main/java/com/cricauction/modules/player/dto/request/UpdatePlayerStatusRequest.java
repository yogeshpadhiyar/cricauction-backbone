package com.cricauction.modules.player.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdatePlayerStatusRequest {

    @NotBlank(message = "Status is required (e.g., Registered, Approved, Rejected, Pending)")
    @Pattern(regexp = "^(?i)(Registered|Approved|Rejected|Pending)$", message = "Invalid player status. Allowed values: Registered, Approved, Rejected, Pending")
    @Size(max = 30, message = "Status must not exceed 30 characters")
    private String status;

    @Size(max = 500, message = "Reason must not exceed 500 characters")
    private String reason;
}
