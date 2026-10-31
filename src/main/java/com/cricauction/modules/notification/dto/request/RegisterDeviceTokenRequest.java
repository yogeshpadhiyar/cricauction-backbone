package com.cricauction.modules.notification.dto.request;

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
public class RegisterDeviceTokenRequest {

    @NotBlank(message = "deviceToken is required")
    @Size(max = 500, message = "deviceToken cannot exceed 500 characters")
    private String deviceToken;

    @NotBlank(message = "platform is required")
    @Pattern(regexp = "(?i)^(ios|android|web)$", message = "platform must be 'ios', 'android', or 'web'")
    @Size(max = 20, message = "platform cannot exceed 20 characters")
    private String platform;
}
