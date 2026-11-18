package com.airbnb.warroom.budget;

import com.airbnb.warroom.board.BoardService;
import com.airbnb.warroom.board.ItineraryItem;
import com.airbnb.warroom.budget.BudgetDtos.BudgetSummary;
import com.airbnb.warroom.budget.BudgetDtos.LineItem;
import com.airbnb.warroom.budget.BudgetDtos.PerPersonShare;
import com.airbnb.warroom.common.ApiException;
import com.airbnb.warroom.common.PublicId;
import com.airbnb.warroom.trip.TripParticipantRepository;
import com.airbnb.warroom.trip.TripRepository;
import com.airbnb.warroom.venue.Venue;
import com.airbnb.warroom.venue.VenueService;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Builds the live budget summary for a Trip Room (PRD Epic 1 budget panel). */
@Service
public class BudgetService {

    private final TripRepository trips;
    private final TripParticipantRepository participants;
    private final BoardService boardService;
    private final VenueService venueService;
    private final BudgetEngine engine;

    public BudgetService(TripRepository trips, TripParticipantRepository participants,
                         BoardService boardService, VenueService venueService, BudgetEngine engine) {
        this.trips = trips;
        this.participants = participants;
        this.boardService = boardService;
        this.venueService = venueService;
        this.engine = engine;
    }

    public Mono<BudgetSummary> summary(Long tripId) {
        Mono<com.airbnb.warroom.trip.Trip> tripMono = trips.findById(tripId)
                .switchIfEmpty(Mono.error(ApiException.notFound("Trip")));
        return tripMono.flatMap(trip -> {
            Mono<List<ItineraryItem>> itemsMono = boardService.getBoard(tripId).collectList();
            Mono<Map<Long, Venue>> venuesMono = venueService.loadVenues(trip.getDestination())
                    .map(vs -> vs.stream().collect(Collectors.toMap(Venue::id, v -> v)));
            return Mono.zip(itemsMono, venuesMono,
                            participants.findViewsByTrip(tripId).collectList())
                    .map(t -> build(trip, t.getT1(), t.getT2(), t.getT3()));
        });
    }

    private BudgetSummary build(com.airbnb.warroom.trip.Trip trip, List<ItineraryItem> items,
                                Map<Long, Venue> venueById,
                                List<com.airbnb.warroom.trip.TripDtos.ParticipantView> parts) {
        // Per-person item prices in board order -> running totals.
        List<BigDecimal> prices = new ArrayList<>();
        List<String> itemIds = new ArrayList<>();
        List<String> venueNames = new ArrayList<>();
        for (ItineraryItem it : items) {
            Venue v = venueById.get(it.getVenueId());
            if (v == null) {
                continue;
            }
            prices.add(v.pricePerPerson());
            itemIds.add(PublicId.encode(PublicId.ITEM, it.getId()));
            venueNames.add(v.name());
        }
        List<BigDecimal> running = engine.runningPerPersonTotals(prices);

        List<LineItem> lineItems = new ArrayList<>();
        for (int i = 0; i < prices.size(); i++) {
            lineItems.add(new LineItem(itemIds.get(i), venueNames.get(i), prices.get(i), running.get(i)));
        }

        BigDecimal perPersonPlanned = running.isEmpty()
                ? BigDecimal.ZERO.setScale(2) : running.get(running.size() - 1);
        int participantCount = Math.max(parts.size(), 1);
        BigDecimal groupPlanned = perPersonPlanned.multiply(BigDecimal.valueOf(participantCount))
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal remaining = trip.getTotalBudget().subtract(groupPlanned).setScale(2, RoundingMode.HALF_UP);
        boolean overBudget = remaining.signum() < 0;

        // Even split of the group-planned cost across participants (exact, cent-accurate).
        List<BigDecimal> shares = parts.isEmpty()
                ? List.of() : engine.evenSplit(groupPlanned, parts.size());
        List<PerPersonShare> evenSplit = new ArrayList<>();
        for (int i = 0; i < parts.size(); i++) {
            evenSplit.add(new PerPersonShare(parts.get(i).userId(), parts.get(i).displayName(), shares.get(i)));
        }

        return new BudgetSummary(trip.getTotalBudget(), parts.size(), perPersonPlanned, groupPlanned,
                remaining, overBudget, lineItems, evenSplit);
    }
}
