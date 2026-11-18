package com.airbnb.warroom.vote;

import com.airbnb.warroom.realtime.BoardCommandService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Closes timed-out votes (PRD 6.2 "timed vote"): every 2s it resolves any open session past its
 * {@code closesAt} and broadcasts the result + applied winner to the room.
 */
@Component
public class VoteExpiryScheduler {

    private static final Logger log = LoggerFactory.getLogger(VoteExpiryScheduler.class);

    private final VoteSessionRepository sessions;
    private final BoardCommandService commands;

    public VoteExpiryScheduler(VoteSessionRepository sessions, BoardCommandService commands) {
        this.sessions = sessions;
        this.commands = commands;
    }

    @Scheduled(fixedDelay = 2000)
    public void sweep() {
        sessions.findByStatusAndClosesAtBefore("open", Instant.now())
                .flatMap(s -> commands.resolveSessionAndBroadcast(s.getTripId(), s.getId(), s.getOpenedByUserId())
                        .onErrorResume(e -> {
                            log.warn("Vote auto-resolve failed for session {}: {}", s.getId(), e.getMessage());
                            return reactor.core.publisher.Mono.empty();
                        }))
                .subscribe();
    }
}
