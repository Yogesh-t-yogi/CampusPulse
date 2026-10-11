
package com.campuspulse.controller.ai;

import com.campuspulse.dto.ai.AiSyllabusResponse;
import com.campuspulse.entity.syllabus.Syllabus;
import com.campuspulse.service.CurrentUserService;
import com.campuspulse.service.ai.SyllabusAiService;
import com.campuspulse.service.ai.SyllabusPersistenceService;
import com.campuspulse.service.syllabus.SyllabusService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai/syllabus")
public class SyllabusAiController {

    private final SyllabusService syllabusService;
    private final SyllabusAiService syllabusAiService;
    private final SyllabusPersistenceService syllabusPersistenceService;
    private final CurrentUserService currentUserService;

    public SyllabusAiController(
            SyllabusService syllabusService,
            SyllabusAiService syllabusAiService,
            SyllabusPersistenceService syllabusPersistenceService,
            CurrentUserService currentUserService) {

        this.syllabusService = syllabusService;
        this.syllabusAiService = syllabusAiService;
        this.syllabusPersistenceService = syllabusPersistenceService;
        this.currentUserService = currentUserService;
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
    public ResponseEntity<String> saveStructuredSyllabus(
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
                    .body("No extracted syllabus text found.");
        }

        // Step 1: Send syllabus text to AI
        AiSyllabusResponse aiResponse =
                syllabusAiService.structureSyllabus(
                        syllabus.getExtractedText()
                );

        // Step 2: Persist the generated structure
        syllabusPersistenceService.saveAiStructure(
                syllabus,
                aiResponse
        );

        return ResponseEntity.ok(
                "AI syllabus structure saved successfully."
        );
    }
}
