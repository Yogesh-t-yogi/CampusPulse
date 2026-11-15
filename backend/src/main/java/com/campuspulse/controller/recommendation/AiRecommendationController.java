
package com.campuspulse.controller.recommendation;

import com.campuspulse.dto.recommendation.AiRecommendation;
import com.campuspulse.service.CurrentUserService;
import com.campuspulse.service.analysis.AnalysisResultService;
import com.campuspulse.service.job.JobPostingService;
import com.campuspulse.service.syllabus.SyllabusService;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/recommendations")
public class AiRecommendationController {

    private final AnalysisResultService analysisResultService;
    private final SyllabusService syllabusService;
    private final CurrentUserService currentUserService;
    private final JobPostingService jobPostingService;

    public AiRecommendationController(
            AnalysisResultService analysisResultService,
            SyllabusService syllabusService,
            CurrentUserService currentUserService,
            JobPostingService jobPostingService) {
        this.analysisResultService = analysisResultService;
        this.syllabusService = syllabusService;
        this.currentUserService = currentUserService;
        this.jobPostingService = jobPostingService;
    }

    // GENERATE RECOMMENDATIONS FOR THE USER'S SYLLABUS
    @GetMapping("/jobs/{jobId}/syllabus/{syllabusId}")
    public List<AiRecommendation> generateRecommendations(
            @PathVariable Long jobId,
            @PathVariable Long syllabusId,
            @RequestParam(defaultValue = "false") boolean refresh) {

        // Verify syllabus ownership before generating recommendations
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
        jobPostingService.requireAccessibleJob(jobId, userId);

        // Saved result unless refresh=true (avoids a new LLM call per page view).
        return analysisResultService.getRecommendations(jobId, syllabusId, refresh);
    }
}
