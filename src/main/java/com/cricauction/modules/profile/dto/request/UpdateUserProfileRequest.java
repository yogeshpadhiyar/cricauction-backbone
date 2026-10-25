package com.cricauction.modules.profile.dto.request;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateUserProfileRequest {

    /**
     * Optional userId in payload for explicit ownership check against authenticated token.
     */
    private Long userId;

    @Size(min = 2, max = 100, message = "Full name must be between 2 and 100 characters")
    private String fullName;

    @Size(max = 20, message = "Mobile number cannot exceed 20 characters")
    private String mobile;

    @Size(max = 100, message = "City cannot exceed 100 characters")
    private String city;

    @Size(max = 500, message = "Bio cannot exceed 500 characters")
    private String bio;
}
