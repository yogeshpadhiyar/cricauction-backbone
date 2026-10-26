package com.cricauction.modules.team.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Event published when an organizer approves or rejects a captain application.
 * Consumed by the Notification Module for multi-channel dispatch (FCM Push, In-App, SMS/WhatsApp).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CaptainApprovalEvent {

    private Long teamId;
    private String teamName;
    private Long tournamentId;
    private String tournamentPublicId;
    private String tournamentName;
    private Long captainUserId;
    private String captainName;
    private String captainMobile;
    private String status; // "APPROVED" or "REJECTED"
    private String reason;
    @Builder.Default
    private Instant timestamp = Instant.now();
}
