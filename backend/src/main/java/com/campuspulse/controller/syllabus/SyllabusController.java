package com.campuspulse.controller.syllabus;

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

    @PostMapping
    public ResponseEntity<Syllabus> createSyllabus(
            @RequestBody Syllabus syllabus) {

        Syllabus savedSyllabus =
                syllabusService.createSyllabus(syllabus);

        return ResponseEntity.ok(savedSyllabus);
    }

    @GetMapping
    public ResponseEntity<List<Syllabus>> getAllSyllabi() {

        return ResponseEntity.ok(
                syllabusService.getAllSyllabi()
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<Syllabus> getSyllabusById(
            @PathVariable Long id) {

        return syllabusService.getSyllabusById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping(
            value = "/upload",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<Map<String, Object>> uploadPdf(
            @RequestParam("file") MultipartFile file) {

        try {

            String extractedText =
                    pdfTextExtractionService.extractText(file);

            return ResponseEntity.ok(
                    Map.of(
                            "fileName",
                            file.getOriginalFilename(),

                            "message",
                            "PDF uploaded and text extracted successfully",

                            "text",
                            extractedText
                    )
            );

        } catch (IllegalArgumentException e) {

            return ResponseEntity.badRequest().body(
                    Map.of(
                            "error",
                            e.getMessage()
                    )
            );

        } catch (IOException e) {

            return ResponseEntity.internalServerError().body(
                    Map.of(
                            "error",
                            "Unable to read PDF file"
                    )
            );
        }
    }
}