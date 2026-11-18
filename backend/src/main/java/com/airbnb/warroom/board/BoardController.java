package com.airbnb.warroom.board;

import com.airbnb.warroom.auth.SecurityUtil;
import com.airbnb.warroom.board.BoardDtos.ItemView;
import com.airbnb.warroom.board.BoardDtos.MoveOutcome;
import com.airbnb.warroom.board.BoardDtos.MoveRequest;
import com.airbnb.warroom.board.BoardDtos.PinRequest;
import com.airbnb.warroom.common.PublicId;
import com.airbnb.warroom.realtime.BoardCommandService;
import com.airbnb.warroom.trip.TripAccessService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * REST mirror of the board WebSocket commands — same {@link BoardCommandService} logic, so Postman,
 * integration tests, and the client can drive the board over HTTP too. Mutations still fan out to
 * all connected WebSocket clients via the room hub.
 */
@RestController
@RequestMapping("/api/v1/trips/{id}/board")
public class BoardController {

    private final BoardCommandService commands;
    private final TripAccessService access;

    public BoardController(BoardCommandService commands, TripAccessService access) {
        this.commands = commands;
        this.access = access;
    }

    @GetMapping
    public Mono<Map<String, Object>> snapshot(@PathVariable String id) {
        Long tripId = PublicId.decode(id);
        return SecurityUtil.currentUserId()
                .flatMap(uid -> access.requireMember(tripId, uid))
                .then(commands.buildSnapshot(tripId));
    }

    @PostMapping("/items")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<ItemView> pin(@PathVariable String id, @Valid @RequestBody PinRequest req) {
        Long tripId = PublicId.decode(id);
        return SecurityUtil.currentUserId().flatMap(uid -> commands.pin(tripId, uid, req));
    }

    /** Returns 200 with {applied:true,...} or 409 with the authoritative current state on conflict. */
    @PostMapping("/move")
    public Mono<ResponseEntity<MoveOutcome>> move(@PathVariable String id, @Valid @RequestBody MoveRequest req) {
        Long tripId = PublicId.decode(id);
        return SecurityUtil.currentUserId()
                .flatMap(uid -> commands.move(tripId, uid, req))
                .map(outcome -> outcome.applied()
                        ? ResponseEntity.ok(outcome)
                        : ResponseEntity.status(HttpStatus.CONFLICT).body(outcome));
    }

    @PostMapping("/items/{itemId}/lock")
    public Mono<ItemView> lock(@PathVariable String id, @PathVariable String itemId) {
        Long tripId = PublicId.decode(id);
        return SecurityUtil.currentUserId().flatMap(uid -> commands.lock(tripId, uid, itemId));
    }

    @PostMapping("/items/{itemId}/unlock")
    public Mono<ItemView> unlock(@PathVariable String id, @PathVariable String itemId) {
        Long tripId = PublicId.decode(id);
        return SecurityUtil.currentUserId().flatMap(uid -> commands.unlock(tripId, uid, itemId));
    }

    @DeleteMapping("/items/{itemId}")
    public Mono<Void> remove(@PathVariable String id, @PathVariable String itemId) {
        Long tripId = PublicId.decode(id);
        return SecurityUtil.currentUserId().flatMap(uid -> commands.remove(tripId, uid, itemId));
    }
}
