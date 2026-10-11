
package com.campuspulse.controller.syllabus;

import com.campuspulse.dto.syllabus.FullSyllabusResponse;
import com.campuspulse.entity.syllabus.Syllabus;
import com.campuspulse.service.CurrentUserService;
import com.campuspulse.service.syllabus.PdfTextExtractionService;
import com.campuspulse.service.syllabus.SyllabusService;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/syllabus")
public class SyllabusController {

    private final SyllabusService syllabusService;
    private final PdfTextExtractionService pdfTextExtractionService;
    private final CurrentUserService currentUserService;

    public SyllabusController(
            SyllabusService syllabusService,
            PdfTextExtractionService pdfTextExtractionService,
            CurrentUserService currentUserService) {

        this.syllabusService = syllabusService;
        this.pdfTextExtractionService = pdfTextExtractionService;
        this.currentUserService = currentUserService;
    }

    // CREATE SYLLABUS
    @PostMapping
    public ResponseEntity<Syllabus> createSyllabus(
            @RequestBody Syllabus syllabus) {

        syllabus.setOwner(currentUserService.getCurrentUser());

        Syllabus savedSyllabus =
                syllabusService.createSyllabus(syllabus);

        return ResponseEntity.ok(savedSyllabus);
    }

    // GET ALL SYLLABI BELONGING TO THE CURRENT USER
    @GetMapping
    public ResponseEntity<List<Syllabus>> getAllSyllabi() {

        Long userId = currentUserService.getCurrentUser().getId();

        return ResponseEntity.ok(
                syllabusService.getAllSyllabi(userId)
        );
    }

    // GET SYLLABUS BY ID, ONLY IF OWNED BY CURRENT USER
    @GetMapping("/{id}")
    public ResponseEntity<Syllabus> getSyllabusById(
            @PathVariable Long id) {

        Long userId = currentUserService.getCurrentUser().getId();

        return syllabusService.getSyllabusById(id, userId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // GET COMPLETE SYLLABUS HIERARCHY
    @GetMapping("/{id}/full")
    public ResponseEntity<FullSyllabusResponse> getFullSyllabus(
            @PathVariable Long id) {

        Long userId = currentUserService.getCurrentUser().getId();

        return syllabusService
                .getFullSyllabusById(id, userId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // UPLOAD PDF, EXTRACT TEXT, AND SAVE
    @PostMapping(
            value = "/upload",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<Map<String, Object>> uploadPdf(
            @RequestParam("file") MultipartFile file) {

        try {
            String extractedText =
                    pdfTextExtractionService.extractText(file);

            String originalFileName = file.getOriginalFilename();

            Syllabus syllabus = new Syllabus();

            syllabus.setFileName(originalFileName);
            syllabus.setName(
                    originalFileName != null
                            ? originalFileName.replace(".pdf", "")
                            : "Uploaded Syllabus"
            );
            syllabus.setExtractedText(extractedText);

            // Assign the authenticated user as owner
            syllabus.setOwner(currentUserService.getCurrentUser());

            Syllabus savedSyllabus =
                    syllabusService.createSyllabus(syllabus);

            return ResponseEntity.ok(
                    Map.of(
                            "id", savedSyllabus.getId(),
                            "name", savedSyllabus.getName(),
                            "fileName",
                            savedSyllabus.getFileName() == null
                                    ? ""
                                    : savedSyllabus.getFileName(),
                            "message",
                            "PDF uploaded, text extracted and syllabus saved successfully",
                            "textLength", extractedText.length()
                    )
            );

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(
                    Map.of("error", e.getMessage())
            );

        } catch (IOException e) {
            return ResponseEntity.internalServerError().body(
                    Map.of("error", "Unable to read PDF file")
            );
        }
    }
}
