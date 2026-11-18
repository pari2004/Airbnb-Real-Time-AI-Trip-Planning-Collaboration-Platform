package com.airbnb.warroom.dashboard;

import com.airbnb.warroom.auth.SecurityUtil;
import com.airbnb.warroom.dashboard.DashboardDtos.DashboardView;
import com.airbnb.warroom.recommendation.Interaction;
import com.airbnb.warroom.recommendation.RecommendationDtos.InteractionRequest;
import com.airbnb.warroom.recommendation.RecommendationDtos.RecommendationView;
import com.airbnb.warroom.recommendation.RecommendationDtos.RetrainResult;
import com.airbnb.warroom.recommendation.RecommendationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

/** Personal home base: dashboard, recommendation rail, and interaction logging (PRD Epic 3). */
@RestController
@RequestMapping("/api/v1/me")
public class UserController {

    private final DashboardService dashboardService;
    private final RecommendationService recommendationService;

    public UserController(DashboardService dashboardService, RecommendationService recommendationService) {
        this.dashboardService = dashboardService;
        this.recommendationService = recommendationService;
    }

    @GetMapping("/dashboard")
    public Mono<DashboardView> dashboard() {
        return SecurityUtil.currentUserId().flatMap(dashboardService::forUser);
    }

    @GetMapping("/recommendations")
    public Mono<List<RecommendationView>> recommendations() {
        return SecurityUtil.currentUserId().flatMap(recommendationService::forUser);
    }

    @PostMapping("/interactions")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<Interaction> record(@Valid @RequestBody InteractionRequest req) {
        return SecurityUtil.currentUserId().flatMap(uid -> recommendationService.record(uid, req));
    }

    /** Lets a reviewer force a synchronous batch retrain (the offline job also runs on new interactions). */
    @PostMapping("/recommendations/retrain")
    public Mono<RetrainResult> retrain() {
        return recommendationService.retrain();
    }
}
