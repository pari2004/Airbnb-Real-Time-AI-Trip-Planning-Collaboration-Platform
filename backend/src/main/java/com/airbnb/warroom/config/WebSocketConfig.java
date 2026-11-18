package com.airbnb.warroom.config;

import com.airbnb.warroom.auth.JwtProvider;
import com.airbnb.warroom.concierge.ConciergeService;
import com.airbnb.warroom.realtime.BoardCommandService;
import com.airbnb.warroom.realtime.RoomHub;
import com.airbnb.warroom.realtime.TripWebSocketHandler;
import com.airbnb.warroom.trip.TripAccessService;
import com.airbnb.warroom.trip.TripRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter;

import java.util.Map;

/** Maps {@code /ws/trips/{tripId}} to the reactive Trip Room WebSocket handler. */
@Configuration
public class WebSocketConfig {

    @Bean
    public WebSocketHandler tripWebSocketHandler(ObjectMapper mapper, JwtProvider jwt, RoomHub hub,
                                                 BoardCommandService commands, ConciergeService concierge,
                                                 TripAccessService access, TripRepository trips) {
        return new TripWebSocketHandler(mapper, jwt, hub, commands, concierge, access, trips);
    }

    @Bean
    public HandlerMapping webSocketHandlerMapping(WebSocketHandler tripWebSocketHandler) {
        SimpleUrlHandlerMapping mapping = new SimpleUrlHandlerMapping();
        mapping.setUrlMap(Map.of("/ws/trips/**", tripWebSocketHandler));
        mapping.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return mapping;
    }

    @Bean
    public WebSocketHandlerAdapter webSocketHandlerAdapter() {
        return new WebSocketHandlerAdapter();
    }
}
