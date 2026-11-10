
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

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(SyllabusController.class);

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

    // LIST THE CURRENT USER'S SYLLABI (summary: status, subject and topic counts)
    @GetMapping
    public List<Map<String, Object>> getAllSyllabi() {
        return syllabusService.listSummaries(currentUserService.getCurrentUser().getId());
    }

    // RENAME
    @PutMapping("/{id}")
    public Map<String, Object> rename(@PathVariable Long id, @RequestBody Map<String, String> body) {
        Syllabus syllabus = syllabusService.rename(
                id, currentUserService.getCurrentUser().getId(), body.get("name"));
        return Map.of("id", syllabus.getId(), "name", syllabus.getName());
    }

    // DELETE (with its curriculum, vectors and saved reports)
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        syllabusService.delete(id, currentUserService.getCurrentUser().getId());
        return ResponseEntity.noContent().build();
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
            log.info("Syllabus upload started, size {} bytes", file.getSize());

            // Step 1: Extract PDF text
            String extractedText =
                    pdfTextExtractionService.extractText(file);

            if (extractedText == null || extractedText.isBlank()) {
                return ResponseEntity.badRequest().body(
                        Map.of("error", "No readable text was extracted from the PDF.")
                );
            }

            // Step 2: Create syllabus entity
            String originalFileName = file.getOriginalFilename();

            Syllabus syllabus = new Syllabus();

            syllabus.setFileName(originalFileName);
            syllabus.setName(
                    originalFileName != null
                            ? originalFileName.replaceAll("(?i)\\.pdf$", "")
                            : "Uploaded Syllabus"
            );
            syllabus.setExtractedText(extractedText);
            var owner = currentUserService.getCurrentUser();
            syllabus.setOwner(owner);
            syllabus.setInstitutionId(owner.getInstitutionId());
            syllabus.setDepartmentId(owner.getDepartmentId());

            // Step 3: Save syllabus
            Syllabus savedSyllabus =
                    syllabusService.createSyllabus(syllabus);

            log.info("Syllabus {} saved, {} characters extracted",
                    savedSyllabus.getId(),
                    savedSyllabus.getExtractedText().length());

            return ResponseEntity.ok(
                    Map.of(
                            "id", savedSyllabus.getId(),
                            "name", savedSyllabus.getName(),
                            "fileName", savedSyllabus.getFileName() == null
                                    ? ""
                                    : savedSyllabus.getFileName(),
                            "message", "PDF uploaded and text extracted successfully",
                            "textLength", savedSyllabus.getExtractedText().length()
                    )
            );

        } catch (IllegalArgumentException e) {
            log.warn("Syllabus upload rejected: {}", e.getMessage());

            return ResponseEntity.badRequest().body(
                    Map.of("error", e.getMessage() == null
                            ? "Invalid PDF file"
                            : e.getMessage())
            );

        } catch (IOException e) {
            log.error("Syllabus upload failed to read PDF", e);

            return ResponseEntity.internalServerError().body(
                    Map.of("error", "Unable to read PDF file")
            );
        }
    }

}
