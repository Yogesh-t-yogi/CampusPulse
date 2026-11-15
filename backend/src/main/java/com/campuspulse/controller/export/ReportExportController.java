package com.campuspulse.controller.export;

import com.campuspulse.entity.job.JobPosting;
import com.campuspulse.entity.syllabus.Syllabus;
import com.campuspulse.service.CurrentUserService;
import com.campuspulse.service.export.ReportExportService;
import com.campuspulse.service.job.JobPostingService;
import com.campuspulse.service.syllabus.SyllabusService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Download a syllabus × job report as PDF or Excel. Same access rules as the report itself. */
@RestController
@RequestMapping("/api/export/report")
public class ReportExportController {

    private static final MediaType XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final ReportExportService exports;
    private final SyllabusService syllabusService;
    private final JobPostingService jobPostingService;
    private final CurrentUserService currentUserService;

    public ReportExportController(ReportExportService exports, SyllabusService syllabusService,
                                  JobPostingService jobPostingService, CurrentUserService currentUserService) {
        this.exports = exports;
        this.syllabusService = syllabusService;
        this.jobPostingService = jobPostingService;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/pdf")
    public ResponseEntity<byte[]> pdf(@RequestParam Long syllabusId, @RequestParam Long jobId) {
        Syllabus syllabus = syllabus(syllabusId);
        JobPosting job = job(jobId);
        return file(exports.pdf(syllabus, job), MediaType.APPLICATION_PDF, fileName(syllabus, job, "pdf"));
    }

    @GetMapping("/excel")
    public ResponseEntity<byte[]> excel(@RequestParam Long syllabusId, @RequestParam Long jobId) {
        Syllabus syllabus = syllabus(syllabusId);
        JobPosting job = job(jobId);
        return file(exports.excel(syllabus, job), XLSX, fileName(syllabus, job, "xlsx"));
    }

    private Syllabus syllabus(Long id) {
        Long userId = currentUserService.getCurrentUser().getId();
        return syllabusService.getSyllabusById(id, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Syllabus not found"));
    }

    private JobPosting job(Long id) {
        return jobPostingService.requireAccessibleJob(id, currentUserService.getCurrentUser().getId());
    }

    static String fileName(Syllabus syllabus, JobPosting job, String ext) {
        String base = ("gap-report-" + syllabus.getName() + "-" + job.getTitle())
                .replaceAll("[^A-Za-z0-9]+", "-").replaceAll("(^-|-$)", "").toLowerCase();
        if (base.length() > 80) base = base.substring(0, 80);
        return base + "." + ext;
    }

    private static ResponseEntity<byte[]> file(byte[] body, MediaType type, String name) {
        return ResponseEntity.ok()
                .contentType(type)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(name).build().toString())
                .body(body);
    }
}
