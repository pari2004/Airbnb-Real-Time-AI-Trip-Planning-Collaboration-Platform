package com.airbnb.warroom.realtime;

import com.airbnb.warroom.auth.AuthPrincipal;
import com.airbnb.warroom.auth.JwtProvider;
import com.airbnb.warroom.board.BoardDtos.MoveRequest;
import com.airbnb.warroom.board.BoardDtos.PinRequest;
import com.airbnb.warroom.common.ApiException;
import com.airbnb.warroom.common.PublicId;
import com.airbnb.warroom.concierge.ConciergeDtos.AiAnswer;
import com.airbnb.warroom.concierge.ConciergeDtos.AskRequest;
import com.airbnb.warroom.concierge.ConciergeService;
import com.airbnb.warroom.trip.TripAccessService;
import com.airbnb.warroom.trip.TripRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * Reactive WebSocket gateway for a Trip Room (PRD 6.1, 7.3.4). One socket per room carries all live
 * traffic. The handshake self-authenticates via a {@code ?token=<jwt>} query param (same JWT as REST)
 * and verifies room membership before admitting the connection. Outbound = snapshot + room-broadcast
 * stream + a per-session stream for targeted replies (conflicts, pong, errors).
 */
public class TripWebSocketHandler implements WebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(TripWebSocketHandler.class);

    private final ObjectMapper mapper;
    private final JwtProvider jwt;
    private final RoomHub hub;
    private final BoardCommandService commands;
    private final ConciergeService concierge;
    private final TripAccessService access;
    private final TripRepository trips;

    public TripWebSocketHandler(ObjectMapper mapper, JwtProvider jwt, RoomHub hub,
                                BoardCommandService commands, ConciergeService concierge,
                                TripAccessService access, TripRepository trips) {
        this.mapper = mapper;
        this.jwt = jwt;
        this.hub = hub;
        this.commands = commands;
        this.concierge = concierge;
        this.access = access;
        this.trips = trips;
    }

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        URI uri = session.getHandshakeInfo().getUri();
        Long tripId;
        AuthPrincipal principal;
        try {
            tripId = extractTripId(uri);
            principal = jwt.parse(extractToken(uri));
        } catch (Exception e) {
            return session.send(Mono.just(session.textMessage(json(
                            Envelope.of(Events.ERROR, null, Map.of("message", "Unauthorized: " + e.getMessage()))))))
                    .then(session.close());
        }
        Long userId = principal.userId();

        // Per-session sink for targeted replies.
        Sinks.Many<Envelope> personal = Sinks.many().unicast().onBackpressureBuffer();

        return access.requireMember(tripId, userId)
                .onErrorResume(err -> Mono.error(new ApiException(403, "Not a participant")))
                .flatMap(role -> {
                    int count = hub.join(tripId, userId);
                    commands.logPresence(tripId, userId, "joined").subscribe();
                    hub.broadcast(tripId, Events.PRESENCE_UPDATE, Map.of(
                            "participantId", PublicId.encode(PublicId.USER, userId),
                            "action", "joined",
                            "participantCount", count));

                    Flux<Envelope> setup = Flux.concat(
                            commands.buildSnapshot(tripId).map(snap -> Envelope.of(
                                    Events.SNAPSHOT, PublicId.encode(PublicId.TRIP, tripId), snap)),
                            trips.findById(tripId).map(t -> Envelope.of(
                                    Events.ROOM_STATE, PublicId.encode(PublicId.TRIP, tripId),
                                    Map.of("status", t.getStatus(),
                                            "leaderConnected", hub.isConnected(tripId, t.getLeaderUserId())))));

                    Flux<Envelope> live = Flux.merge(hub.subscribe(tripId), personal.asFlux());
                    Mono<Void> output = session.send(Flux.concat(setup, live).map(e -> session.textMessage(json(e))));

                    Mono<Void> input = session.receive()
                            .map(msg -> msg.getPayloadAsText())
                            .flatMap(text -> dispatch(tripId, userId, text, personal)
                                    .onErrorResume(err -> {
                                        personal.tryEmitNext(Envelope.of(Events.ERROR,
                                                PublicId.encode(PublicId.TRIP, tripId),
                                                Map.of("message", err.getMessage())));
                                        return Mono.empty();
                                    }))
                            .then();

                    return Mono.zip(output, input).then()
                            .doFinally(sig -> onDisconnect(tripId, userId));
                })
                .onErrorResume(err -> session.send(Mono.just(session.textMessage(json(
                                Envelope.of(Events.ERROR, null, Map.of("message", err.getMessage()))))))
                        .then(session.close()));
    }

    private Mono<Void> dispatch(Long tripId, Long userId, String text, Sinks.Many<Envelope> personal) {
        JsonNode node;
        try {
            node = mapper.readTree(text);
        } catch (Exception e) {
            return Mono.error(ApiException.badRequest("Malformed event JSON"));
        }
        String event = node.path("event").asText("");
        JsonNode payload = node.path("payload");
        String tripPublic = PublicId.encode(PublicId.TRIP, tripId);

        return switch (event) {
            case Events.BOARD_PIN -> commands.pin(tripId, userId, convert(payload, PinRequest.class)).then();
            case Events.BOARD_MOVE -> commands.move(tripId, userId, convert(payload, MoveRequest.class))
                    .doOnNext(outcome -> {
                        if (!outcome.applied()) {
                            personal.tryEmitNext(Envelope.of(Events.BOARD_CONFLICT, tripPublic, Map.of(
                                    "conflictType", "concurrentMove",
                                    "itemIds", List.of(outcome.item().itemId()),
                                    "details", "Your move was rejected (" + outcome.conflictReason()
                                            + "); reconcile to the current state.",
                                    "current", outcome.item())));
                        }
                    }).then();
            case Events.BOARD_LOCK -> commands.lock(tripId, userId, payload.path("itemId").asText()).then();
            case Events.BOARD_UNLOCK -> commands.unlock(tripId, userId, payload.path("itemId").asText()).then();
            case "trip.board.remove" -> commands.remove(tripId, userId, payload.path("itemId").asText());
            case Events.VOTE_OPEN_REQ -> commands.openVote(tripId, userId,
                    payload.path("itemId").asText(),
                    readStringList(payload.path("candidateVenueIds")),
                    payload.path("durationSeconds").isMissingNode() ? null : payload.path("durationSeconds").asInt())
                    .then();
            case Events.VOTE_CAST -> commands.castVote(tripId, userId,
                    payload.path("itemId").asText(), payload.path("choiceVenueId").asText());
            case "trip.vote.resolve" -> commands.resolveVoteByItem(tripId, userId,
                    payload.path("itemId").asText(), true).then();
            case Events.CHAT_SEND -> commands.chat(tripId, userId, payload.path("body").asText()).then();
            case Events.TYPING -> {
                hub.broadcast(tripId, Events.TYPING_UPDATE, Map.of(
                        "userId", PublicId.encode(PublicId.USER, userId),
                        "isTyping", payload.path("isTyping").asBoolean(false)));
                yield Mono.empty();
            }
            case Events.SYNC_PING -> {
                personal.tryEmitNext(Envelope.of(Events.SYNC_PONG, tripPublic, Map.of(
                        "clientSentAt", payload.path("clientSentAt").asLong(0),
                        "serverReceivedAt", System.currentTimeMillis())));
                yield Mono.empty();
            }
            case "trip.ai.ask" -> {
                AskRequest ask = new AskRequest(null, payload.path("question").asText(),
                        payload.path("anchorItemId").isMissingNode() ? null
                                : payload.path("anchorItemId").asText());
                yield concierge.ask(tripId, userId, ask)
                        .doOnNext(ans -> broadcastAi(tripId, userId, ans)).then();
            }
            default -> Mono.error(ApiException.badRequest("Unknown event: " + event));
        };
    }

    private void broadcastAi(Long tripId, Long userId, AiAnswer ans) {
        hub.broadcast(tripId, Events.AI_SUGGESTION, Map.of(
                "userId", PublicId.encode(PublicId.USER, userId),
                "answer", ans.answer(),
                "suggestedVenueId", ans.suggestedVenueId() == null ? "" : ans.suggestedVenueId(),
                "sourceEvents", ans.sourceEvents(),
                "grounded", ans.grounded()));
    }

    private void onDisconnect(Long tripId, Long userId) {
        int count = hub.leave(tripId, userId);
        commands.logPresence(tripId, userId, "left").subscribe();
        hub.broadcast(tripId, Events.PRESENCE_UPDATE, Map.of(
                "participantId", PublicId.encode(PublicId.USER, userId),
                "action", "left",
                "participantCount", count));
        // If the Leader just dropped, announce the deterministic "leader disconnected" room state.
        trips.findById(tripId).subscribe(t -> {
            if (t.getLeaderUserId().equals(userId) && !hub.isConnected(tripId, userId)) {
                hub.broadcast(tripId, Events.ROOM_STATE, Map.of(
                        "status", t.getStatus(), "leaderConnected", false));
            }
        });
    }

    // ---- parsing helpers --------------------------------------------------

    private Long extractTripId(URI uri) {
        String path = uri.getPath(); // /ws/trips/{tripId}
        String[] parts = path.split("/");
        return PublicId.decode(parts[parts.length - 1]);
    }

    private String extractToken(URI uri) {
        Map<String, String> params = UriComponentsBuilder.fromUri(uri).build().getQueryParams().toSingleValueMap();
        String token = params.get("token");
        if (token == null || token.isBlank()) {
            throw ApiException.unauthorized("Missing token query parameter");
        }
        return token;
    }

    private <T> T convert(JsonNode node, Class<T> type) {
        try {
            return mapper.convertValue(node, type);
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("Invalid payload for " + type.getSimpleName() + ": " + e.getMessage());
        }
    }

    private List<String> readStringList(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        return mapper.convertValue(node, mapper.getTypeFactory()
                .constructCollectionType(List.class, String.class));
    }

    private String json(Envelope e) {
        try {
            return mapper.writeValueAsString(e);
        } catch (Exception ex) {
            log.error("Failed to serialize envelope", ex);
            return "{\"event\":\"" + Events.ERROR + "\"}";
        }
    }
}
