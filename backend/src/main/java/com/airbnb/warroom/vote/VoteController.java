package com.airbnb.warroom.vote;

import com.airbnb.warroom.auth.SecurityUtil;
import com.airbnb.warroom.common.PublicId;
import com.airbnb.warroom.realtime.BoardCommandService;
import com.airbnb.warroom.vote.VoteDtos.CastVoteRequest;
import com.airbnb.warroom.vote.VoteDtos.OpenVoteRequest;
import com.airbnb.warroom.vote.VoteDtos.ResolveVoteRequest;
import com.airbnb.warroom.vote.VoteDtos.VoteResult;
import com.airbnb.warroom.vote.VoteDtos.VoteView;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

/** REST mirror of the voting WebSocket commands (PRD 6.2). */
@RestController
@RequestMapping("/api/v1/trips/{id}/votes")
public class VoteController {

    private final BoardCommandService commands;

    public VoteController(BoardCommandService commands) {
        this.commands = commands;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<VoteView> open(@PathVariable String id, @Valid @RequestBody OpenVoteRequest req) {
        Long tripId = PublicId.decode(id);
        return SecurityUtil.currentUserId().flatMap(uid ->
                commands.openVote(tripId, uid, req.itemId(), req.candidateVenueIds(), req.durationSeconds()));
    }

    @PostMapping("/cast")
    public Mono<Void> cast(@PathVariable String id, @Valid @RequestBody CastVoteRequest req) {
        Long tripId = PublicId.decode(id);
        return SecurityUtil.currentUserId().flatMap(uid ->
                commands.castVote(tripId, uid, req.itemId(), req.choiceVenueId()));
    }

    @PostMapping("/resolve")
    public Mono<VoteResult> resolve(@PathVariable String id, @Valid @RequestBody ResolveVoteRequest req) {
        Long tripId = PublicId.decode(id);
        return SecurityUtil.currentUserId().flatMap(uid ->
                commands.resolveVoteByItem(tripId, uid, req.itemId(), true));
    }
}
