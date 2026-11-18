package com.airbnb.warroom.vote;

import com.airbnb.warroom.common.PublicId;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Contested-slot voting payloads (PRD 6.2, 7.3.4). */
public final class VoteDtos {

    private VoteDtos() {
    }

    public record OpenVoteRequest(@NotBlank String itemId,
                                  @NotEmpty List<String> candidateVenueIds,
                                  Integer durationSeconds) {
    }

    public record CastVoteRequest(@NotBlank String itemId, @NotBlank String choiceVenueId) {
    }

    public record ResolveVoteRequest(@NotBlank String itemId) {
    }

    public record VoteView(String voteSessionId, String itemId, List<String> candidateVenueIds,
                           Instant closesAt, String status, String winningVenueId) {

        public static VoteView from(VoteSession s) {
            List<String> candidates = s.getCandidateVenueIds() == null ? List.of()
                    : java.util.Arrays.stream(s.getCandidateVenueIds())
                    .map(id -> PublicId.encode(PublicId.VENUE, id)).toList();
            return new VoteView(
                    PublicId.encode("vote_", s.getId()),
                    PublicId.encode(PublicId.ITEM, s.getItemId()),
                    candidates,
                    s.getClosesAt(),
                    s.getStatus(),
                    s.getWinningVenueId() == null ? null
                            : PublicId.encode(PublicId.VENUE, s.getWinningVenueId()));
        }
    }

    public record VoteResult(String itemId, String winningVenueId, Map<String, Long> tally) {
    }
}
