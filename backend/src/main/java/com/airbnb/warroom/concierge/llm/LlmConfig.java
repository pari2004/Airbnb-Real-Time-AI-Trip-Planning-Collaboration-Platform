package com.airbnb.warroom.concierge.llm;

import com.airbnb.warroom.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/** Selects the {@link LlmClient} implementation from {@code app.concierge.llm.provider}. */
@Configuration
public class LlmConfig {

    private static final Logger log = LoggerFactory.getLogger(LlmConfig.class);

    @Bean
    public LlmClient llmClient(AppProperties props, WebClient.Builder builder) {
        String provider = props.concierge().llm().provider();
        String key = props.concierge().llm().apiKey();
        boolean useRemote = provider != null
                && !provider.isBlank()
                && !provider.equalsIgnoreCase("template")
                && key != null && !key.isBlank();
        if (useRemote) {
            log.info("AI Concierge using remote LLM provider '{}' (model {})",
                    provider, props.concierge().llm().model());
            return new WebClientLlmClient(builder, props);
        }
        log.info("AI Concierge using deterministic template answerer (no external LLM key configured)");
        return new TemplateLlmClient();
    }
}
