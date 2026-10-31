package com.cricauction.modules.auction.service;

import com.cricauction.modules.auction.dto.response.AuctionEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * Real-time Auction WebSocket Event Broadcaster.
 * Sends STOMP events to /topic/tournaments/{tournamentPublicId}/auction.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuctionBroadcaster {

    private final SimpMessagingTemplate messagingTemplate;

    public <T> void broadcast(String tournamentPublicId, String eventType, T data) {
        String destination = "/topic/tournaments/" + tournamentPublicId + "/auction";
        AuctionEvent<T> event = AuctionEvent.<T>builder()
                .eventType(eventType)
                .tournamentId(tournamentPublicId)
                .data(data)
                .build();
        try {
            messagingTemplate.convertAndSend(destination, event);
            log.debug("WebSocket Event [{}] broadcasted to [{}]", eventType, destination);
        } catch (Exception ex) {
            log.warn("Failed to broadcast WebSocket Event [{}] to [{}]: {}", eventType, destination, ex.getMessage());
        }
    }
}
