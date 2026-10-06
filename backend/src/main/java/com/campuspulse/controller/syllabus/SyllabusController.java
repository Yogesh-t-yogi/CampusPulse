package com.campuspulse.controller.syllabus;

import com.campuspulse.dto.syllabus.FullSyllabusResponse;
import com.campuspulse.entity.syllabus.Syllabus;
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

    public SyllabusController(
            SyllabusService syllabusService,
            PdfTextExtractionService pdfTextExtractionService) {

        this.syllabusService = syllabusService;
        this.pdfTextExtractionService = pdfTextExtractionService;
    }

    // =========================================================
    // CREATE SYLLABUS
    // =========================================================

    @PostMapping
    public ResponseEntity<Syllabus> createSyllabus(
            @RequestBody Syllabus syllabus) {

        Syllabus savedSyllabus =
                syllabusService.createSyllabus(syllabus);

        return ResponseEntity.ok(savedSyllabus);
    }

    // =========================================================
    // GET ALL SYLLABI
    // =========================================================

    @GetMapping
    public ResponseEntity<List<Syllabus>> getAllSyllabi() {

        return ResponseEntity.ok(
                syllabusService.getAllSyllabi()
        );
    }

    // =========================================================
    // GET SYLLABUS BY ID
    // =========================================================

    @GetMapping("/{id}")
    public ResponseEntity<Syllabus> getSyllabusById(
            @PathVariable Long id) {

        return syllabusService.getSyllabusById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // =========================================================
    // GET COMPLETE SYLLABUS HIERARCHY
    // =========================================================

        @GetMapping("/{id}/full")
        public ResponseEntity<FullSyllabusResponse> getFullSyllabus(
                @PathVariable Long id) {

        return syllabusService
                .getFullSyllabusById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
        }

    // =========================================================
    // UPLOAD PDF + EXTRACT TEXT + SAVE TO DATABASE
    // =========================================================

    @PostMapping(
            value = "/upload",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<Map<String, Object>> uploadPdf(
            @RequestParam("file") MultipartFile file) {

        try {

            // 1. Extract text from PDF
            String extractedText =
                    pdfTextExtractionService.extractText(file);

            // 2. Create Syllabus object
            Syllabus syllabus = new Syllabus();

            String originalFileName =
                    file.getOriginalFilename();

            // 3. Store original PDF filename
            syllabus.setFileName(originalFileName);

            // 4. Create syllabus name from filename
            syllabus.setName(
                    originalFileName != null
                            ? originalFileName.replace(".pdf", "")
                            : "Uploaded Syllabus"
            );

            // 5. Store extracted PDF text
            syllabus.setExtractedText(extractedText);

            // 6. Save syllabus to PostgreSQL
            Syllabus savedSyllabus =
                    syllabusService.createSyllabus(syllabus);

            // 7. Return success response
            return ResponseEntity.ok(
                    Map.of(
                            "id",
                            savedSyllabus.getId(),

                            "name",
                            savedSyllabus.getName(),

                            "fileName",
                            savedSyllabus.getFileName(),

                            "message",
                            "PDF uploaded, text extracted and syllabus saved successfully",

                            "textLength",
                            extractedText.length()
                    )
            );

        } catch (IllegalArgumentException e) {

            // Invalid file / non-PDF / empty file
            return ResponseEntity.badRequest().body(
                    Map.of(
                            "error",
                            e.getMessage()
                    )
            );

        } catch (IOException e) {

            // PDF reading/extraction error
            return ResponseEntity.internalServerError().body(
                    Map.of(
                            "error",
                            "Unable to read PDF file"
                    )
            );
        }
    }
}