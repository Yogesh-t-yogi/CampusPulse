package com.campuspulse.service.analysis;

import com.campuspulse.dto.matching.SemanticGapReport;
import com.campuspulse.dto.recommendation.AiRecommendation;
import com.campuspulse.service.matching.SemanticMatchingService;
import com.campuspulse.service.recommendation.AiRecommendationService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Saves the latest gap report and AI recommendations for each syllabus/job pair,
 * so pages read the same result instead of recomputing (and re-calling the LLM).
 * Saved results are cleared when the syllabus or the job's skills change.
 * Callers must check access to the syllabus and job first.
 */
@Service
public class AnalysisResultService {

    /** Bump when matching rules change so saved results are recomputed. 2 = hybrid matching. */
    static final int ALGO_VERSION = 2;

    private static final Logger log = LoggerFactory.getLogger(AnalysisResultService.class);

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final SemanticMatchingService semanticMatchingService;
    private final AiRecommendationService aiRecommendationService;

    public AnalysisResultService(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            SemanticMatchingService semanticMatchingService,
            AiRecommendationService aiRecommendationService) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.semanticMatchingService = semanticMatchingService;
        this.aiRecommendationService = aiRecommendationService;
    }

    public SemanticGapReport getReport(Long jobId, Long syllabusId, boolean refresh) {
        if (!refresh) {
            String saved = readColumn("report_json", jobId, syllabusId);
            if (saved != null) {
                try {
                    return objectMapper.readValue(saved, SemanticGapReport.class);
                } catch (JsonProcessingException e) {
                    log.warn("Ignoring unreadable saved report for job {} syllabus {}", jobId, syllabusId);
                }
            }
        }

        SemanticGapReport report = semanticMatchingService.generateGapReport(jobId, syllabusId);
        // A new report makes old recommendations stale, so they are cleared.
        jdbcTemplate.update("""
                INSERT INTO analysis_result
                    (syllabus_id, job_id, report_json, recommendations_json, algo_version, updated_at)
                VALUES (?, ?, ?, NULL, ?, now())
                ON CONFLICT (syllabus_id, job_id) DO UPDATE
                    SET report_json = EXCLUDED.report_json,
                        recommendations_json = NULL,
                        algo_version = EXCLUDED.algo_version,
                        updated_at = now()
                """, syllabusId, jobId, toJson(report), ALGO_VERSION);
        return report;
    }

    public List<AiRecommendation> getRecommendations(Long jobId, Long syllabusId, boolean refresh) {
        if (!refresh) {
            String saved = readColumn("recommendations_json", jobId, syllabusId);
            if (saved != null) {
                try {
                    return objectMapper.readValue(saved, new TypeReference<List<AiRecommendation>>() {});
                } catch (JsonProcessingException e) {
                    log.warn("Ignoring unreadable saved recommendations for job {} syllabus {}", jobId, syllabusId);
                }
            }
        }

        SemanticGapReport report = getReport(jobId, syllabusId, false);
        List<AiRecommendation> recommendations = aiRecommendationService.generateRecommendations(report);
        jdbcTemplate.update("""
                UPDATE analysis_result
                SET recommendations_json = ?, updated_at = now()
                WHERE syllabus_id = ? AND job_id = ?
                """, toJson(recommendations), syllabusId, jobId);
        return recommendations;
    }

    public void clearForSyllabus(Long syllabusId) {
        jdbcTemplate.update("DELETE FROM analysis_result WHERE syllabus_id = ?", syllabusId);
    }

    public void clearForJob(Long jobId) {
        jdbcTemplate.update("DELETE FROM analysis_result WHERE job_id = ?", jobId);
    }

    private String readColumn(String column, Long jobId, Long syllabusId) {
        List<String> rows = jdbcTemplate.queryForList(
                "SELECT " + column + " FROM analysis_result"
                        + " WHERE syllabus_id = ? AND job_id = ? AND algo_version = ?",
                String.class, syllabusId, jobId, ALGO_VERSION);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Could not save analysis result.", e);
        }
    }
}
