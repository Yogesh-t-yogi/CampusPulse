package com.campuspulse.service.syllabus;

import com.campuspulse.service.ai.LlmService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Change 055: LlamaParse v2 job flow, and its place in front of the vision models. */
class LlamaParseClientTest {

    private final ObjectMapper mapper = new ObjectMapper();

    /** Fake API: records the configuration, answers PENDING once, then the given result. */
    private final class FakeApi implements LlamaParseClient.Api {
        final List<String> configurations = new ArrayList<>();
        final String finalJson;
        int polls;

        FakeApi(String finalJson) {
            this.finalJson = finalJson;
        }

        @Override
        public String upload(byte[] pdf, String configurationJson) {
            configurations.add(configurationJson);
            return "pjb-1";
        }

        @Override
        public JsonNode job(String jobId) {
            try {
                return mapper.readTree(polls++ == 0 ? "{\"job\":{\"status\":\"PENDING\"}}" : finalJson);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
    }

    private LlamaParseClient client(FakeApi api, String key) {
        return new LlamaParseClient(api, mapper, key, "cost_effective", 5_000, 1);
    }

    @Test
    void parsesRequestedPagesFromCompletedJob() throws Exception {
        FakeApi api = new FakeApi("""
                {"job":{"status":"COMPLETED"},"markdown":{"pages":[
                  {"page_number":2,"markdown":"# UNIT 2 Trees","success":true},
                  {"page_number":5,"markdown":"","success":true},
                  {"page_number":6,"markdown":"junk","success":false}]}}
                """);
        Map<Integer, String> pages = client(api, "llx-test").parse(new byte[] {1}, List.of(2, 5, 6));

        assertEquals(Map.of(2, "# UNIT 2 Trees"), pages);
        JsonNode config = mapper.readTree(api.configurations.get(0));
        assertEquals("cost_effective", config.get("tier").asText());
        assertEquals("2,5,6", config.get("page_ranges").get("target_pages").asText());
        assertEquals(2, api.polls);
    }

    @Test
    void failedJobAndMissingKeyGiveReadableErrors() {
        FakeApi failed = new FakeApi("{\"job\":{\"status\":\"FAILED\",\"error_message\":\"bad pdf\"}}");
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> client(failed, "llx-test").parse(new byte[] {1}, List.of(1)));
        assertTrue(e.getMessage().contains("bad pdf"));
        assertFalse(client(failed, "").configured());
    }

    @Test
    void pageNumbersFallBackToRequestOrder() throws Exception {
        JsonNode result = mapper.readTree("{\"markdown\":{\"pages\":[{\"page_number\":1,\"markdown\":\"A\"},{\"page_number\":2,\"markdown\":\"B\"}]}}");
        assertEquals(Map.of(4, "A", 9, "B"), LlamaParseClient.pages(result, List.of(4, 9)));
    }

    @Test
    void readerUsesLlamaParseFirstAndVisionOnlyForTheRest() throws Exception {
        FakeApi api = new FakeApi("{\"job\":{\"status\":\"COMPLETED\"},\"markdown\":{\"pages\":[{\"page_number\":2,\"markdown\":\"## UNIT 2\",\"success\":true}]}}");
        LlmService llm = mock(LlmService.class);
        when(llm.generateFromImages(anyString(), anyList())).thenReturn("page one by vision");
        ScannedPageReader reader = new ScannedPageReader(llm, client(api, "llx-test"), mock(JdbcTemplate.class), 60, 1, 100);

        var results = reader.read(ScannedPdfReadingTest.twoPagePdf(), "sha", List.of(1, 2), done -> { });

        assertEquals("ai", results.get(0).method());
        assertEquals("llamaparse", results.get(1).method());
        assertEquals("## UNIT 2", results.get(1).text());
    }

    @Test
    void readerSkipsVisionWhenLlamaParseReadsEverything() throws Exception {
        FakeApi api = new FakeApi("{\"job\":{\"status\":\"COMPLETED\"},\"markdown\":{\"pages\":[{\"page_number\":2,\"markdown\":\"## UNIT 2\"}]}}");
        LlmService llm = mock(LlmService.class);
        ScannedPageReader reader = new ScannedPageReader(llm, client(api, "llx-test"), mock(JdbcTemplate.class), 60, 1, 100);
        reader.read(ScannedPdfReadingTest.twoPagePdf(), "sha", List.of(2), done -> { });
        verify(llm, never()).generateFromImages(anyString(), anyList());
        assertTrue(reader.canRead());
    }
}
