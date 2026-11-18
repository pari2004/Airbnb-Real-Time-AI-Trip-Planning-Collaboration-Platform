package com.airbnb.warroom.realtime;

import com.airbnb.warroom.analytics.SessionEvent;
import com.airbnb.warroom.analytics.SessionEventRepository;
import com.airbnb.warroom.board.BoardDtos.ItemView;
import com.airbnb.warroom.board.BoardDtos.MoveOutcome;
import com.airbnb.warroom.board.BoardDtos.MoveRequest;
import com.airbnb.warroom.board.BoardDtos.PinRequest;
import com.airbnb.warroom.board.BoardService;
import com.airbnb.warroom.budget.BudgetService;
import com.airbnb.warroom.chat.ChatMessage;
import com.airbnb.warroom.chat.ChatService;
import com.airbnb.warroom.common.ApiException;
import com.airbnb.warroom.common.PublicId;
import com.airbnb.warroom.concierge.ConflictDetector;
import com.airbnb.warroom.concierge.ConciergeDtos.ConflictView;
import com.airbnb.warroom.trip.RoomRole;
import com.airbnb.warroom.trip.TripAccessService;
import com.airbnb.warroom.trip.TripParticipantRepository;
import com.airbnb.warroom.trip.TripRepository;
import com.airbnb.warroom.user.User;
import com.airbnb.warroom.user.UserRepository;
import com.airbnb.warroom.venue.Venue;
import com.airbnb.warroom.venue.VenueService;
import com.airbnb.warroom.vote.VoteDtos;
import com.airbnb.warroom.vote.VoteDtos.VoteResult;
import com.airbnb.warroom.vote.VoteService;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Single-source orchestration for every board-mutating command, shared verbatim by the WebSocket
 * gateway and the REST controllers (PRD 9.x: write the logic once, invoke from both entry points).
 * Each command enforces RBAC, mutates authoritative state, logs a structured event, broadcasts the
 * resulting room event(s), and re-runs conflict + budget evaluation.
 */
@Service
public class BoardCommandService {

    private final BoardService board;
    private final VenueService venueService;
    private final VoteService voteService;
    private final ChatService chatService;
    private final ConflictDetector conflictDetector;
    private final BudgetService budgetService;
    private final TripRepository trips;
    private final TripParticipantRepository participants;
    private final UserRepository users;
    private final SessionEventRepository events;
    private final RoomHub hub;
    private final TripAccessService access;

    public BoardCommandService(BoardService board, VenueService venueService, VoteService voteService,
                               ChatService chatService, ConflictDetector conflictDetector,
                               BudgetService budgetService, TripRepository trips,
                               TripParticipantRepository participants, UserRepository users,
                               SessionEventRepository events, RoomHub hub, TripAccessService access) {
        this.board = board;
        this.venueService = venueService;
        this.voteService = voteService;
        this.chatService = chatService;
        this.conflictDetector = conflictDetector;
        this.budgetService = budgetService;
        this.trips = trips;
        this.participants = participants;
        this.users = users;
        this.events = events;
        this.hub = hub;
        this.access = access;
    }

    // ---- Board ------------------------------------------------------------

    public Mono<ItemView> pin(Long tripId, Long userId, PinRequest req) {
        return access.requireCanPin(tripId, userId)
                .then(board.pin(tripId, userId, req))
                .map(ItemView::from)
                .flatMap(view -> {
                    hub.broadcast(tripId, Events.BOARD_UPDATE, view);
                    return logEvent(tripId, userId, "pin", "{\"itemId\":\"" + view.itemId() + "\"}")
                            .then(afterBoardMutation(tripId))
                            .thenReturn(view);
                });
    }

    public Mono<MoveOutcome> move(Long tripId, Long userId, MoveRequest req) {
        return access.requireCanPin(tripId, userId)
                .then(board.move(tripId, req))
                .flatMap(outcome -> {
                    if (outcome.applied()) {
                        hub.broadcast(tripId, Events.BOARD_UPDATE, outcome.item());
                        return logEvent(tripId, userId, "move", "{\"itemId\":\"" + outcome.item().itemId() + "\"}")
                                .then(afterBoardMutation(tripId))
                                .thenReturn(outcome);
                    }
                    return logEvent(tripId, userId, "move_conflict",
                            "{\"itemId\":\"" + outcome.item().itemId() + "\",\"reason\":\""
                                    + outcome.conflictReason() + "\"}")
                            .thenReturn(outcome);
                });
    }

    public Mono<ItemView> lock(Long tripId, Long userId, String itemPublicId) {
        Long itemId = PublicId.decode(itemPublicId);
        return access.requireLeader(tripId, userId)
                .then(board.lock(itemId))
                .map(ItemView::from)
                .flatMap(view -> {
                    hub.broadcast(tripId, Events.BOARD_UPDATE, view);
                    return logEvent(tripId, userId, "lock", "{\"itemId\":\"" + view.itemId() + "\"}")
                            .thenReturn(view);
                });
    }

    public Mono<ItemView> unlock(Long tripId, Long userId, String itemPublicId) {
        Long itemId = PublicId.decode(itemPublicId);
        return access.requireLeader(tripId, userId)
                .then(board.unlock(itemId))
                .map(ItemView::from)
                .flatMap(view -> {
                    hub.broadcast(tripId, Events.BOARD_UPDATE, view);
                    return logEvent(tripId, userId, "unlock", "{\"itemId\":\"" + view.itemId() + "\"}")
                            .thenReturn(view);
                });
    }

    public Mono<Void> remove(Long tripId, Long userId, String itemPublicId) {
        Long itemId = PublicId.decode(itemPublicId);
        return access.roleOf(tripId, userId)
                .flatMap(role -> board.remove(tripId, itemId, userId, role == RoomRole.LEADER))
                .then(Mono.fromRunnable(() ->
                        hub.broadcast(tripId, Events.BOARD_REMOVE, Map.of("itemId", itemPublicId))))
                .then(logEvent(tripId, userId, "remove", "{\"itemId\":\"" + itemPublicId + "\"}"))
                .then(afterBoardMutation(tripId));
    }

    // ---- Chat -------------------------------------------------------------

    public Mono<ChatMessage> chat(Long tripId, Long userId, String body) {
        return participants.find(tripId, userId)
                .switchIfEmpty(Mono.error(ApiException.forbidden("Not a participant of this trip")))
                .flatMap(p -> {
                    if (p.muted()) {
                        return Mono.error(ApiException.forbidden("You are muted in this room"));
                    }
                    return chatService.post(tripId, userId, body);
                })
                .flatMap(m -> {
                    hub.broadcast(tripId, Events.CHAT_MESSAGE, chatPayload(m));
                    return Mono.just(m);
                });
    }

    // ---- Voting -----------------------------------------------------------

    public Mono<VoteDtos.VoteView> openVote(Long tripId, Long userId, String itemId,
                                            List<String> candidateVenueIds, Integer durationSeconds) {
        return access.requireLeader(tripId, userId)
                .then(voteService.openVote(tripId, userId, itemId, candidateVenueIds, durationSeconds))
                .map(VoteDtos.VoteView::from)
                .flatMap(view -> {
                    hub.broadcast(tripId, Events.VOTE_OPEN, view);
                    return logEvent(tripId, userId, "vote_open", "{\"itemId\":\"" + view.itemId() + "\"}")
                            .thenReturn(view);
                });
    }

    public Mono<Void> castVote(Long tripId, Long userId, String itemId, String choiceVenueId) {
        return access.requireCanVote(tripId, userId)
                .then(voteService.castVote(tripId, userId, itemId, choiceVenueId))
                .then(Mono.fromRunnable(() -> hub.broadcast(tripId, Events.VOTE_CAST_ACK, Map.of(
                        "itemId", itemId,
                        "userId", PublicId.encode(PublicId.USER, userId),
                        "choiceVenueId", choiceVenueId))))
                .then(logEvent(tripId, userId, "vote_cast", "{\"itemId\":\"" + itemId + "\"}"));
    }

    /** Resolve an open vote for an item (leader action or scheduled expiry). */
    public Mono<VoteResult> resolveVoteByItem(Long tripId, Long userId, String itemPublicId, boolean requireLeader) {
        Long itemId = PublicId.decode(itemPublicId);
        Mono<Void> gate = requireLeader ? access.requireLeader(tripId, userId).then() : Mono.empty();
        return gate.then(voteService.resolveByItem(itemId))
                .flatMap(result -> broadcastResolution(tripId, userId, result));
    }

    /** Used by the vote-expiry scheduler to resolve + broadcast a timed-out vote. */
    public Mono<VoteResult> resolveSessionAndBroadcast(Long tripId, Long sessionId, Long actorUserId) {
        return voteService.resolve(sessionId).flatMap(result -> broadcastResolution(tripId, actorUserId, result));
    }

    private Mono<VoteResult> broadcastResolution(Long tripId, Long userId, VoteResult result) {
        hub.broadcast(tripId, Events.VOTE_RESOLVED, Map.of(
                "itemId", result.itemId(),
                "winningVenueId", result.winningVenueId(),
                "tally", result.tally()));
        Long itemId = PublicId.decode(result.itemId());
        return board.getItem(itemId).map(ItemView::from)
                .flatMap(view -> {
                    hub.broadcast(tripId, Events.BOARD_UPDATE, view);
                    return logEvent(tripId, userId, "vote_resolved",
                            "{\"itemId\":\"" + result.itemId() + "\",\"winner\":\""
                                    + result.winningVenueId() + "\"}")
                            .then(afterBoardMutation(tripId))
                            .thenReturn(result);
                });
    }

    // ---- Conflict + budget re-evaluation ----------------------------------

    private Mono<Void> afterBoardMutation(Long tripId) {
        return runConflictScan(tripId).then(broadcastBudget(tripId));
    }

    public Mono<Void> runConflictScan(Long tripId) {
        return trips.findById(tripId).flatMap(trip -> Mono.zip(
                        board.getBoard(tripId).collectList(),
                        venueService.loadVenues(trip.getDestination())
                                .map(vs -> vs.stream().collect(Collectors.toMap(Venue::id, v -> v))),
                        bindingCeiling(tripId).map(java.util.Optional::of)
                                .defaultIfEmpty(java.util.Optional.empty()))
                .flatMap(t -> {
                    List<ConflictView> conflicts = conflictDetector.detect(
                            t.getT1(), t.getT2(), t.getT3().orElse(null));
                    return Flux.fromIterable(conflicts)
                            .concatMap(c -> {
                                hub.broadcast(tripId, Events.BOARD_CONFLICT, c);
                                return chatService.system(tripId, "conflict", c.details())
                                        .then(logEvent(tripId, null, "conflict",
                                                "{\"type\":\"" + c.conflictType() + "\"}"));
                            })
                            .then();
                }));
    }

    public Mono<Void> broadcastBudget(Long tripId) {
        return budgetService.summary(tripId)
                .doOnNext(summary -> hub.broadcast(tripId, Events.BUDGET_UPDATE, summary))
                .then();
    }

    /** The binding per-person ceiling = the lowest declared ceiling among participants (or empty). */
    public Mono<BigDecimal> bindingCeiling(Long tripId) {
        return participants.findByTrip(tripId)
                .map(com.airbnb.warroom.trip.TripParticipant::userId)
                .collectList()
                .flatMap(ids -> ids.isEmpty() ? Mono.empty()
                        : users.findAllById(ids)
                        .map(User::getBudgetCeiling)
                        .filter(Objects::nonNull)
                        .reduce(BigDecimal::min));
    }

    // ---- Snapshot (sent to a joining client) ------------------------------

    public Mono<Map<String, Object>> buildSnapshot(Long tripId) {
        return trips.findById(tripId).flatMap(trip -> Mono.zip(
                        board.getBoard(tripId).map(ItemView::from).collectList(),
                        budgetService.summary(tripId),
                        chatService.history(tripId).map(this::chatPayload).collectList())
                .map(t -> Map.of(
                        "tripId", PublicId.encode(PublicId.TRIP, tripId),
                        "status", trip.getStatus(),
                        "items", t.getT1(),
                        "budget", t.getT2(),
                        "chatHistory", t.getT3(),
                        "presenceCount", hub.presenceCount(tripId))));
    }

    // ---- helpers ----------------------------------------------------------

    private Map<String, Object> chatPayload(ChatMessage m) {
        return Map.of(
                "messageId", PublicId.encode(PublicId.MESSAGE, m.getId()),
                "userId", m.getUserId() == null ? "" : PublicId.encode(PublicId.USER, m.getUserId()),
                "body", m.getBody(),
                "msgType", m.getMsgType(),
                "tsSeconds", m.getTs() / 1000);
    }

    private Mono<Void> logEvent(Long tripId, Long userId, String type, String payload) {
        return events.save(new SessionEvent(tripId, userId, null, type, payload)).then();
    }

    /** Appends a presence event (joined/left) to the log so analytics can reconstruct peak concurrency. */
    public Mono<Void> logPresence(Long tripId, Long userId, String action) {
        return logEvent(tripId, userId, action, "{}");
    }
}
