package com.airbnb.warroom.concierge.llm;

import com.airbnb.warroom.concierge.SuggestionCandidate;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Generates the natural-language prose of a Concierge answer from a pre-filtered, pre-ranked set of
 * real candidates. Implementations never introduce venues/prices/hours outside {@code candidates} —
 * that is what keeps the answer grounded (PRD 6.2 acceptance criteria).
 */
public interface LlmClient {

    /**
     * @param question   the traveller's natural-language question
     * @param top        the top-ranked valid candidate (the one being recommended)
     * @param candidates the full grounding set (already constraint-valid), ordered by relevance
     * @return the answer text (grounded in {@code candidates} only)
     */
    Mono<String> generate(String question, SuggestionCandidate top, List<SuggestionCandidate> candidates);
}
