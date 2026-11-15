package com.campuspulse.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

/**
 * Chat calls with fallback across providers (plan 14, Phase B): tries each configured provider in
 * order (default Gemini → Groq) and moves on when one is rate limited, down, times out,
 * rejects its key or (for JSON calls) returns unreadable JSON. All use the OpenAI chat format.
 */
@Service
public class LlmService {

    private static final Logger log = LoggerFactory.getLogger(LlmService.class);
    static final int MAX_TOKENS = 8000;

    /** Sends one request body to one provider; returns the parsed JSON response. */
    interface Transport {
        Map<?, ?> post(LlmProvider provider, Map<String, Object> body);
    }

    private final LlmProviders providers;
    private final Transport transport;
    private final ObjectMapper objectMapper;
    private final long timeoutSeconds;
    /** Providers that rejected response_format once; called without it afterwards. */
    private final Set<String> noJsonMode = ConcurrentHashMap.newKeySet();
    /** provider|model → time until which it is skipped (after a bad key or missing model). */
    private final Map<String, Long> pausedUntil = new ConcurrentHashMap<>();
    static final long PAUSE_MS = 10 * 60 * 1000;

    @Autowired
    public LlmService(WebClient.Builder webClientBuilder, LlmProviders providers, ObjectMapper objectMapper,
                      @Value("${llm.timeout-seconds:120}") long timeoutSeconds) {
        this(providers, webTransport(webClientBuilder.build(), timeoutSeconds), objectMapper, timeoutSeconds);
    }

    LlmService(LlmProviders providers, Transport transport, ObjectMapper objectMapper, long timeoutSeconds) {
        this.providers = providers;
        this.transport = transport;
        this.objectMapper = objectMapper;
        this.timeoutSeconds = timeoutSeconds;
    }

    /** Free text answer (temperature 0.2). */
    public String generateResponse(String prompt) {
        return run(providers.forText(), p -> textBody(p.model(), prompt, 0.2), false, false);
    }

    /**
     * JSON answer: temperature 0 and the provider's JSON mode, so the reply is a single JSON object.
     * The prompt must ask for a JSON object.
     */
    public String generateJson(String prompt) {
        return run(providers.forText(), p -> textBody(p.model(), prompt, 0.0), true, false);
    }

    /**
     * Reads images (e.g. scanned PDF pages as JPEG) with a vision model and returns its text answer.
     * Only providers with a vision model are tried.
     */
    public String generateFromImages(String prompt, List<byte[]> jpegImages) {
        if (jpegImages == null || jpegImages.isEmpty()) {
            throw new IllegalArgumentException("No images to read.");
        }
        return run(providers.forVision(), p -> visionBody(p.visionModel(), prompt, jpegImages), false, true);
    }

    /** Which providers are set up (for the admin status view). Never includes keys. */
    public List<Map<String, Object>> status() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (LlmProvider p : providers.all()) {
            Map<String, Object> row = new HashMap<>();
            row.put("name", p.name());
            row.put("configured", p.configured());
            row.put("model", p.model());
            row.put("visionModel", p.supportsVision() ? p.visionModel() : "");
            rows.add(row);
        }
        return rows;
    }

    public boolean anyConfigured() {
        return !providers.forText().isEmpty();
    }

    // ------------------------------------------------------------------ chain

    private String run(List<LlmProvider> candidates, Function<LlmProvider, Map<String, Object>> body,
                       boolean json, boolean vision) {
        if (candidates.isEmpty()) {
            throw new IllegalStateException(vision
                    ? "No AI provider that can read images is set up. Add GEMINI_API_KEY or GROQ_API_KEY "
                        + "(with a vision model) to .env and restart the backend."
                    : "No AI provider is set up. Add GEMINI_API_KEY or GROQ_API_KEY to .env "
                        + "and restart the backend.");
        }
        List<String> failures = new ArrayList<>();
        String unreadable = null;
        int rateLimited = 0;
        for (LlmProvider provider : candidates) {
            Map<String, Object> request = body.apply(provider);
            String label = provider.name() + "(" + request.get("model") + ")";
            String pauseKey = provider.name() + "|" + request.get("model");
            Long until = pausedUntil.get(pauseKey);
            if (until != null && until > System.currentTimeMillis()) {
                failures.add(provider.name() + ": skipped after an earlier key/model error");
                continue;
            }
            try {
                String content = callOne(provider, request, json);
                if (json && !readableJson(content)) {
                    failures.add(provider.name() + ": unreadable JSON");
                    if (unreadable == null) unreadable = content;
                    continue;
                }
                if (!failures.isEmpty()) {
                    log.info("AI answer from {} after: {}", provider.name(), failures);
                }
                return content;
            } catch (RuntimeException e) {
                String reason = reason(e);
                if (reason.equals("rate limit")) rateLimited++;
                failures.add(provider.name() + ": " + reason);
                if (isConfigError(e)) {
                    pausedUntil.put(pauseKey, System.currentTimeMillis() + PAUSE_MS);
                    log.warn("AI provider {} failed: {} — skipping it for 10 minutes; check its key/model in .env", label, reason);
                } else {
                    log.warn("AI provider {} failed: {}", label, reason);
                }
            }
        }
        // Every provider failed; a non-JSON answer is still better than nothing for lenient callers.
        if (unreadable != null) return unreadable;
        if (rateLimited == candidates.size()) {
            throw new IllegalStateException("The AI providers' rate limits were reached. Wait a minute and try again.");
        }
        throw new IllegalStateException("No AI provider could answer (" + String.join("; ", failures) + ").");
    }

    private String callOne(LlmProvider provider, Map<String, Object> body, boolean json) {
        Map<String, Object> request = new HashMap<>(body);
        boolean jsonMode = json && !noJsonMode.contains(provider.name());
        if (jsonMode) request.put("response_format", Map.of("type", "json_object"));
        Map<?, ?> response;
        try {
            response = transport.post(provider, request);
        } catch (RuntimeException e) {
            WebClientResponseException http = findCause(e, WebClientResponseException.class);
            if (!jsonMode || http == null || http.getStatusCode().value() != 400) throw e;
            // This model doesn't support JSON mode: remember and retry without it.
            log.warn("{} rejected JSON mode; continuing without it", provider.name());
            noJsonMode.add(provider.name());
            request.remove("response_format");
            response = transport.post(provider, request);
        }
        return extractContent(response);
    }

    private String reason(RuntimeException e) {
        WebClientResponseException http = findCause(e, WebClientResponseException.class);
        if (http != null) {
            int status = http.getStatusCode().value();
            if (status == 429) return "rate limit";
            if (status == 401 || status == 403) return "key rejected (HTTP " + status + ")";
            String detail = providerMessage(http);
            if (status == 404) return "model or URL not found (HTTP 404)" + detail;
            return "HTTP " + status + detail;
        }
        if (findCause(e, TimeoutException.class) != null) return "no answer within " + timeoutSeconds + " s";
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }

    /** Bad key or unknown model/URL: retrying soon won't help. */
    private static boolean isConfigError(RuntimeException e) {
        WebClientResponseException http = findCause(e, WebClientResponseException.class);
        if (http == null) return false;
        int status = http.getStatusCode().value();
        return status == 401 || status == 403 || status == 404;
    }

    /** First line of the provider's error message, shortened (never contains our key). */
    static String providerMessage(WebClientResponseException http) {
        String body = http.getResponseBodyAsString();
        if (body == null || body.isBlank()) return "";
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"message\"[ \t\r\n]*:[ \t\r\n]*\"([^\"]{1,200})").matcher(body);
        String text = m.find() ? m.group(1) : body;
        text = text.replaceAll("[ \t\r\n]+", " ").trim();
        return text.isEmpty() ? "" : " — " + (text.length() > 160 ? text.substring(0, 160) + "…" : text);
    }

    // ------------------------------------------------------------------ bodies

    static Map<String, Object> textBody(String model, String prompt, double temperature) {
        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("messages", List.of(Map.of("role", "user", "content", prompt)));
        body.put("temperature", temperature);
        body.put("max_tokens", MAX_TOKENS);
        return body;
    }

    static Map<String, Object> visionBody(String model, String prompt, List<byte[]> jpegImages) {
        List<Map<String, Object>> content = new ArrayList<>();
        content.add(Map.of("type", "text", "text", prompt));
        for (byte[] image : jpegImages) {
            content.add(Map.of("type", "image_url", "image_url",
                    Map.of("url", "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(image))));
        }
        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("messages", List.of(Map.of("role", "user", "content", content)));
        body.put("temperature", 0.0);
        body.put("max_tokens", MAX_TOKENS);
        return body;
    }

    // ------------------------------------------------------------------ helpers

    /** JSON object/array somewhere in the reply (code fences and chatter around it are tolerated). */
    boolean readableJson(String content) {
        if (content == null) return false;
        int start = indexOfFirst(content, '{', '[');
        int end = Math.max(content.lastIndexOf('}'), content.lastIndexOf(']'));
        if (start < 0 || end <= start) return false;
        try {
            objectMapper.readTree(content.substring(start, end + 1));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static int indexOfFirst(String text, char a, char b) {
        int i = text.indexOf(a);
        int j = text.indexOf(b);
        if (i < 0) return j;
        if (j < 0) return i;
        return Math.min(i, j);
    }

    static String extractContent(Map<?, ?> response) {
        if (response == null) throw new IllegalStateException("empty response");
        if (!(response.get("choices") instanceof List<?> choices) || choices.isEmpty()
                || !(choices.get(0) instanceof Map<?, ?> choice)
                || !(choice.get("message") instanceof Map<?, ?> message)
                || message.get("content") == null) {
            throw new IllegalStateException("response had no message content");
        }
        Object content = message.get("content");
        // Some providers return content as a list of parts.
        if (content instanceof List<?> parts) {
            StringBuilder text = new StringBuilder();
            for (Object part : parts) {
                if (part instanceof Map<?, ?> m && m.get("text") != null) text.append(m.get("text"));
            }
            content = text.toString();
        }
        String text = content.toString();
        if (text.isBlank()) throw new IllegalStateException("empty answer");
        return text;
    }

    private static <T extends Throwable> T findCause(Throwable error, Class<T> type) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (type.isInstance(t)) return type.cast(t);
        }
        return null;
    }

    private static Transport webTransport(WebClient webClient, long timeoutSeconds) {
        return (provider, body) -> webClient.post()
                .uri(provider.url())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + provider.key())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                // One quick retry for temporary server errors; rate limits go to the next provider.
                .retryWhen(Retry.backoff(1, Duration.ofSeconds(2)).filter(LlmService::isServerError))
                .block();
    }

    private static boolean isServerError(Throwable error) {
        return error instanceof WebClientResponseException response && response.getStatusCode().value() >= 500;
    }
}
