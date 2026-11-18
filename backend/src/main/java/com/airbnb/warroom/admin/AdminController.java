package com.airbnb.warroom.admin;

import com.airbnb.warroom.recommendation.RecommendationDtos.RetrainResult;
import com.airbnb.warroom.recommendation.RecommendationService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * Admin-only operations (PRD 10.1). The {@code /api/v1/admin/**} prefix is gated to ROLE_ADMIN in
 * SecurityConfig, so these are rejected server-side for non-admin tokens.
 */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    private final RecommendationService recommendationService;

    public AdminController(RecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    @PostMapping("/recommendations/retrain")
    public Mono<RetrainResult> retrain() {
        return recommendationService.retrain();
    }
}
