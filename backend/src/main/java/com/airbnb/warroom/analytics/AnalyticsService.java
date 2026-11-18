package com.airbnb.warroom.analytics;

import com.airbnb.warroom.analytics.AnalyticsDtos.SessionAnalytics;
import com.airbnb.warroom.budget.BudgetService;
import com.airbnb.warroom.common.PublicId;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Session analytics reconciled directly against the append-only event log (PRD Epic 3): numbers are
 * derived from {@code session_events}, so they match the underlying log exactly rather than being
 * tracked in a parallel counter that could drift.
 */
@Service
public class AnalyticsService {

    private final SessionEventRepository events;
    private final BudgetService budgetService;

    public AnalyticsService(SessionEventRepository events, BudgetService budgetService) {
        this.events = events;
        this.budgetService = budgetService;
    }

    public Mono<SessionAnalytics> forTrip(Long tripId) {
        return events.findByTripIdOrderByCreatedAtAsc(tripId).collectList()
                .flatMap(log -> budgetService.summary(tripId).map(budget -> compute(tripId, log, budget)));
    }

    private SessionAnalytics compute(Long tripId, List<SessionEvent> log,
                                     com.airbnb.warroom.budget.BudgetDtos.BudgetSummary budget) {
        long aiQuestions = count(log, "ai_ask");
        long conflicts = count(log, "conflict");
        long votesResolved = count(log, "vote_resolved");
        long pinsAndMoves = log.stream()
                .filter(e -> e.getEventType().equals("pin") || e.getEventType().equals("move"))
                .count();

        long durationMinutes = 0;
        if (!log.isEmpty()) {
            Instant first = log.get(0).getCreatedAt();
            Instant last = log.get(log.size() - 1).getCreatedAt();
            if (first != null && last != null) {
                durationMinutes = Duration.between(first, last).toMinutes();
            }
        }

        int peak = peakConcurrent(log);

        return new SessionAnalytics(
                PublicId.encode(PublicId.TRIP, tripId),
                durationMinutes, peak, aiQuestions, conflicts, votesResolved,
                pinsAndMoves, log.size(), budget);
    }

    /** Replays joined/left events in time order, tracking the running max concurrency. */
    private int peakConcurrent(List<SessionEvent> log) {
        int current = 0;
        int peak = 0;
        for (SessionEvent e : log) {
            if ("joined".equals(e.getEventType())) {
                current++;
                peak = Math.max(peak, current);
            } else if ("left".equals(e.getEventType())) {
                current = Math.max(0, current - 1);
            }
        }
        return peak;
    }

    private long count(List<SessionEvent> log, String type) {
        return log.stream().filter(e -> type.equals(e.getEventType())).count();
    }
}
