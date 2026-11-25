package com.campuspulse.service.syllabus;

import com.campuspulse.service.ai.LlmService;
import com.campuspulse.service.ai.SyllabusProcessingServiceAccess;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TC-08: a scanned page read by LlamaParse is attributed to LlamaParse — in the page result, in the
 * pdf_page_text cache row, and in the page summary the status endpoint returns ("pages" → counts).
 */
class LlamaParseAttributionTest {

    @Test
    void tc08_scannedPageReadByLlamaParseIsReportedAsLlamaParse() throws Exception {
        LlmService llm = mock(LlmService.class);
        LlamaParseClient llamaParse = mock(LlamaParseClient.class);
        when(llamaParse.configured()).thenReturn(true);
        when(llamaParse.parse(org.mockito.ArgumentMatchers.any(), eq(List.of(2))))
                .thenReturn(Map.of(2, "## UNIT 2 Trees and Graphs"));
        JdbcTemplate jdbc = mock(JdbcTemplate.class); // nothing cached yet
        ScannedPageReader reader = new ScannedPageReader(llm, llamaParse, jdbc, 60, 1, 100);

        byte[] pdf = ScannedPdfReadingTest.twoPagePdf();
        var results = reader.read(pdf, "sha-1", List.of(2), done -> { });

        // 1. The page result says who read it.
        assertEquals(1, results.size());
        assertEquals("llamaparse", results.get(0).method());
        assertEquals("## UNIT 2 Trees and Graphs", results.get(0).text());
        verify(llm, never()).generateFromImages(anyString(), anyList());

        // 2. The cache row (pdf_page_text.method) records LlamaParse.
        verify(jdbc).update(contains("INSERT INTO pdf_page_text"), eq("sha-1"), eq(2), eq("llamaparse"),
                eq("## UNIT 2 Trees and Graphs"));

        // 3. The page summary saved for GET /api/ai/syllabus/{id}/status counts the page under llamaparse.
        Map<String, Object> summary = SyllabusProcessingServiceAccess.summary(
                List.of("UNIT 1 text layer", ""), results);
        assertEquals(Map.of("text", 1, "llamaparse", 1), summary.get("counts"));
        assertEquals(List.of(), summary.get("unreadablePages"));

        // ... and survives the JSON round trip through syllabus_file.page_summary.
        JdbcTemplate fileJdbc = mock(JdbcTemplate.class);
        SyllabusFileStore store = new SyllabusFileStore(fileJdbc, new ObjectMapper());
        store.finish(9L, summary, List.of());
        ArgumentCaptor<Object> args = ArgumentCaptor.forClass(Object.class);
        verify(fileJdbc).update(contains("page_summary"), args.capture(), args.capture(), args.capture());
        String storedJson = (String) args.getAllValues().get(1);
        when(fileJdbc.queryForList(contains("page_summary"), eq(String.class), eq(9L))).thenReturn(List.of(storedJson));
        Map<String, Object> shown = store.summary(9L);
        assertEquals(Map.of("text", 1, "llamaparse", 1), shown.get("counts"));
        assertTrue(storedJson.contains("\"llamaparse\":1"), storedJson);
    }

    @Test
    void cachedLlamaParsePageKeepsItsAttributionWithoutNewCalls() throws Exception {
        LlmService llm = mock(LlmService.class);
        LlamaParseClient llamaParse = mock(LlamaParseClient.class);
        when(llamaParse.configured()).thenReturn(true);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<org.springframework.jdbc.core.RowMapper<ScannedPageReader.PageResult>>any(),
                eq("sha-2"), eq(2)))
                .thenReturn(List.of(new ScannedPageReader.PageResult(2, "cached text", "llamaparse")));
        var results = new ScannedPageReader(llm, llamaParse, jdbc, 60, 1, 100)
                .read(ScannedPdfReadingTest.twoPagePdf(), "sha-2", List.of(2), done -> { });
        assertEquals("llamaparse", results.get(0).method());
        verify(llamaParse, never()).parse(org.mockito.ArgumentMatchers.any(), anyList());
        verify(llm, never()).generateFromImages(anyString(), anyList());
    }
}
