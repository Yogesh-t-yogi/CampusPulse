package com.campuspulse.controller.ai;

import com.campuspulse.entity.syllabus.Syllabus;
import com.campuspulse.service.CurrentUserService;
import com.campuspulse.service.ai.SyllabusProcessingService;
import com.campuspulse.service.syllabus.SyllabusService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api/ai/syllabus")
public class SyllabusAiController {

    private final SyllabusService syllabusService;
    private final SyllabusProcessingService syllabusProcessingService;
    private final CurrentUserService currentUserService;

    public SyllabusAiController(
            SyllabusService syllabusService,
            SyllabusProcessingService syllabusProcessingService,
            CurrentUserService currentUserService) {
        this.syllabusService = syllabusService;
        this.syllabusProcessingService = syllabusProcessingService;
        this.currentUserService = currentUserService;
    }

    // START AI PROCESSING (structure + topic indexing) IN THE BACKGROUND.
    // Returns 202 at once; poll GET /api/ai/syllabus/{id}/status.
    @PostMapping("/{id}/save")
    public ResponseEntity<Map<String, Object>> startProcessing(@PathVariable Long id) {
        Syllabus syllabus = requireOwnSyllabus(id);

        if (syllabus.getExtractedText() == null
                || syllabus.getExtractedText().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "No extracted syllabus text found.");
        }

        if (!syllabusProcessingService.markProcessing(id)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "This syllabus is already being processed.");
        }

        syllabusProcessingService.processInBackground(id, syllabus.getExtractedText());

        return ResponseEntity.accepted().body(Map.of(
                "syllabusId", id,
                "status", SyllabusProcessingService.PROCESSING,
                "message", "Processing started."));
    }

    @GetMapping("/{id}/status")
    public Map<String, Object> getStatus(@PathVariable Long id) {
        requireOwnSyllabus(id);
        return syllabusProcessingService.getStatus(id);
    }

    private Syllabus requireOwnSyllabus(Long id) {
        Long userId = currentUserService.getCurrentUser().getId();
        return syllabusService.getSyllabusById(id, userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Syllabus not found"));
    }
}
