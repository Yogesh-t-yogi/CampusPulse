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

    // ------------------------------------------------------------------ TC-15

    private static final String GROQ_SECRET = "gsk_TEST_invalid_key_0123456789";
    private static final String GEMINI_SECRET = "AIza_TEST_gemini_key_9876543210";

    private ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> captureLogs() {
        ch.qos.logback.classic.Logger logger =
                (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(LlmService.class);
        ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender =
                new ch.qos.logback.core.read.ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return appender;
    }

    private static void detach(ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender) {
        ((ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(LlmService.class)).detachAppender(appender);
    }

    private static String allText(ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender) {
        StringBuilder text = new StringBuilder();
        for (var event : appender.list) {
            text.append(event.getFormattedMessage()).append('\n');
            if (event.getThrowableProxy() != null) text.append(event.getThrowableProxy().getMessage()).append('\n');
        }
        return text.toString();
    }

    /** Groq answers 401 like the real API does for a bad key (message only, no key echoed). */
    private static WebClientResponseException invalidKey() {
        return WebClientResponseException.create(401, "Unauthorized", null,
                "{\"error\":{\"message\":\"Invalid API Key\",\"type\":\"invalid_request_error\",\"code\":\"invalid_api_key\"}}"
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8), java.nio.charset.StandardCharsets.UTF_8);
    }

    private LlmService groqThenGemini(Function<LlmProvider, Object> behaviour) {
        // LLM_ORDER=groq,gemini with real-looking keys; the transport records who was called.
        Map<String, LlmProvider> providers = Map.of(
                "groq", new LlmProvider("groq", "https://groq/x", GROQ_SECRET, "groq-m", ""),
                "gemini", new LlmProvider("gemini", "https://gemini/x", GEMINI_SECRET, "gemini-m", "gemini-v"));
        LlmService.Transport transport = (provider, body) -> {
            calls.add(provider.name());
            Object result = behaviour.apply(provider);
            if (result instanceof RuntimeException e) throw e;
            return Map.of("choices", List.of(Map.of("message", Map.of("content", result))));
        };
        return new LlmService(new LlmProviders("groq,gemini", providers::get), transport, new ObjectMapper(), 5);
    }

    @Test
    void tc15_groqInvalidKeyFallsBackToGeminiAndKeyIsNeverLogged() {
        var logs = captureLogs();
        try {
            LlmService service = groqThenGemini(p -> p.name().equals("groq") ? invalidKey() : "answer from gemini");
            assertEquals("answer from gemini", service.generateResponse("q"));
            assertEquals(List.of("groq", "gemini"), calls, "LLM_ORDER respected: groq tried first");

            String logged = allText(logs);
            assertTrue(logged.contains("groq") && logged.contains("key rejected (HTTP 401)"), logged);
            assertFalse(logged.contains(GROQ_SECRET), "Groq key in log: " + logged);
            assertFalse(logged.contains(GEMINI_SECRET), "Gemini key in log: " + logged);

            // Second call: groq is paused after the bad key, gemini answers directly.
            assertEquals("answer from gemini", service.generateResponse("q2"));
            assertEquals(List.of("groq", "gemini", "gemini"), calls);
        } finally {
            detach(logs);
        }
    }

    @Test
    void tc15_whenEveryProviderFailsTheErrorNamesNoKey() {
        var logs = captureLogs();
        try {
            IllegalStateException e = assertThrows(IllegalStateException.class,
                    () -> groqThenGemini(p -> invalidKey()).generateJson("q"));
            assertTrue(e.getMessage().contains("groq: key rejected (HTTP 401)"), e.getMessage());
            assertTrue(e.getMessage().contains("gemini: key rejected (HTTP 401)"), e.getMessage());
            for (String secret : List.of(GROQ_SECRET, GEMINI_SECRET)) {
                assertFalse(e.getMessage().contains(secret), "key in exception message");
                assertFalse(allText(logs).contains(secret), "key in log");
            }
        } finally {
            detach(logs);
        }
    }

    @Test
    void providerStatusAndToStringNeverExposeKeys() {
        LlmService service = groqThenGemini(p -> "x");
        String status = service.status().toString();
        assertFalse(status.contains(GROQ_SECRET) || status.contains(GEMINI_SECRET), status);
    }
}
