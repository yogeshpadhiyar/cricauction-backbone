package com.cricauction.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Enterprise WebSocket Message Broker Configuration for Module 7 Live Auction Engine.
 * Supports native WebSocket and SockJS fallback at /ws/v1/auction.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic", "/queue");
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws/v1/auction")
                .setAllowedOriginPatterns("*")
                .withSockJS();
        registry.addEndpoint("/ws/v1/auction")
                .setAllowedOriginPatterns("*");

        registry.addEndpoint("/ws/v1/matches")
                .setAllowedOriginPatterns("*")
                .withSockJS();
        registry.addEndpoint("/ws/v1/matches")
                .setAllowedOriginPatterns("*");
    }
}
