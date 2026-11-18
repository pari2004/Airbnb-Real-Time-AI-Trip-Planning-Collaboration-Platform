package com.airbnb.warroom.concierge.llm;

import com.airbnb.warroom.concierge.SuggestionCandidate;
import com.airbnb.warroom.venue.Venue;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Deterministic, key-free answer generator. Builds a grounded sentence purely from the top
 * candidate's real metadata — guaranteeing the answer never fabricates anything and the system runs
 * with no external LLM dependency (the default). Swapped for {@link WebClientLlmClient} when an
 * API key/provider is configured.
 */
public class TemplateLlmClient implements LlmClient {

    @Override
    public Mono<String> generate(String question, SuggestionCandidate top, List<SuggestionCandidate> candidates) {
        Venue v = top.venue();
        StringBuilder sb = new StringBuilder();
        sb.append(v.name());

        if (top.distanceKm() != null) {
            int walkMinutes = (int) Math.round(top.distanceKm() / 5.0 * 60.0); // ~5 km/h walking
            sb.append(" is about a ").append(Math.max(1, walkMinutes)).append("-minute walk from your anchor");
        } else {
            sb.append(" is a solid ").append(v.category()).append(" option");
        }

        if (!v.dietaryTags().isEmpty()) {
            sb.append(", offers ").append(String.join("/", v.dietaryTags())).append(" options");
        }
        sb.append(", is open ").append(v.hoursOpen()).append("–").append(v.hoursClose());
        sb.append(", and runs about $").append(v.pricePerPerson().stripTrailingZeros().toPlainString())
                .append(" per person.");

        if (candidates.size() > 1) {
            SuggestionCandidate alt = candidates.get(1);
            sb.append(" Alternatively, ").append(alt.venue().name())
                    .append(" ($").append(alt.venue().pricePerPerson().stripTrailingZeros().toPlainString())
                    .append("/person) also fits.");
        }
        return Mono.just(sb.toString());
    }
}
