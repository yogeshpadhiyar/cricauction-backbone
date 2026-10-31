package com.cricauction.modules.match.service;

import com.cricauction.modules.match.dto.MatchScoreUpdateEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * Real-time Match WebSocket Event Broadcaster for Module 8.
 * Sends STOMP events to:
 * - /topic/matches/{matchPublicId}/score
 * - /topic/tournaments/{tournamentPublicId}/matches
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MatchBroadcaster {

    private final SimpMessagingTemplate messagingTemplate;

    public void broadcastScoreUpdate(String matchPublicId, MatchScoreUpdateEvent event) {
        String destination = "/topic/matches/" + matchPublicId + "/score";
        try {
            messagingTemplate.convertAndSend(destination, event);
            log.debug("Match score update broadcasted to [{}]", destination);
        } catch (Exception ex) {
            log.warn("Failed to broadcast match score update to [{}]: {}", destination, ex.getMessage());
        }
    }

    public <T> void broadcastTournamentMatchUpdate(String tournamentPublicId, String eventType, T data) {
        String destination = "/topic/tournaments/" + tournamentPublicId + "/matches";
        try {
            messagingTemplate.convertAndSend(destination, data);
            log.debug("Tournament match event [{}] broadcasted to [{}]", eventType, destination);
        } catch (Exception ex) {
            log.warn("Failed to broadcast tournament match event to [{}]: {}", destination, ex.getMessage());
        }
    }
}

