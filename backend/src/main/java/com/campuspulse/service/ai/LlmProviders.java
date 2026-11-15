package com.campuspulse.service.ai;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Providers in the order to try them ({@code llm.order}, default llama → gemini → groq).
 * Each reads llm.&lt;name&gt;.url / key / model / vision-model (see application.properties).
 */
@Component
public class LlmProviders {

    static final List<String> KNOWN = List.of("llama", "gemini", "groq");

    private final List<LlmProvider> ordered;

    @org.springframework.beans.factory.annotation.Autowired
    public LlmProviders(Environment env) {
        this(env.getProperty("llm.order", "llama,gemini,groq"), name -> new LlmProvider(
                name,
                env.getProperty("llm." + name + ".url", ""),
                env.getProperty("llm." + name + ".key", ""),
                env.getProperty("llm." + name + ".model", ""),
                env.getProperty("llm." + name + ".vision-model", "")));
    }

    LlmProviders(String order, java.util.function.Function<String, LlmProvider> lookup) {
        List<String> names = new ArrayList<>();
        Arrays.stream(order.split(","))
                .map(s -> s.trim().toLowerCase(Locale.ROOT))
                .filter(KNOWN::contains)
                .filter(s -> !names.contains(s))
                .forEach(names::add);
        // Providers left out of the order still come last, so a key is never silently ignored.
        KNOWN.stream().filter(s -> !names.contains(s)).forEach(names::add);
        this.ordered = names.stream().map(lookup).toList();
    }

    /** All providers in order, configured or not. */
    public List<LlmProvider> all() {
        return ordered;
    }

    /** Configured providers in order (text). */
    public List<LlmProvider> forText() {
        return ordered.stream().filter(LlmProvider::configured).toList();
    }

    /** Configured providers with an image-capable model, in order. */
    public List<LlmProvider> forVision() {
        return ordered.stream().filter(LlmProvider::supportsVision).toList();
    }
}
