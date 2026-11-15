package com.campuspulse.service.ai;

import com.campuspulse.dto.ai.AiSyllabusResponse;
import com.campuspulse.service.analysis.AnalysisResultService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Runs syllabus AI structuring + topic indexing in the background and records
 * progress in syllabus.processing_status / processing_message.
 */
@Service
public class SyllabusProcessingService {

    public static final String PROCESSING = "PROCESSING";
    public static final String READY = "READY";
    public static final String READY_WITH_WARNINGS = "READY_WITH_WARNINGS";
    public static final String FAILED = "FAILED";

    private static final Logger log = LoggerFactory.getLogger(SyllabusProcessingService.class);

    private final JdbcTemplate jdbcTemplate;
    private final SyllabusAiService syllabusAiService;
    private final SyllabusPersistenceService syllabusPersistenceService;
    private final AnalysisResultService analysisResultService;
    private final com.campuspulse.service.syllabus.SyllabusFileStore fileStore;
    private final com.campuspulse.service.syllabus.ScannedPageReader pageReader;

    public SyllabusProcessingService(
            JdbcTemplate jdbcTemplate,
            SyllabusAiService syllabusAiService,
            SyllabusPersistenceService syllabusPersistenceService,
            AnalysisResultService analysisResultService,
            com.campuspulse.service.syllabus.SyllabusFileStore fileStore,
            com.campuspulse.service.syllabus.ScannedPageReader pageReader) {
        this.fileStore = fileStore;
        this.pageReader = pageReader;
        this.jdbcTemplate = jdbcTemplate;
        this.syllabusAiService = syllabusAiService;
        this.syllabusPersistenceService = syllabusPersistenceService;
        this.analysisResultService = analysisResultService;
    }

    /**
     * Marks the syllabus as PROCESSING. Returns false if it already is
     * (so the same syllabus is never processed twice at once).
     */
    public boolean markProcessing(Long syllabusId) {
        int updated = jdbcTemplate.update("""
                UPDATE syllabus
                SET processing_status = ?, processing_message = ?
                WHERE id = ? AND (processing_status IS NULL OR processing_status <> ?)
                """, PROCESSING, "Extracting subjects, units and topics…", syllabusId, PROCESSING);
        return updated == 1;
    }

    public boolean hasPagesToRead(Long syllabusId) {
        return fileStore.hasPending(syllabusId);
    }

    @Async
    public void processInBackground(Long syllabusId, String extractedText) {
        try {
            extractedText = readWaitingPages(syllabusId, extractedText);
            AiSyllabusResponse structure = syllabusAiService.structureSyllabus(extractedText);
            syllabusPersistenceService.saveAiStructure(syllabusId, structure);
            setStatus(syllabusId, PROCESSING, "Indexing topics for skill matching…");

            SyllabusPersistenceService.EmbeddingGenerationResult embeddings =
                    syllabusPersistenceService.generateEmbeddings(syllabusId);
            // Curriculum changed: old saved reports are no longer valid.
            analysisResultService.clearForSyllabus(syllabusId);

            if (embeddings.failed() == 0) {
                setStatus(syllabusId, READY,
                        "Curriculum ready: " + embeddings.generated() + " topics indexed.");
            } else {
                setStatus(syllabusId, READY_WITH_WARNINGS, embeddings.warning());
            }
        } catch (RuntimeException e) {
            log.warn("Processing failed for syllabus {}: {}", syllabusId, e.getMessage());
            String message = e.getMessage() == null ? "Processing failed." : e.getMessage();
            setStatus(syllabusId, FAILED,
                    message.length() > 500 ? message.substring(0, 500) : message);
        }
    }

    /**
     * Scanned pages saved at upload are read now (AI vision, then Tesseract if installed), merged with
     * the text pages in page order and saved as the syllabus text. Returns the full text.
     */
    String readWaitingPages(Long syllabusId, String knownText) {
        com.campuspulse.service.syllabus.SyllabusFileStore.StoredFile file = fileStore.pending(syllabusId);
        if (file == null) return knownText;
        List<Integer> pages = file.pendingPages();
        setStatus(syllabusId, PROCESSING, "Reading " + pages.size() + " scanned page(s) with AI...");
        List<com.campuspulse.service.syllabus.ScannedPageReader.PageResult> results;
        try {
            results = pageReader.read(file.bytes(), file.sha256(), pages, done ->
                    setStatus(syllabusId, PROCESSING,
                            "Reading scanned pages with AI: " + done + " of " + pages.size() + " done..."));
        } catch (java.io.IOException e) {
            throw new IllegalStateException("The PDF could not be opened for reading.");
        }
        PageMerge merge = mergePages(file.pageTexts(), results);
        @SuppressWarnings("unchecked")
        List<Integer> unreadable = (List<Integer>) merge.summary().get("unreadablePages");
        fileStore.finish(syllabusId, merge.summary(), unreadable);
        if (merge.text().isBlank()) {
            throw new IllegalStateException("No page of this PDF could be read. Check LLAMA_CLOUD_API_KEY "
                    + "(LlamaParse) or GEMINI_API_KEY in .env, then use Re-process.");
        }
        jdbcTemplate.update("UPDATE syllabus SET extracted_text = ? WHERE id = ?", merge.text(), syllabusId);
        setStatus(syllabusId, PROCESSING, "Extracting subjects, units and topics...");
        return merge.text();
    }

    record PageMerge(String text, Map<String, Object> summary) {
    }

    /** AI text replaces a page's text layer; a page with neither is listed as unreadable. */
    static PageMerge mergePages(List<String> pageTexts, List<com.campuspulse.service.syllabus.ScannedPageReader.PageResult> results) {
        List<String> merged = new java.util.ArrayList<>(pageTexts);
        Map<String, Integer> counts = new java.util.LinkedHashMap<>();
        List<Integer> unreadable = new java.util.ArrayList<>();
        java.util.Set<Integer> handled = new java.util.HashSet<>();
        for (var r : results) {
            handled.add(r.page());
            String layer = merged.get(r.page() - 1);
            boolean hasLayer = layer != null && !layer.isBlank();
            if (!r.text().isBlank()) {
                merged.set(r.page() - 1, r.text());
                counts.merge(r.method(), 1, Integer::sum);
            } else if (hasLayer) {
                counts.merge("text", 1, Integer::sum);
            } else {
                unreadable.add(r.page());
                counts.merge(r.method(), 1, Integer::sum);
            }
        }
        int textPages = (int) java.util.stream.IntStream.rangeClosed(1, pageTexts.size())
                .filter(page -> !handled.contains(page)).count();
        if (textPages > 0) counts.merge("text", textPages, Integer::sum);
        Map<String, Object> summary = new java.util.LinkedHashMap<>();
        summary.put("pages", pageTexts.size());
        summary.put("counts", counts);
        summary.put("unreadablePages", unreadable);
        return new PageMerge(com.campuspulse.service.syllabus.PdfTextExtractionService.joinPages(merged, List.of()), summary);
    }

    public Map<String, Object> getStatus(Long syllabusId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT s.processing_status AS status,
                       s.processing_message AS message,
                       (SELECT count(*) FROM topic t
                          JOIN unit u ON u.id = t.unit_id
                          JOIN subject sb ON sb.id = u.subject_id
                         WHERE sb.syllabus_id = s.id) AS "topicCount"
                FROM syllabus s WHERE s.id = ?
                """, syllabusId);
        if (rows.isEmpty()) {
            return Map.of("status", "UNKNOWN");
        }
        Map<String, Object> row = rows.get(0);
        long topicCount = ((Number) row.get("topicCount")).longValue();
        Object status = row.get("status");
        if (status == null) {
            // Uploaded before this feature, or never processed.
            status = topicCount > 0 ? READY : "NOT_PROCESSED";
        }
        Map<String, Object> view = new java.util.HashMap<>();
        view.put("syllabusId", syllabusId);
        view.put("status", status);
        view.put("message", row.get("message") == null ? "" : row.get("message"));
        view.put("topicCount", topicCount);
        Map<String, Object> pages = fileStore.summary(syllabusId);
        if (pages != null) view.put("pages", pages);
        return view;
    }

    // A restart stops background work; don't leave syllabi stuck in PROCESSING.
    @EventListener(ApplicationReadyEvent.class)
    public void failInterruptedProcessing() {
        int stuck = jdbcTemplate.update("""
                UPDATE syllabus SET processing_status = ?, processing_message = ?
                WHERE processing_status = ?
                """, FAILED, "Processing was interrupted by a server restart. Run it again.", PROCESSING);
        if (stuck > 0) {
            log.info("Marked {} interrupted syllabus processing run(s) as FAILED", stuck);
        }
    }

    private void setStatus(Long syllabusId, String status, String message) {
        jdbcTemplate.update(
                "UPDATE syllabus SET processing_status = ?, processing_message = ? WHERE id = ?",
                status, message, syllabusId);
    }
}
