package com.cricauction.modules.auth.dto.response;

import com.cricauction.modules.auth.entity.User;
import com.cricauction.modules.profile.dto.response.PlayerProfileResponse;
import com.cricauction.modules.profile.entity.PlayerProfile;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserProfileResponse {
    private String id;
    private String fullName;
    private String email;
    private String mobile;
    private String role;
    private String authProvider;
    private String city;
    private String bio;
    private String profilePhoto;

    @com.fasterxml.jackson.annotation.JsonProperty("isVerified")
    private boolean isVerified;

    private PlayerProfileResponse playerProfile;

    public static UserProfileResponse fromEntity(User user) {
        return fromEntity(user, null);
    }

    public static UserProfileResponse fromEntity(User user, PlayerProfile playerProfile) {
        return UserProfileResponse.builder()
                .id(user.getPublicUserId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .mobile(user.getMobile())
                .role(user.getActiveRole().getValue())
                .authProvider(user.getAuthProvider() != null ? user.getAuthProvider().getValue() : "local")
                .city(user.getCity())
                .bio(user.getBio())
                .profilePhoto(user.getProfilePhotoUrl())
                .isVerified(user.isMobileVerified() || user.isEmailVerified())
                .playerProfile(PlayerProfileResponse.fromEntity(playerProfile))
                .build();
    }
}
