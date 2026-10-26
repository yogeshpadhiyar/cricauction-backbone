package com.cricauction.modules.team.service.impl;

import com.cricauction.modules.team.event.CaptainApprovalEvent;
import com.cricauction.modules.team.service.CaptainNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultCaptainNotificationService implements CaptainNotificationService {

    private final ApplicationEventPublisher eventPublisher;

    @Override
    public void notifyCaptainApprovalStatus(CaptainApprovalEvent event) {
        log.info("DISPATCHING NOTIFICATION to Captain [{} (User {})] for Team [{}] in Tournament [{}]: Status = [{}] | Reason = [{}]",
                event.getCaptainName(), event.getCaptainUserId(), event.getTeamName(),
                event.getTournamentName(), event.getStatus(), event.getReason());

        // Publish Spring Application Event for asynchronous consumption by Notification Module
        eventPublisher.publishEvent(event);
    }
}
