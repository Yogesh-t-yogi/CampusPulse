
package com.campuspulse.controller.syllabus;

import com.campuspulse.dto.syllabus.FullSyllabusResponse;
import com.campuspulse.entity.syllabus.Syllabus;
import com.campuspulse.service.CurrentUserService;
import com.campuspulse.service.syllabus.PdfTextExtractionService;
import com.campuspulse.service.syllabus.SyllabusAccess;
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
    private final com.campuspulse.service.syllabus.SyllabusFileStore fileStore;
    private final com.campuspulse.service.syllabus.ScannedPageReader pageReader;

    public SyllabusController(
            SyllabusService syllabusService,
            PdfTextExtractionService pdfTextExtractionService,
            CurrentUserService currentUserService,
            com.campuspulse.service.syllabus.SyllabusFileStore fileStore,
            com.campuspulse.service.syllabus.ScannedPageReader pageReader) {

        this.syllabusService = syllabusService;
        this.pdfTextExtractionService = pdfTextExtractionService;
        this.currentUserService = currentUserService;
        this.fileStore = fileStore;
        this.pageReader = pageReader;
    }

    // LIST THE CURRENT USER'S SYLLABI (summary: status, subject and topic counts)
    @GetMapping
    public List<Map<String, Object>> getAllSyllabi() {
        return syllabusService.listReadable(currentUserService.getCurrentUser());
    }

    // EDIT DETAILS: name, programId, semester, visibility (PRIVATE / DEPARTMENT)
    @PutMapping("/{id}")
    public Map<String, Object> updateDetails(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Syllabus syllabus = syllabusService.updateDetails(id, body);
        Map<String, Object> view = new java.util.HashMap<>();
        view.put("id", syllabus.getId());
        view.put("name", syllabus.getName());
        view.put("programId", syllabus.getProgramId());
        view.put("semester", syllabus.getSemester());
        view.put("visibility", syllabus.getVisibility());
        return view;
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
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "programId", required = false) Long programId,
            @RequestParam(value = "semester", required = false) Long semester,
            @RequestParam(value = "revisionOf", required = false) Long revisionOf) {

        try {
            log.info("Syllabus upload started, size {} bytes", file.getSize());

            // Step 1: text layer of every page; scanned/garbled pages are read later by AI.
            PdfTextExtractionService.PdfAnalysis pdf = pdfTextExtractionService.analyse(file);
            boolean needsReading = !pdf.pagesToRead().isEmpty();
            if (needsReading && !pageReader.canRead()) {
                if (pdf.knownText().isBlank()) {
                    return ResponseEntity.badRequest().body(Map.of("error",
                            "This PDF is scanned (no selectable text). Add LLAMA_CLOUD_API_KEY (LlamaParse) or "
                                    + "GEMINI_API_KEY to .env so it can be read, then restart the backend."));
                }
                needsReading = false; // use the pages that have text; the rest are skipped
            }
            String extractedText = needsReading ? pdf.knownText()
                    : PdfTextExtractionService.joinPages(pdf.pageTexts(), List.of());

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
            syllabus.setVisibility(SyllabusAccess.defaultVisibility(owner));
            // Students' syllabi belong to their own program; staff pick one.
            Long chosenProgram = "STUDENT".equals(owner.getRole()) && owner.getProgramId() != null
                    ? owner.getProgramId() : programId;
            syllabusService.applyProgram(syllabus, chosenProgram);
            syllabus.setSemester(syllabusService.validSemester(syllabus, semester));
            if (revisionOf != null) {
                // New version of an existing syllabus: same name, program, semester, sharing.
                Syllabus previous = syllabusService.requireManageable(revisionOf);
                syllabus.setName(previous.getName());
                syllabus.setDepartmentId(previous.getDepartmentId());
                syllabus.setProgramId(previous.getProgramId());
                syllabus.setSemester(previous.getSemester());
                syllabus.setVisibility(previous.getVisibility());
                syllabus.setPreviousVersionId(previous.getId());
                syllabus.setVersionNo((previous.getVersionNo() == null ? 1 : previous.getVersionNo()) + 1);
            }

            // Step 3: Save syllabus (and the PDF itself while pages wait for AI reading)
            Syllabus savedSyllabus =
                    syllabusService.createSyllabus(syllabus);
            if (needsReading) {
                fileStore.save(savedSyllabus.getId(), pdf);
            }

            int textLength = savedSyllabus.getExtractedText() == null ? 0 : savedSyllabus.getExtractedText().length();
            log.info("Syllabus {} saved: {} pages, {} characters, {} pages for AI reading",
                    savedSyllabus.getId(), pdf.pageCount(), textLength, needsReading ? pdf.pagesToRead().size() : 0);

            Map<String, Object> body = new java.util.HashMap<>();
            body.put("id", savedSyllabus.getId());
            body.put("name", savedSyllabus.getName());
            body.put("fileName", savedSyllabus.getFileName() == null ? "" : savedSyllabus.getFileName());
            body.put("pageCount", pdf.pageCount());
            body.put("pagesForAi", needsReading ? pdf.pagesToRead().size() : 0);
            body.put("textLength", textLength);
            body.put("message", needsReading
                    ? pdf.pagesToRead().size() + " of " + pdf.pageCount() + " pages are scanned; AI will read them during processing."
                    : "PDF uploaded and text extracted successfully");
            return ResponseEntity.ok(body            );

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
