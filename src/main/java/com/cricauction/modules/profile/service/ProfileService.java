package com.cricauction.modules.profile.service;

import com.cricauction.modules.auth.dto.response.UserProfileResponse;
import com.cricauction.modules.profile.dto.request.UpdatePlayerProfileRequest;
import com.cricauction.modules.profile.dto.request.UpdateUserProfileRequest;
import com.cricauction.modules.profile.dto.response.PlayerProfileResponse;
import com.cricauction.modules.profile.dto.response.ProfilePhotoUploadResponse;
import com.cricauction.security.UserPrincipal;
import org.springframework.web.multipart.MultipartFile;

public interface ProfileService {

    UserProfileResponse getUserProfile(UserPrincipal currentUser);

    UserProfileResponse getUserProfileById(Long userId);

    UserProfileResponse getUserProfileById(UserPrincipal currentUser, Long userId);

    UserProfileResponse updateUserProfile(UserPrincipal currentUser, UpdateUserProfileRequest request);

    ProfilePhotoUploadResponse uploadProfilePhoto(UserPrincipal currentUser, MultipartFile file);

    PlayerProfileResponse updatePlayerProfile(UserPrincipal currentUser, UpdatePlayerProfileRequest request);

    PlayerProfileResponse getPlayerProfile(UserPrincipal currentUser);
}
