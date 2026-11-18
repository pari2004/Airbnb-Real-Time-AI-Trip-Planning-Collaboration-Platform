package com.airbnb.warroom.concierge;

import com.airbnb.warroom.config.AppProperties;
import com.airbnb.warroom.venue.Venue;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/**
 * Ranks surviving candidates by the weighted score mandated in PRD 6.2:
 * 40% proximity to the anchor, 30% rating/popularity, 30% budget headroom.
 */
@Component
public class Ranker {

    private final double wProximity;
    private final double wRating;
    private final double wHeadroom;

    public Ranker(AppProperties props) {
        this.wProximity = props.concierge().rankWeightProximity();
        this.wRating = props.concierge().rankWeightRating();
        this.wHeadroom = props.concierge().rankWeightBudgetHeadroom();
    }

    public List<SuggestionCandidate> rank(List<Venue> valid, ConstraintContext ctx,
                                          ConstraintMatcher matcher, RagRetriever.Scorer lexical) {
        return valid.stream()
                .map(v -> score(v, ctx, matcher, lexical))
                .sorted(Comparator.comparingDouble(SuggestionCandidate::score).reversed())
                .toList();
    }

    private SuggestionCandidate score(Venue v, ConstraintContext ctx, ConstraintMatcher matcher,
                                      RagRetriever.Scorer lexical) {
        Double dist = matcher.distanceKm(v, ctx);

        // Proximity: 1 at the anchor, 0 at the radius edge; neutral 0.5 when no anchor is set.
        double proximityScore;
        if (dist != null && ctx.radiusKm() != null && ctx.radiusKm() > 0) {
            proximityScore = clamp(1.0 - (dist / ctx.radiusKm()));
        } else {
            proximityScore = 0.5;
        }

        double ratingScore = clamp(v.rating() / 5.0);

        // Budget headroom: reward margin under the ceiling; neutral 0.5 when no budget is set.
        double headroomScore;
        if (ctx.hasBudget() && ctx.budgetCeiling().signum() > 0) {
            double ceiling = ctx.budgetCeiling().doubleValue();
            double price = v.pricePerPerson().doubleValue();
            headroomScore = clamp((ceiling - price) / ceiling);
        } else {
            headroomScore = 0.5;
        }

        double structured = wProximity * proximityScore + wRating * ratingScore + wHeadroom * headroomScore;
        double lex = lexical == null ? 0.0 : lexical.similarity(v);
        return new SuggestionCandidate(v, dist, structured, proximityScore, ratingScore, headroomScore, lex);
    }

    private static double clamp(double x) {
        return Math.max(0.0, Math.min(1.0, x));
    }
}
