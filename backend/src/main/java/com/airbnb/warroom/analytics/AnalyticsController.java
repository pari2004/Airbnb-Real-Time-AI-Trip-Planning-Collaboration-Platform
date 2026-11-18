package com.airbnb.warroom.analytics;

import com.airbnb.warroom.analytics.AnalyticsDtos.SessionAnalytics;
import com.airbnb.warroom.auth.SecurityUtil;
import com.airbnb.warroom.common.PublicId;
import com.airbnb.warroom.trip.TripAccessService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/trips/{id}/analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;
    private final TripAccessService access;

    public AnalyticsController(AnalyticsService analyticsService, TripAccessService access) {
        this.analyticsService = analyticsService;
        this.access = access;
    }

    @GetMapping
    public Mono<SessionAnalytics> analytics(@PathVariable String id) {
        Long tripId = PublicId.decode(id);
        return SecurityUtil.currentUserId()
                .flatMap(uid -> access.requireMember(tripId, uid))
                .then(analyticsService.forTrip(tripId));
    }
}
