package com.airbnb.warroom.concierge;

import com.airbnb.warroom.board.BoardService;
import com.airbnb.warroom.board.ItineraryItem;
import com.airbnb.warroom.chat.ChatMessage;
import com.airbnb.warroom.chat.ChatMessageRepository;
import com.airbnb.warroom.common.ApiException;
import com.airbnb.warroom.common.PublicId;
import com.airbnb.warroom.concierge.ConciergeDtos.AiAnswer;
import com.airbnb.warroom.concierge.ConciergeDtos.AskRequest;
import com.airbnb.warroom.concierge.ConciergeDtos.CandidateView;
import com.airbnb.warroom.config.AppProperties;
import com.airbnb.warroom.analytics.SessionEvent;
import com.airbnb.warroom.analytics.SessionEventRepository;
import com.airbnb.warroom.concierge.llm.LlmClient;
import com.airbnb.warroom.trip.Trip;
import com.airbnb.warroom.trip.TripRepository;
import com.airbnb.warroom.user.User;
import com.airbnb.warroom.user.UserRepository;
import com.airbnb.warroom.venue.Venue;
import com.airbnb.warroom.venue.VenueService;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Orchestrates a Concierge question end-to-end (PRD 6.2 / 7.3.3): resolve constraints from the
 * question + requester + anchor, hard-filter candidates, rank 40/30/30, retrieve/ground, generate a
 * grounded answer, and persist it. Returns a hedged answer (no suggestion) when nothing is valid.
 */
@Service
public class ConciergeService {

    private static final int MEAL_DURATION_MIN = 120;
    private static final int MAX_CANDIDATES = 5;

    private final TripRepository trips;
    private final UserRepository users;
    private final VenueService venueService;
    private final BoardService boardService;
    private final ConstraintMatcher matcher;
    private final Ranker ranker;
    private final RagRetriever rag;
    private final QuestionParser parser;
    private final LlmClient llm;
    private final ChatMessageRepository chat;
    private final SessionEventRepository events;
    private final AppProperties props;

    public ConciergeService(TripRepository trips, UserRepository users, VenueService venueService,
                            BoardService boardService, ConstraintMatcher matcher, Ranker ranker,
                            RagRetriever rag, QuestionParser parser, LlmClient llm,
                            ChatMessageRepository chat, SessionEventRepository events, AppProperties props) {
        this.trips = trips;
        this.users = users;
        this.venueService = venueService;
        this.boardService = boardService;
        this.matcher = matcher;
        this.ranker = ranker;
        this.rag = rag;
        this.parser = parser;
        this.llm = llm;
        this.chat = chat;
        this.events = events;
        this.props = props;
    }

    private record AnchorInfo(ItineraryItem item, Venue venue) {
    }

    public Mono<AiAnswer> ask(Long tripId, Long userId, AskRequest req) {
        return trips.findById(tripId)
                .switchIfEmpty(Mono.error(ApiException.notFound("Trip")))
                .flatMap(trip -> users.findById(userId)
                        .flatMap(user -> resolveAnchor(req)
                                .flatMap(anchor -> venueService.loadVenues(trip.getDestination())
                                        .flatMap(venues -> process(trip, user, req, anchor.orElse(null), venues)))));
    }

    private Mono<java.util.Optional<AnchorInfo>> resolveAnchor(AskRequest req) {
        if (req.anchorItemId() == null || req.anchorItemId().isBlank()) {
            return Mono.just(java.util.Optional.empty());
        }
        Long itemId = PublicId.decode(req.anchorItemId());
        return boardService.getItem(itemId)
                .flatMap(item -> venueService.loadVenue(item.getVenueId())
                        .map(v -> java.util.Optional.of(new AnchorInfo(item, v))));
    }

    private Mono<AiAnswer> process(Trip trip, User user, AskRequest req, AnchorInfo anchor, List<Venue> venues) {
        ConstraintContext ctx = buildContext(user, req.question(), anchor);
        List<String> constraintsApplied = describeConstraints(ctx);

        // Exclude the anchor's own venue from the candidate pool.
        List<Venue> pool = new ArrayList<>(venues);
        if (anchor != null) {
            pool.removeIf(v -> v.id().equals(anchor.venue().id()));
        }

        List<Venue> valid = matcher.filter(pool, ctx);
        if (valid.isEmpty()) {
            return hedge(trip, user, req, constraintsApplied);
        }

        RagRetriever.Scorer scorer = rag.buildScorer(valid, req.question());
        List<SuggestionCandidate> ranked = ranker.rank(valid, ctx, matcher, scorer);
        List<SuggestionCandidate> grounding = ranked.subList(0, Math.min(MAX_CANDIDATES, ranked.size()));
        SuggestionCandidate top = ranked.get(0);

        return llm.generate(req.question(), top, grounding).flatMap(text -> {
            List<String> sourceEvents = grounding.stream()
                    .map(c -> "venue_" + c.venue().id()).toList();
            List<CandidateView> candidateViews = grounding.stream().map(CandidateView::from).toList();
            AiAnswer answer = new AiAnswer(
                    text,
                    PublicId.encode(PublicId.VENUE, top.venue().id()),
                    sourceEvents,
                    Instant.now(),
                    candidateViews,
                    constraintsApplied,
                    true);
            return persist(trip.getId(), user.getId(), req.question(), answer).thenReturn(answer);
        });
    }

    private Mono<AiAnswer> hedge(Trip trip, User user, AskRequest req, List<String> constraintsApplied) {
        String msg = "No venue in the " + trip.getDestination()
                + " dataset satisfies all of your active constraints ("
                + String.join("; ", constraintsApplied)
                + "). Try relaxing the budget, dietary filters, or the time window.";
        AiAnswer answer = new AiAnswer(msg, null, List.of(), Instant.now(), List.of(),
                constraintsApplied, true);
        return persist(trip.getId(), user.getId(), req.question(), answer).thenReturn(answer);
    }

    private ConstraintContext buildContext(User user, String question, AnchorInfo anchor) {
        BigDecimal parsedBudget = parser.parseBudget(question);
        BigDecimal budget = parsedBudget != null ? parsedBudget : user.getBudgetCeiling();

        Set<String> dietary = new LinkedHashSet<>(parser.parseDietary(question));
        if (user.getDietaryTags() != null) {
            for (String t : user.getDietaryTags()) {
                dietary.add(t.toLowerCase());
            }
        }

        LocalTime reqStart = null;
        LocalTime reqEnd = null;
        Double lat = null;
        Double lng = null;
        Double radius = null;
        if (anchor != null) {
            reqStart = anchor.item().getEndTime();
            LocalTime end = reqStart.plusMinutes(MEAL_DURATION_MIN);
            reqEnd = end.isBefore(reqStart) ? LocalTime.of(23, 59) : end;
            lat = anchor.venue().lat();
            lng = anchor.venue().lng();
            radius = props.concierge().proximityRadiusKm();
        }

        return new ConstraintContext(budget, dietary, reqStart, reqEnd, lat, lng, radius,
                props.concierge().hoursBufferMinutes(), parser.parseCategory(question));
    }

    private List<String> describeConstraints(ConstraintContext ctx) {
        List<String> out = new ArrayList<>();
        if (ctx.hasBudget()) {
            out.add("budget <= $" + ctx.budgetCeiling().stripTrailingZeros().toPlainString() + "/person");
        }
        if (ctx.hasDietary()) {
            out.add("dietary: " + ctx.dietaryTags());
        }
        if (ctx.hasTimeWindow()) {
            out.add("open for " + ctx.requestedStart() + "-" + ctx.requestedEnd()
                    + " (+" + ctx.bufferMinutes() + "m buffer)");
        }
        if (ctx.hasAnchor()) {
            out.add("within " + ctx.radiusKm() + "km of anchor");
        }
        if (ctx.categoryHint() != null) {
            out.add("category: " + ctx.categoryHint());
        }
        return out;
    }

    private Mono<Void> persist(Long tripId, Long userId, String question, AiAnswer answer) {
        ChatMessage m = new ChatMessage();
        m.setTripId(tripId);
        m.setUserId(null); // AI author
        m.setMsgType("ai_answer");
        m.setBody(answer.answer());
        m.setTs(System.currentTimeMillis());
        String payload = "{\"question\":\"" + escape(question) + "\",\"suggestedVenueId\":"
                + (answer.suggestedVenueId() == null ? "null" : "\"" + answer.suggestedVenueId() + "\"") + "}";
        SessionEvent ev = new SessionEvent(tripId, userId, null, "ai_ask", payload);
        return chat.save(m).then(events.save(ev)).then();
    }

    private String escape(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
