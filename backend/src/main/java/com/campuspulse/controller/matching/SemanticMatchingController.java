package com.campuspulse.controller.matching;

import com.campuspulse.dto.matching.SemanticSkillMatch;
import com.campuspulse.service.matching.SemanticMatchingService;
import org.springframework.web.bind.annotation.*;
import com.campuspulse.dto.matching.SemanticGapReport;

import java.util.List;

@RestController
@RequestMapping("/api/matching/semantic")
public class SemanticMatchingController {

    private final SemanticMatchingService semanticMatchingService;

    public SemanticMatchingController(
            SemanticMatchingService semanticMatchingService) {

        this.semanticMatchingService =
                semanticMatchingService;
    }

    @GetMapping("/jobs/{jobId}/syllabus/{syllabusId}")
    public List<SemanticSkillMatch> matchSkills(
            @PathVariable Long jobId,
            @PathVariable Long syllabusId) {

        return semanticMatchingService.matchSkills(
                jobId,
                syllabusId
        );
    }

    @GetMapping("/report/jobs/{jobId}/syllabus/{syllabusId}")
    public SemanticGapReport generateGapReport(
            @PathVariable Long jobId,
            @PathVariable Long syllabusId) {

        return semanticMatchingService.generateGapReport(
                jobId,
                syllabusId
        );
    }
}