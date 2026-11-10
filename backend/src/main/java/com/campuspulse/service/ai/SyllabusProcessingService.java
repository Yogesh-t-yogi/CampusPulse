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

    public SyllabusProcessingService(
            JdbcTemplate jdbcTemplate,
            SyllabusAiService syllabusAiService,
            SyllabusPersistenceService syllabusPersistenceService,
            AnalysisResultService analysisResultService) {
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

    @Async
    public void processInBackground(Long syllabusId, String extractedText) {
        try {
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
        return Map.of(
                "syllabusId", syllabusId,
                "status", status,
                "message", row.get("message") == null ? "" : row.get("message"),
                "topicCount", topicCount);
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
