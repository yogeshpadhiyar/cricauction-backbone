package com.cricauction.modules.auth.dto.request;

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
public class SendOtpRequest {

    @Schema(description = "Target mobile number or email address", example = "+919876543210 or player@example.com")
    private String identifier;

    @Schema(description = "Mobile number (optional if identifier is provided)", example = "+919876543210")
    private String mobile;

    @Schema(description = "Email address (optional if identifier is provided)", example = "player@example.com")
    private String email;

    @NotBlank(message = "Purpose is required (e.g. login, signup_verification, password_reset)")
    @Schema(description = "Purpose of OTP", example = "login")
    private String purpose;

    public String getTargetIdentifier() {
        if (identifier != null && !identifier.isBlank()) {
            return identifier.trim();
        }
        if (mobile != null && !mobile.isBlank()) {
            return mobile.trim();
        }
        if (email != null && !email.isBlank()) {
            return email.trim();
        }
        return "";
    }
}
