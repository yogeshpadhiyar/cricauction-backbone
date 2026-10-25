package com.cricauction.modules.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OtpLoginRequest {

    @NotBlank(message = "Identifier (email or mobile) is required")
    @Schema(description = "User's registered email address or mobile number", 
            example = "+919876543210 or virat@example.com")
    private String identifier;

    @NotBlank(message = "OTP code is required")
    @Pattern(regexp = "^\\d{6}$", message = "OTP must be exactly 6 digits")
    @Schema(description = "6-digit OTP received via SMS or Email", example = "123456")
    private String otp;

    @Schema(description = "Role if newly registering via OTP login (organizer, captain, player)", example = "player")
    private String role;

    @Schema(description = "City of residence (optional)", example = "Surat")
    private String city;
}
