package com.cricauction.modules.team.service;

import com.cricauction.modules.team.event.CaptainApprovalEvent;

public interface CaptainNotificationService {

    /**
     * Dispatches notification to captain upon approval or rejection.
     */
    void notifyCaptainApprovalStatus(CaptainApprovalEvent event);
}
