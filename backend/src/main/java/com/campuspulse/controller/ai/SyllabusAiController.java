
package com.campuspulse.controller.ai;

import com.campuspulse.dto.ai.AiSyllabusResponse;
import com.campuspulse.entity.syllabus.Syllabus;
import com.campuspulse.service.CurrentUserService;
import com.campuspulse.service.analysis.AnalysisResultService;
import com.campuspulse.service.ai.SyllabusAiService;
import com.campuspulse.service.ai.SyllabusPersistenceService;
import com.campuspulse.service.syllabus.SyllabusService;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai/syllabus")
public class SyllabusAiController {

    private final SyllabusService syllabusService;
    private final SyllabusAiService syllabusAiService;
    private final SyllabusPersistenceService syllabusPersistenceService;
    private final CurrentUserService currentUserService;
    private final AnalysisResultService analysisResultService;

    public SyllabusAiController(
            SyllabusService syllabusService,
            SyllabusAiService syllabusAiService,
            SyllabusPersistenceService syllabusPersistenceService,
            CurrentUserService currentUserService,
            AnalysisResultService analysisResultService) {

        this.syllabusService = syllabusService;
        this.syllabusAiService = syllabusAiService;
        this.syllabusPersistenceService = syllabusPersistenceService;
        this.currentUserService = currentUserService;
        this.analysisResultService = analysisResultService;
    }

    // STRUCTURE SYLLABUS USING AI
    @GetMapping("/{id}/structure")
    public ResponseEntity<AiSyllabusResponse> structureSyllabus(
            @PathVariable Long id) {

        Long userId = currentUserService.getCurrentUser().getId();

        Syllabus syllabus = syllabusService
                .getSyllabusById(id, userId)
                .orElse(null);

        if (syllabus == null) {
            return ResponseEntity.notFound().build();
        }

        if (syllabus.getExtractedText() == null
                || syllabus.getExtractedText().isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        AiSyllabusResponse structuredResult =
                syllabusAiService.structureSyllabus(
                        syllabus.getExtractedText()
                );

        return ResponseEntity.ok(structuredResult);
    }

    // GENERATE AND SAVE AI SYLLABUS STRUCTURE
    @PostMapping("/{id}/save")
    public ResponseEntity<Map<String, Object>> saveStructuredSyllabus(
            @PathVariable Long id) {

        Long userId = currentUserService.getCurrentUser().getId();

        Syllabus syllabus = syllabusService
                .getSyllabusById(id, userId)
                .orElse(null);

        if (syllabus == null) {
            return ResponseEntity.notFound().build();
        }

        if (syllabus.getExtractedText() == null
                || syllabus.getExtractedText().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of(
                            "error",
                            "No extracted syllabus text found."));
        }

        // Step 1: Send syllabus text to AI
        AiSyllabusResponse aiResponse =
                syllabusAiService.structureSyllabus(
                        syllabus.getExtractedText()
                );

        // Step 2: Persist the generated structure
        Syllabus savedSyllabus = syllabusPersistenceService.saveAiStructure(
                syllabus,
                aiResponse
        );
        SyllabusPersistenceService.EmbeddingGenerationResult embeddings =
                syllabusPersistenceService.generateEmbeddings(savedSyllabus);
        // Curriculum changed: old saved reports are no longer valid.
        analysisResultService.clearForSyllabus(savedSyllabus.getId());

        return ResponseEntity.ok(Map.of(
                "syllabusId", savedSyllabus.getId(),
                "curriculumSaved", true,
                "embeddingsGenerated", embeddings.generated(),
                "embeddingFailures", embeddings.failed(),
                "embeddingWarning", embeddings.warning(),
                "message", embeddings.failed() == 0
                        ? "AI syllabus structure saved successfully."
                        : "Curriculum saved, but embedding generation needs attention."
        ));
    }
}
