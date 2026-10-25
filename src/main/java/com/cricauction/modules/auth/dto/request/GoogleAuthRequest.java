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
public class GoogleAuthRequest {

    @NotBlank(message = "Google ID token is required")
    @Schema(description = "Google ID Token obtained directly from Google Sign-In SDK (JWT)", 
            example = "eyJhbGciOiJSUzI1NiIsImtpZCI6IjEy...")
    private String idToken;

    @Schema(description = "Desired role when creating a new account (organizer, captain, player). Defaults to 'player'", 
            example = "player")
    private String role;

    @Schema(description = "City of residence (optional)", example = "Mumbai")
    private String city;

    @Schema(description = "Optional mobile number if provided during sign-up", example = "+919876543210")
    private String mobile;
}
