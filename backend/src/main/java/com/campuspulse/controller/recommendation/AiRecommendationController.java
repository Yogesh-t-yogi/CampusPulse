package com.campuspulse.controller.recommendation;

import com.campuspulse.dto.matching.SemanticGapReport;
import com.campuspulse.dto.recommendation.AiRecommendation;
import com.campuspulse.service.matching.SemanticMatchingService;
import com.campuspulse.service.recommendation.AiRecommendationService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/recommendations")
public class AiRecommendationController {

    private final SemanticMatchingService semanticMatchingService;
    private final AiRecommendationService aiRecommendationService;

    public AiRecommendationController(
            SemanticMatchingService semanticMatchingService,
            AiRecommendationService aiRecommendationService) {

        this.semanticMatchingService =
                semanticMatchingService;

        this.aiRecommendationService =
                aiRecommendationService;
    }

    @GetMapping("/jobs/{jobId}/syllabus/{syllabusId}")
    public List<AiRecommendation> generateRecommendations(
            @PathVariable Long jobId,
            @PathVariable Long syllabusId) {

        SemanticGapReport gapReport =
                semanticMatchingService.generateGapReport(
                        jobId,
                        syllabusId
                );

        return aiRecommendationService
                .generateRecommendations(gapReport);
    }
}