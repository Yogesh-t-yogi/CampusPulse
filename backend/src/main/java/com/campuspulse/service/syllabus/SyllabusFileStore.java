package com.campuspulse.service.syllabus;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/** Keeps an uploaded PDF while some of its pages wait to be read by AI (table syllabus_file). */
@Service
public class SyllabusFileStore {

    /** What is stored for one syllabus. */
    public record StoredFile(byte[] bytes, String sha256, List<String> pageTexts, List<Integer> pendingPages) {
    }

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public SyllabusFileStore(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public void save(Long syllabusId, PdfTextExtractionService.PdfAnalysis pdf) {
        jdbc.update("""
                INSERT INTO syllabus_file (syllabus_id, sha256, page_count, content, page_texts, pending_pages)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (syllabus_id) DO UPDATE SET sha256 = EXCLUDED.sha256, page_count = EXCLUDED.page_count,
                    content = EXCLUDED.content, page_texts = EXCLUDED.page_texts,
                    pending_pages = EXCLUDED.pending_pages, page_summary = NULL
                """, syllabusId, pdf.sha256(), pdf.pageCount(), pdf.bytes(),
                json(pdf.pageTexts()), json(pdf.pagesToRead()));
    }

    /** The stored file if pages are still waiting to be read, else null. */
    public StoredFile pending(Long syllabusId) {
        List<StoredFile> rows = jdbc.query("""
                SELECT content, sha256, page_texts, pending_pages FROM syllabus_file WHERE syllabus_id = ?
                """, (rs, i) -> new StoredFile(rs.getBytes("content"), rs.getString("sha256"),
                read(rs.getString("page_texts"), new TypeReference<List<String>>() {}),
                read(rs.getString("pending_pages"), new TypeReference<List<Integer>>() {})), syllabusId);
        if (rows.isEmpty() || rows.get(0).pendingPages().isEmpty()) return null;
        return rows.get(0);
    }

    public boolean hasPending(Long syllabusId) {
        Integer n = jdbc.queryForObject("""
                SELECT count(*) FROM syllabus_file WHERE syllabus_id = ? AND pending_pages <> '[]'
                """, Integer.class, syllabusId);
        return n != null && n > 0;
    }

    /**
     * Saves how each page was read. Pages that could not be read stay waiting, so "Re-process"
     * tries them again (e.g. after fixing an AI key).
     */
    public void finish(Long syllabusId, Map<String, Object> summary, List<Integer> stillWaiting) {
        jdbc.update("UPDATE syllabus_file SET pending_pages = ?, page_summary = ? WHERE syllabus_id = ?",
                json(stillWaiting), json(summary), syllabusId);
    }

    /** How pages were read (null if this syllabus never needed AI reading). */
    public Map<String, Object> summary(Long syllabusId) {
        List<String> rows = jdbc.queryForList(
                "SELECT page_summary FROM syllabus_file WHERE syllabus_id = ? AND page_summary IS NOT NULL",
                String.class, syllabusId);
        return rows.isEmpty() ? null : read(rows.get(0), new TypeReference<Map<String, Object>>() {});
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private <T> T read(String json, TypeReference<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored PDF data is unreadable.", e);
        }
    }
}
