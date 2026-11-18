package com.airbnb.warroom.budget;

import com.airbnb.warroom.auth.SecurityUtil;
import com.airbnb.warroom.budget.BudgetDtos.BudgetSummary;
import com.airbnb.warroom.common.PublicId;
import com.airbnb.warroom.trip.TripAccessService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/trips/{id}/budget")
public class BudgetController {

    private final BudgetService budgetService;
    private final TripAccessService access;

    public BudgetController(BudgetService budgetService, TripAccessService access) {
        this.budgetService = budgetService;
        this.access = access;
    }

    @GetMapping
    public Mono<BudgetSummary> summary(@PathVariable String id) {
        Long tripId = PublicId.decode(id);
        return SecurityUtil.currentUserId()
                .flatMap(uid -> access.requireMember(tripId, uid))
                .then(budgetService.summary(tripId));
    }
}
