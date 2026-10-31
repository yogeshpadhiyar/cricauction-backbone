package com.cricauction.modules.dashboard.service;

import com.cricauction.modules.dashboard.dto.response.CaptainDashboardResponse;
import com.cricauction.modules.dashboard.dto.response.OrganizerDashboardResponse;
import com.cricauction.modules.dashboard.dto.response.PlayerDashboardResponse;
import com.cricauction.security.UserPrincipal;

public interface DashboardService {

    OrganizerDashboardResponse getOrganizerDashboard(UserPrincipal currentUser);

    CaptainDashboardResponse getCaptainDashboard(UserPrincipal currentUser);

    PlayerDashboardResponse getPlayerDashboard(UserPrincipal currentUser);

    void invalidateUserCache(Long userId);
}
