package com.cricauction.modules.team.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
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
public class InviteCaptainRequest {

    @Schema(description = "Dispatch channel: whatsapp, sms, or email", example = "whatsapp")
    @Builder.Default
    @Size(max = 20, message = "Channel name cannot exceed 20 characters")
    @Pattern(regexp = "^(?i)(whatsapp|sms|email)$", message = "Invite channel must be whatsapp, sms, or email")
    private String channel = "whatsapp";
}
