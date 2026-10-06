package com.campuspulse.controller.ai;

import com.campuspulse.dto.ai.AiSyllabusResponse;
import com.campuspulse.entity.syllabus.Syllabus;
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

    public SyllabusAiController(
            SyllabusService syllabusService,
            SyllabusAiService syllabusAiService,
            SyllabusPersistenceService syllabusPersistenceService) {

        this.syllabusService = syllabusService;
        this.syllabusAiService = syllabusAiService;
        this.syllabusPersistenceService =
                syllabusPersistenceService;
    }

    @GetMapping("/{id}/structure")
    public ResponseEntity<AiSyllabusResponse> structureSyllabus(
            @PathVariable Long id) {

        Syllabus syllabus = syllabusService
                .getSyllabusById(id)
                .orElse(null);

        if (syllabus == null) {
            return ResponseEntity.notFound().build();
        }

        if (syllabus.getExtractedText() == null ||
                syllabus.getExtractedText().isBlank()) {

            return ResponseEntity.badRequest().build();
        }

        AiSyllabusResponse structuredResult =
                syllabusAiService.structureSyllabus(
                        syllabus.getExtractedText()
                );

        return ResponseEntity.ok(structuredResult);
    }

    @PostMapping("/{id}/save")
    public ResponseEntity<String> saveStructuredSyllabus(
            @PathVariable Long id) {

        Syllabus syllabus = syllabusService
                .getSyllabusById(id)
                .orElse(null);

        if (syllabus == null) {
            return ResponseEntity.notFound().build();
        }

        if (syllabus.getExtractedText() == null ||
                syllabus.getExtractedText().isBlank()) {

            return ResponseEntity.badRequest()
                    .body("No extracted syllabus text found.");
        }

        // Step 1: Send syllabus text to AI
        AiSyllabusResponse aiResponse =
                syllabusAiService.structureSyllabus(
                        syllabus.getExtractedText()
                );

        // Step 2: Convert AI response to entities
        // and save everything to PostgreSQL
        syllabusPersistenceService.saveAiStructure(
                syllabus,
                aiResponse
        );

        return ResponseEntity.ok(
                "AI syllabus structure saved successfully."
        );
    }
}