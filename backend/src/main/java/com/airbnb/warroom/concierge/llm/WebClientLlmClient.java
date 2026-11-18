package com.airbnb.warroom.concierge.llm;

import com.airbnb.warroom.concierge.SuggestionCandidate;
import com.airbnb.warroom.config.AppProperties;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Real hosted-LLM adapter (OpenAI- or Anthropic-compatible). The model is constrained by a strict
 * system prompt to use ONLY the supplied candidate venues; a post-check and an error fallback both
 * defer to {@link TemplateLlmClient}, so a bad/empty/hallucinated response can never surface
 * ungrounded output (PRD 6.2).
 */
public class WebClientLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(WebClientLlmClient.class);

    private final WebClient http;
    private final AppProperties.Concierge.Llm cfg;
    private final TemplateLlmClient fallback = new TemplateLlmClient();

    public WebClientLlmClient(WebClient.Builder builder, AppProperties props) {
        this.cfg = props.concierge().llm();
        this.http = builder.baseUrl(cfg.baseUrl()).build();
    }

    @Override
    public Mono<String> generate(String question, SuggestionCandidate top, List<SuggestionCandidate> candidates) {
        String context = candidates.stream().map(this::describe).collect(Collectors.joining("\n"));
        String system = "You are a trip-planning concierge. Recommend ONLY from the venues listed below. "
                + "Never invent a venue, price, or opening hour. Keep it to 2 short sentences. "
                + "Recommend \"" + top.venue().name() + "\" as the primary choice.\n\nVENUES:\n" + context;

        return callProvider(system, question)
                .map(String::trim)
                .filter(ans -> !ans.isBlank() && mentionsRealVenue(ans, candidates))
                .switchIfEmpty(fallback.generate(question, top, candidates))
                .timeout(Duration.ofSeconds(8))
                .onErrorResume(e -> {
                    log.warn("LLM call failed ({}), falling back to template answer", e.toString());
                    return fallback.generate(question, top, candidates);
                });
    }

    private Mono<String> callProvider(String system, String question) {
        if ("anthropic".equalsIgnoreCase(cfg.provider())) {
            Map<String, Object> body = Map.of(
                    "model", cfg.model(),
                    "max_tokens", 300,
                    "system", system,
                    "messages", List.of(Map.of("role", "user", "content", question)));
            return http.post().uri("/messages")
                    .header("x-api-key", cfg.apiKey())
                    .header("anthropic-version", "2023-06-01")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve().bodyToMono(JsonNode.class)
                    .map(n -> n.path("content").path(0).path("text").asText(""));
        }
        // OpenAI-compatible chat completions (default)
        Map<String, Object> body = Map.of(
                "model", cfg.model(),
                "temperature", 0.2,
                "messages", List.of(
                        Map.of("role", "system", "content", system),
                        Map.of("role", "user", "content", question)));
        return http.post().uri("/chat/completions")
                .header("Authorization", "Bearer " + cfg.apiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve().bodyToMono(JsonNode.class)
                .map(n -> n.path("choices").path(0).path("message").path("content").asText(""));
    }

    private String describe(SuggestionCandidate c) {
        var v = c.venue();
        return "- " + v.name() + " | " + v.category() + " | $" + v.pricePerPerson() + "/person | open "
                + v.hoursOpen() + "-" + v.hoursClose()
                + (v.dietaryTags().isEmpty() ? "" : " | " + String.join(",", v.dietaryTags()))
                + (c.distanceKm() != null ? String.format(" | %.2f km away", c.distanceKm()) : "");
    }

    /** Grounding guard: the answer must mention at least one real candidate by name. */
    private boolean mentionsRealVenue(String answer, List<SuggestionCandidate> candidates) {
        String lower = answer.toLowerCase();
        return candidates.stream().anyMatch(c -> lower.contains(c.venue().name().toLowerCase()));
    }
}
