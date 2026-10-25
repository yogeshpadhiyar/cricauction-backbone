package com.cricauction.modules.auth.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SignUpResponse {
    private String userId;
    private String mobile;
    private String email;

    @JsonProperty("isVerified")
    private boolean isVerified;

    private int otpExpirySeconds;
}
