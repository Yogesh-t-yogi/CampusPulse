package com.campuspulse.service.syllabus;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * LlamaParse (LlamaCloud) Parse API v2 (change 055): upload a PDF, wait for the job, take Markdown per page.
 * POST /api/v2/parse/upload (multipart: file + configuration JSON) → GET /api/v2/parse/{id}?expand=markdown
 * → {"job":{"status":…}, "markdown":{"pages":[{"page_number":1,"markdown":"…","success":true}]}}.
 */
@Service
public class LlamaParseClient {

    private static final Logger log = LoggerFactory.getLogger(LlamaParseClient.class);
    static final long POLL_MS = 3000;

    /** The two HTTP calls, replaceable in tests. */
    interface Api {
        String upload(byte[] pdf, String configurationJson);

        JsonNode job(String jobId);
    }

    private final Api api;
    private final ObjectMapper objectMapper;
    private final String key;
    private final String tier;
    private final long timeoutMs;
    private final long pollMs;

    @Autowired
    public LlamaParseClient(WebClient.Builder builder, ObjectMapper objectMapper,
                            @Value("${llamaparse.base-url:https://api.cloud.llamaindex.ai}") String baseUrl,
                            @Value("${llamaparse.key:}") String key,
                            @Value("${llamaparse.tier:cost_effective}") String tier,
                            @Value("${llamaparse.timeout-seconds:300}") long timeoutSeconds) {
        this(webApi(builder.baseUrl(baseUrl.trim()).build(), key == null ? "" : key.trim(), objectMapper),
                objectMapper, key, tier, timeoutSeconds * 1000, POLL_MS);
    }

    LlamaParseClient(Api api, ObjectMapper objectMapper, String key, String tier, long timeoutMs, long pollMs) {
        this.api = api;
        this.objectMapper = objectMapper;
        this.key = key == null ? "" : key.trim();
        this.tier = tier == null || tier.isBlank() ? "cost_effective" : tier.trim();
        this.timeoutMs = timeoutMs;
        this.pollMs = pollMs;
    }

    public boolean configured() {
        return !key.isEmpty() && key.chars().noneMatch(Character::isISOControl) && !key.contains(" ");
    }

    public String tier() {
        return tier;
    }

    /**
     * Parses the given pages (1-based) and returns page → Markdown for pages LlamaParse read.
     * Throws IllegalStateException with a readable reason if the job can't be done.
     */
    public Map<Integer, String> parse(byte[] pdf, List<Integer> pages) {
        if (!configured()) throw new IllegalStateException("LLAMA_CLOUD_API_KEY is not set");
        if (pages.isEmpty()) return Map.of();
        Map<String, Object> configuration = new LinkedHashMap<>();
        configuration.put("tier", tier);
        configuration.put("version", "latest");
        configuration.put("page_ranges", Map.of("target_pages",
                pages.stream().map(String::valueOf).collect(Collectors.joining(","))));
        String jobId;
        try {
            jobId = api.upload(pdf, objectMapper.writeValueAsString(configuration));
        } catch (RuntimeException e) {
            throw new IllegalStateException("LlamaParse upload failed: " + reason(e), e);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
        log.info("LlamaParse job {} started for {} page(s), tier {}", jobId, pages.size(), tier);

        long deadline = System.currentTimeMillis() + timeoutMs;
        JsonNode result;
        while (true) {
            try {
                result = api.job(jobId);
            } catch (RuntimeException e) {
                throw new IllegalStateException("LlamaParse status check failed: " + reason(e), e);
            }
            String status = result.path("job").path("status").asText("");
            if ("COMPLETED".equals(status)) break;
            if ("FAILED".equals(status) || "CANCELLED".equals(status)) {
                String error = result.path("job").path("error_message").asText("");
                throw new IllegalStateException("LlamaParse job " + status.toLowerCase()
                        + (error.isBlank() ? "" : ": " + error));
            }
            if (System.currentTimeMillis() > deadline) {
                throw new IllegalStateException("LlamaParse did not finish within " + timeoutMs / 1000 + " s");
            }
            sleep(pollMs);
        }
        return pages(result, pages);
    }

    /** page → Markdown from the result; page numbers are the PDF's own (1-based), else matched by order. */
    static Map<Integer, String> pages(JsonNode result, List<Integer> requested) {
        Map<Integer, String> out = new LinkedHashMap<>();
        JsonNode list = result.path("markdown").path("pages");
        int index = 0;
        for (JsonNode page : list) {
            int number = page.path("page_number").asInt(-1);
            if (!requested.contains(number) && index < requested.size()) number = requested.get(index);
            index++;
            if (!page.path("success").asBoolean(true)) continue;
            String text = page.path("markdown").asText("").trim();
            if (!text.isEmpty() && requested.contains(number)) out.put(number, text);
        }
        return out;
    }

    private static String reason(RuntimeException e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof WebClientResponseException http) {
                int status = http.getStatusCode().value();
                if (status == 401 || status == 403) return "key rejected (HTTP " + status + ") — check LLAMA_CLOUD_API_KEY and region";
                if (status == 402) return "out of credits (HTTP 402)";
                if (status == 429) return "rate limit (HTTP 429)";
                String body = http.getResponseBodyAsString();
                return "HTTP " + status + (body == null || body.isBlank() ? "" : " — " + body.substring(0, Math.min(160, body.length())));
            }
        }
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted");
        }
    }

    /** Responses are read as text and parsed here (Spring Boot 4's web client can't decode this JsonNode type). */
    private static JsonNode parse(ObjectMapper objectMapper, String body) {
        try {
            return objectMapper.readTree(body == null ? "{}" : body);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("unreadable LlamaParse response");
        }
    }

    private static Api webApi(WebClient client, String key, ObjectMapper objectMapper) {
        return new Api() {
            @Override
            public String upload(byte[] pdf, String configurationJson) {
                MultipartBodyBuilder parts = new MultipartBodyBuilder();
                parts.part("file", new ByteArrayResource(pdf) {
                    @Override
                    public String getFilename() {
                        return "syllabus.pdf";
                    }
                }).contentType(MediaType.APPLICATION_PDF);
                parts.part("configuration", configurationJson);
                JsonNode created = parse(objectMapper, client.post().uri("/api/v2/parse/upload")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + key)
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .body(BodyInserters.fromMultipartData(parts.build()))
                        .retrieve().bodyToMono(String.class)
                        .timeout(Duration.ofSeconds(120)).block());
                String id = created == null ? "" : created.path("id").asText(created.path("job").path("id").asText(""));
                if (id.isBlank()) throw new IllegalStateException("no job id in the upload response");
                return id;
            }

            @Override
            public JsonNode job(String jobId) {
                return parse(objectMapper, client.get().uri(uri -> uri.path("/api/v2/parse/{id}").queryParam("expand", "markdown").build(jobId))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + key)
                        .retrieve().bodyToMono(String.class)
                        .timeout(Duration.ofSeconds(60)).block());
            }
        };
    }
}
