package com.airbnb.warroom.chat;

import com.airbnb.warroom.common.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Chat persistence with server-side sanitisation to prevent stored XSS (PRD 10.1). */
@Service
public class ChatService {

    private static final int MAX_BODY = 2000;

    private final ChatMessageRepository repo;

    public ChatService(ChatMessageRepository repo) {
        this.repo = repo;
    }

    public Mono<ChatMessage> post(Long tripId, Long userId, String rawBody) {
        if (rawBody == null || rawBody.isBlank()) {
            return Mono.error(ApiException.badRequest("Message body is required"));
        }
        if (rawBody.length() > MAX_BODY) {
            return Mono.error(ApiException.badRequest("Message exceeds " + MAX_BODY + " characters"));
        }
        ChatMessage m = new ChatMessage();
        m.setTripId(tripId);
        m.setUserId(userId);
        m.setMsgType("chat");
        m.setBody(HtmlUtils.htmlEscape(rawBody.trim())); // neutralise markup before persisting
        m.setTs(System.currentTimeMillis());
        return repo.save(m);
    }

    public Mono<ChatMessage> system(Long tripId, String type, String body) {
        ChatMessage m = new ChatMessage();
        m.setTripId(tripId);
        m.setUserId(null);
        m.setMsgType(type);
        m.setBody(HtmlUtils.htmlEscape(body));
        m.setTs(System.currentTimeMillis());
        return repo.save(m);
    }

    public Flux<ChatMessage> history(Long tripId) {
        return repo.findByTripIdOrderByIdAsc(tripId);
    }
}
