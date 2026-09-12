package com.shopflow.realtime.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP-over-WebSocket configuration. Clients connect at {@code /ws} and
 * subscribe to {@code /topic/**} destinations. For multi-instance scale-out,
 * replace the simple broker with a relay (Redis/RabbitMQ) so messages reach
 * clients regardless of which pod holds the socket.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Single-instance in-memory broker. For multi-pod scale-out, swap this for
        // enableStompBrokerRelay("/topic") backed by Redis/RabbitMQ so a message
        // reaches clients regardless of which pod holds their socket.
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }
}
