
package com.campuspulse.controller.matching;

import com.campuspulse.dto.matching.SemanticSkillMatch;
import com.campuspulse.dto.matching.SemanticGapReport;
import com.campuspulse.service.CurrentUserService;
import com.campuspulse.service.analysis.AnalysisResultService;
import com.campuspulse.service.analysis.MultiRoleReportService;
import com.campuspulse.service.matching.SemanticMatchingService;
import com.campuspulse.service.job.JobPostingService;
import com.campuspulse.service.syllabus.SyllabusService;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/matching/semantic")
public class SemanticMatchingController {

    private final SemanticMatchingService semanticMatchingService;
    private final SyllabusService syllabusService;
    private final CurrentUserService currentUserService;
    private final JobPostingService jobPostingService;
    private final AnalysisResultService analysisResultService;
    private final MultiRoleReportService multiRoleReportService;

    public SemanticMatchingController(
            SemanticMatchingService semanticMatchingService,
            SyllabusService syllabusService,
            CurrentUserService currentUserService,
            JobPostingService jobPostingService,
            AnalysisResultService analysisResultService,
            MultiRoleReportService multiRoleReportService) {

        this.semanticMatchingService = semanticMatchingService;
        this.syllabusService = syllabusService;
        this.currentUserService = currentUserService;
        this.jobPostingService = jobPostingService;
        this.analysisResultService = analysisResultService;
        this.multiRoleReportService = multiRoleReportService;
    }

    // SEMANTIC SKILL MATCHING
    @GetMapping("/jobs/{jobId}/syllabus/{syllabusId}")
    public List<SemanticSkillMatch> matchSkills(
            @PathVariable Long jobId,
            @PathVariable Long syllabusId) {

        verifySyllabusOwnership(syllabusId);
        verifyJobAccess(jobId);

        return semanticMatchingService.matchSkills(
                jobId,
                syllabusId
        );
    }

    // SEMANTIC GAP REPORT
    @GetMapping("/report/jobs/{jobId}/syllabus/{syllabusId}")
    public SemanticGapReport generateGapReport(
            @PathVariable Long jobId,
            @PathVariable Long syllabusId,
            @RequestParam(defaultValue = "false") boolean refresh) {

        verifySyllabusOwnership(syllabusId);
        verifyJobAccess(jobId);

        // Saved result unless refresh=true.
        return analysisResultService.getReport(jobId, syllabusId, refresh);
    }

    // ONE SYLLABUS VS SEVERAL JOBS (max 10), e.g. ?syllabusId=3&jobIds=1,4,7
    @GetMapping("/report/multi")
    public MultiRoleReportService.MultiRoleReport multiRoleReport(
            @RequestParam Long syllabusId,
            @RequestParam List<Long> jobIds) {

        verifySyllabusOwnership(syllabusId);
        return multiRoleReportService.build(
                syllabusId, jobIds, currentUserService.getCurrentUser().getId());
    }

    // VERIFY THAT THE CURRENT USER OWNS THE SYLLABUS
    private void verifySyllabusOwnership(Long syllabusId) {

        Long userId = currentUserService.getCurrentUser().getId();

        boolean ownsSyllabus = syllabusService
                .getSyllabusById(syllabusId, userId)
                .isPresent();

        if (!ownsSyllabus) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Syllabus not found"
            );
        }
    }

    private void verifyJobAccess(Long jobId) {
        Long userId = currentUserService.getCurrentUser().getId();
        jobPostingService.requireAccessibleJob(jobId, userId);
    }
}
