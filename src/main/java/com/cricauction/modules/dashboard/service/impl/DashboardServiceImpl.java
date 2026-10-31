package com.cricauction.modules.dashboard.service.impl;

import com.cricauction.modules.dashboard.cache.DashboardCacheService;
import com.cricauction.modules.dashboard.dto.response.CaptainDashboardResponse;
import com.cricauction.modules.dashboard.dto.response.OrganizerDashboardResponse;
import com.cricauction.modules.dashboard.dto.response.PlayerDashboardResponse;
import com.cricauction.modules.dashboard.service.DashboardQueryService;
import com.cricauction.modules.dashboard.service.DashboardService;
import com.cricauction.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private final DashboardCacheService cacheService;
    private final DashboardQueryService queryService;

    @Override
    public OrganizerDashboardResponse getOrganizerDashboard(UserPrincipal currentUser) {
        String cacheKey = "dash:organizer:" + currentUser.getId();
        return cacheService.getOrCompute(cacheKey, () -> queryService.getOrganizerDashboard(currentUser.getId()));
    }

    @Override
    public CaptainDashboardResponse getCaptainDashboard(UserPrincipal currentUser) {
        String cacheKey = "dash:captain:" + currentUser.getId();
        return cacheService.getOrCompute(cacheKey, () -> queryService.getCaptainDashboard(currentUser.getId()));
    }

    @Override
    public PlayerDashboardResponse getPlayerDashboard(UserPrincipal currentUser) {
        String cacheKey = "dash:player:" + currentUser.getId();
        return cacheService.getOrCompute(cacheKey, () -> queryService.getPlayerDashboard(currentUser.getId()));
    }

    @Override
    public void invalidateUserCache(Long userId) {
        if (userId != null) {
            cacheService.evict("dash:organizer:" + userId);
            cacheService.evict("dash:captain:" + userId);
            cacheService.evict("dash:player:" + userId);
            log.debug("Evicted dashboard cache keys for userId={}", userId);
        }
    }
}
