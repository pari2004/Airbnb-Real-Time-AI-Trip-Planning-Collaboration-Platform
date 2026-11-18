package com.airbnb.warroom.vote;

import com.airbnb.warroom.board.BoardService;
import com.airbnb.warroom.common.ApiException;
import com.airbnb.warroom.common.PublicId;
import com.airbnb.warroom.vote.VoteDtos.VoteResult;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Contested-slot voting (PRD 6.2). Opening a vote moves the item into the {@code voting} stage;
 * casting is one vote per user per session (idempotent); resolution tallies and applies the winner
 * to the board deterministically (ties broken by lowest venue id).
 */
@Service
public class VoteService {

    private static final int DEFAULT_DURATION_SECONDS = 60;

    private final VoteSessionRepository sessions;
    private final ItemVoteRepository votes;
    private final BoardService boardService;

    public VoteService(VoteSessionRepository sessions, ItemVoteRepository votes, BoardService boardService) {
        this.sessions = sessions;
        this.votes = votes;
        this.boardService = boardService;
    }

    public Mono<VoteSession> openVote(Long tripId, Long openedByUserId, String itemPublicId,
                                      List<String> candidatePublicIds, Integer durationSeconds) {
        Long itemId = PublicId.decode(itemPublicId);
        Long[] candidates = candidatePublicIds.stream().map(PublicId::decode).toArray(Long[]::new);
        int duration = durationSeconds == null ? DEFAULT_DURATION_SECONDS : durationSeconds;
        return sessions.findByItemIdAndStatus(itemId, "open")
                .flatMap(existing -> Mono.<VoteSession>error(
                        ApiException.conflict("A vote is already open for this item")))
                .switchIfEmpty(Mono.defer(() -> boardService.getItem(itemId).flatMap(item -> {
                    if (!item.getTripId().equals(tripId)) {
                        return Mono.error(ApiException.badRequest("Item does not belong to this trip"));
                    }
                    VoteSession s = new VoteSession();
                    s.setTripId(tripId);
                    s.setItemId(itemId);
                    s.setCandidateVenueIds(candidates);
                    s.setOpenedByUserId(openedByUserId);
                    s.setOpenedAt(Instant.now());
                    s.setClosesAt(Instant.now().plusSeconds(duration));
                    s.setStatus("open");
                    return boardService.setStage(itemId, "voting").then(sessions.save(s));
                })));
    }

    public Mono<VoteSession> castVote(Long tripId, Long userId, String itemPublicId, String choicePublicId) {
        Long itemId = PublicId.decode(itemPublicId);
        Long choiceVenueId = PublicId.decode(choicePublicId);
        return sessions.findByItemIdAndStatus(itemId, "open")
                .switchIfEmpty(Mono.error(ApiException.notFound("Open vote for this item")))
                .flatMap(s -> {
                    boolean valid = java.util.Arrays.asList(s.getCandidateVenueIds()).contains(choiceVenueId);
                    if (!valid) {
                        return Mono.error(ApiException.badRequest("Choice is not a candidate in this vote"));
                    }
                    return votes.castVote(s.getId(), userId, choiceVenueId).thenReturn(s);
                });
    }

    /** Tally an open session, apply the winner to the board, and mark it resolved. */
    public Mono<VoteResult> resolve(Long voteSessionId) {
        return sessions.findById(voteSessionId)
                .switchIfEmpty(Mono.error(ApiException.notFound("Vote session")))
                .flatMap(s -> {
                    if (!"open".equals(s.getStatus())) {
                        return Mono.error(ApiException.conflict("Vote already resolved"));
                    }
                    return votes.tally(s.getId()).collectList().flatMap(rows -> {
                        Map<String, Long> tally = new LinkedHashMap<>();
                        Long winner = pickWinner(rows, s.getCandidateVenueIds());
                        for (Map<String, Object> row : rows) {
                            Long vid = (Long) row.get("venueId");
                            Long count = (Long) row.get("count");
                            tally.put(PublicId.encode(PublicId.VENUE, vid), count);
                        }
                        s.setStatus("resolved");
                        s.setWinningVenueId(winner);
                        return sessions.save(s)
                                .then(boardService.applyVoteWinner(s.getItemId(), winner))
                                .thenReturn(new VoteResult(
                                        PublicId.encode(PublicId.ITEM, s.getItemId()),
                                        PublicId.encode(PublicId.VENUE, winner),
                                        tally));
                    });
                });
    }

    /** Resolve-by-item convenience (used by WS handler / leader action). */
    public Mono<VoteResult> resolveByItem(Long itemId) {
        return sessions.findByItemIdAndStatus(itemId, "open")
                .switchIfEmpty(Mono.error(ApiException.notFound("Open vote for this item")))
                .flatMap(s -> resolve(s.getId()));
    }

    private Long pickWinner(List<Map<String, Object>> rows, Long[] candidates) {
        long maxCount = -1;
        Long winner = null;
        for (Map<String, Object> row : rows) {
            Long vid = (Long) row.get("venueId");
            long count = (Long) row.get("count");
            // deterministic tie-break: highest count, then lowest venue id
            if (count > maxCount || (count == maxCount && (winner == null || vid < winner))) {
                maxCount = count;
                winner = vid;
            }
        }
        // No votes cast -> default to the first candidate so the slot still resolves.
        if (winner == null && candidates != null && candidates.length > 0) {
            winner = candidates[0];
        }
        return winner;
    }
}
