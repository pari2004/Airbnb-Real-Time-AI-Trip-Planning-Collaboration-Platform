package com.airbnb.warroom.dashboard;

import com.airbnb.warroom.common.ApiException;
import com.airbnb.warroom.common.PublicId;
import com.airbnb.warroom.dashboard.DashboardDtos.CategoryCount;
import com.airbnb.warroom.dashboard.DashboardDtos.DashboardView;
import com.airbnb.warroom.dashboard.DashboardDtos.ProfileView;
import com.airbnb.warroom.dashboard.DashboardDtos.TripSummary;
import com.airbnb.warroom.recommendation.Interaction;
import com.airbnb.warroom.recommendation.InteractionRepository;
import com.airbnb.warroom.trip.Trip;
import com.airbnb.warroom.trip.TripParticipantRepository;
import com.airbnb.warroom.trip.TripRepository;
import com.airbnb.warroom.user.User;
import com.airbnb.warroom.user.UserDto;
import com.airbnb.warroom.user.UserRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Builds the personal dashboard: profile from history, active rooms, and past trips (PRD Epic 3). */
@Service
public class DashboardService {

    private static final Set<String> ACTIVE_STATUSES = Set.of("drafting", "live");

    private final UserRepository users;
    private final InteractionRepository interactions;
    private final TripParticipantRepository participants;
    private final TripRepository trips;

    public DashboardService(UserRepository users, InteractionRepository interactions,
                            TripParticipantRepository participants, TripRepository trips) {
        this.users = users;
        this.interactions = interactions;
        this.participants = participants;
        this.trips = trips;
    }

    public Mono<DashboardView> forUser(Long userId) {
        Mono<User> userMono = users.findById(userId)
                .switchIfEmpty(Mono.error(ApiException.notFound("User")));
        Mono<ProfileView> profileMono = Mono.zip(userMono, interactions.findByUserId(userId).collectList())
                .map(t -> buildProfile(t.getT1(), t.getT2()));
        Mono<List<TripSummary>> tripsMono = buildTripSummaries(userId);

        return Mono.zip(userMono, profileMono, tripsMono).map(t -> {
            List<TripSummary> all = t.getT3();
            List<TripSummary> active = all.stream()
                    .filter(s -> ACTIVE_STATUSES.contains(s.status())).toList();
            List<TripSummary> past = all.stream()
                    .filter(s -> !ACTIVE_STATUSES.contains(s.status())).toList();
            return new DashboardView(UserDto.from(t.getT1()), t.getT2(), active, past);
        });
    }

    private ProfileView buildProfile(User user, List<Interaction> history) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (Interaction in : history) {
            if (in.isLiked()) {
                counts.merge(in.getCategory().toLowerCase(), 1L, Long::sum);
            }
        }
        List<CategoryCount> favored = counts.entrySet().stream()
                .map(e -> new CategoryCount(e.getKey(), e.getValue()))
                .sorted(Comparator.comparingLong(CategoryCount::count).reversed())
                .toList();
        List<String> dietary = user.getDietaryTags() == null ? List.of() : List.of(user.getDietaryTags());
        return new ProfileView(user.getBudgetCeiling(), dietary, favored);
    }

    private Mono<List<TripSummary>> buildTripSummaries(Long userId) {
        return participants.findTripIdsForUser(userId).collectList()
                .flatMap(tripIds -> {
                    if (tripIds.isEmpty()) {
                        return Mono.just(List.of());
                    }
                    return Flux.fromIterable(tripIds)
                            .concatMap(tripId -> Mono.zip(
                                    trips.findById(tripId),
                                    participants.find(tripId, userId)))
                            .map(t -> {
                                Trip trip = t.getT1();
                                return new TripSummary(
                                        PublicId.encode(PublicId.TRIP, trip.getId()),
                                        trip.getDestination(), trip.getStatus(), t.getT2().roomRole(),
                                        trip.getStartDate(), trip.getEndDate());
                            })
                            .collect(ArrayList::new, List::add);
                });
    }
}
