
package com.campuspulse.controller.recommendation;

import com.campuspulse.dto.matching.SemanticGapReport;
import com.campuspulse.dto.recommendation.AiRecommendation;
import com.campuspulse.service.CurrentUserService;
import com.campuspulse.service.matching.SemanticMatchingService;
import com.campuspulse.service.recommendation.AiRecommendationService;
import com.campuspulse.service.syllabus.SyllabusService;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/recommendations")
public class AiRecommendationController {

    private final SemanticMatchingService semanticMatchingService;
    private final AiRecommendationService aiRecommendationService;
    private final SyllabusService syllabusService;
    private final CurrentUserService currentUserService;

    public AiRecommendationController(
            SemanticMatchingService semanticMatchingService,
            AiRecommendationService aiRecommendationService,
            SyllabusService syllabusService,
            CurrentUserService currentUserService) {

        this.semanticMatchingService = semanticMatchingService;
        this.aiRecommendationService = aiRecommendationService;
        this.syllabusService = syllabusService;
        this.currentUserService = currentUserService;
    }

    // GENERATE RECOMMENDATIONS FOR THE USER'S SYLLABUS
    @GetMapping("/jobs/{jobId}/syllabus/{syllabusId}")
    public List<AiRecommendation> generateRecommendations(
            @PathVariable Long jobId,
            @PathVariable Long syllabusId) {

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

        SemanticGapReport gapReport =
                semanticMatchingService.generateGapReport(
                        jobId,
                        syllabusId
                );

        return aiRecommendationService
                .generateRecommendations(gapReport);
    }
}
