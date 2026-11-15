package com.campuspulse.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LlmServiceFallbackTest {

    private static final Map<String, LlmProvider> ALL = Map.of(
            "llama", new LlmProvider("llama", "https://llama/x", "k1", "llama-m", "llama-v"),
            "gemini", new LlmProvider("gemini", "https://gemini/x", "k2", "gemini-m", "gemini-v"),
            "groq", new LlmProvider("groq", "https://groq/x", "k3", "groq-m", ""));

    private final List<String> calls = new ArrayList<>();

    private LlmService service(String order, Function<LlmProvider, LlmProvider> tweak,
                               Function<LlmProvider, Object> behaviour) {
        LlmProviders providers = new LlmProviders(order, name -> tweak.apply(ALL.get(name)));
        LlmService.Transport transport = (provider, body) -> {
            calls.add(provider.name() + ":" + body.get("model") + (body.containsKey("response_format") ? ":json" : ""));
            Object result = behaviour.apply(provider);
            if (result instanceof RuntimeException e) throw e;
            return Map.of("choices", List.of(Map.of("message", Map.of("content", result))));
        };
        return new LlmService(providers, transport, new ObjectMapper(), 5);
    }

    private static WebClientResponseException http(int status) {
        return WebClientResponseException.create(status, "x", null, null, null);
    }

    @Test
    void usesFirstProviderWhenItAnswers() {
        String out = service("llama,gemini,groq", p -> p, p -> "{\"ok\":true}").generateJson("q");
        assertEquals("{\"ok\":true}", out);
        assertEquals(List.of("llama:llama-m:json"), calls);
    }

    @Test
    void fallsThroughOnRateLimitServerErrorAndBadKey() {
        String out = service("llama,gemini,groq", p -> p, p -> switch (p.name()) {
            case "llama" -> http(429);
            case "gemini" -> http(401);
            default -> "hello";
        }).generateResponse("q");
        assertEquals("hello", out);
        assertEquals(3, calls.size());
    }

    @Test
    void unreadableJsonTriesNextProvider() {
        String out = service("llama,gemini", p -> p, p -> p.name().equals("llama") ? "sorry, no" : "```json\n{\"a\":1}\n```")
                .generateJson("q");
        assertTrue(out.contains("\"a\""));
    }

    @Test
    void providerWithoutKeyIsSkipped() {
        service("llama,gemini,groq",
                p -> p.name().equals("llama") ? new LlmProvider("llama", p.url(), "", p.model(), p.visionModel()) : p,
                p -> "{}").generateJson("q");
        assertEquals("gemini", calls.get(0).split(":")[0]);
    }

    @Test
    void jsonModeRejectedIsRetriedWithoutIt() {
        int[] n = {0};
        String out = service("groq", p -> p, p -> n[0]++ == 0 ? http(400) : "{\"x\":1}").generateJson("q");
        assertEquals("{\"x\":1}", out);
        assertEquals(List.of("groq:groq-m:json", "groq:groq-m"), calls.subList(calls.size() - 2, calls.size()));
    }

    @Test
    void allRateLimitedGivesFriendlyMessage() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> service("llama,gemini,groq", p -> p, p -> http(429)).generateResponse("q"));
        assertTrue(e.getMessage().contains("rate limits"));
    }

    @Test
    void visionUsesOnlyVisionModels() {
        String out = service("groq,llama,gemini", p -> p, p -> "page text")
                .generateFromImages("read", List.of(new byte[] {1, 2, 3}));
        assertEquals("page text", out);
        assertEquals("llama:llama-v", calls.get(0), "groq has no vision model, so llama goes first");
    }

    @Test
    void noProviderConfiguredExplainsWhatToSet() {
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> service("llama,gemini,groq",
                p -> new LlmProvider(p.name(), p.url(), "", p.model(), p.visionModel()), p -> "x").generateJson("q"));
        assertTrue(e.getMessage().contains("API_KEY"));
        assertFalse(calls.size() > 0);
    }

    @Test
    void providersMissingFromOrderStillComeLast() {
        LlmProviders providers = new LlmProviders("groq", ALL::get);
        assertEquals(List.of("groq", "gemini"), providers.all().stream().map(LlmProvider::name).toList());
    }

    @Test
    void keyIsNeverPrinted() {
        assertFalse(ALL.get("llama").toString().contains("k1"));
    }

    @Test
    void badKeyOrMissingModelIsSkippedOnLaterCalls() {
        LlmService service = service("llama,groq", p -> p, p -> p.name().equals("llama") ? http(401) : "{}");
        service.generateJson("first");
        service.generateJson("second");
        assertEquals(1, calls.stream().filter(c -> c.startsWith("llama")).count(), "llama paused after 401");
        assertEquals(2, calls.stream().filter(c -> c.startsWith("groq")).count());
    }

    @Test
    void providerErrorMessageIsShortAndReadable() {
        WebClientResponseException e = WebClientResponseException.create(404, "Not Found", null,
                "[{\"error\": {\"code\": 404, \"message\": \"This model models/gemini-2.5-flash is no longer available\"}}]"
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8), java.nio.charset.StandardCharsets.UTF_8);
        assertEquals(" — This model models/gemini-2.5-flash is no longer available", LlmService.providerMessage(e));
    }
}
