
package com.campuspulse.controller.matching;

import com.campuspulse.dto.matching.SemanticSkillMatch;
import com.campuspulse.dto.matching.SemanticGapReport;
import com.campuspulse.service.CurrentUserService;
import com.campuspulse.service.matching.SemanticMatchingService;
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

    public SemanticMatchingController(
            SemanticMatchingService semanticMatchingService,
            SyllabusService syllabusService,
            CurrentUserService currentUserService) {

        this.semanticMatchingService = semanticMatchingService;
        this.syllabusService = syllabusService;
        this.currentUserService = currentUserService;
    }

    // SEMANTIC SKILL MATCHING
    @GetMapping("/jobs/{jobId}/syllabus/{syllabusId}")
    public List<SemanticSkillMatch> matchSkills(
            @PathVariable Long jobId,
            @PathVariable Long syllabusId) {

        verifySyllabusOwnership(syllabusId);

        return semanticMatchingService.matchSkills(
                jobId,
                syllabusId
        );
    }

    // SEMANTIC GAP REPORT
    @GetMapping("/report/jobs/{jobId}/syllabus/{syllabusId}")
    public SemanticGapReport generateGapReport(
            @PathVariable Long jobId,
            @PathVariable Long syllabusId) {

        verifySyllabusOwnership(syllabusId);

        return semanticMatchingService.generateGapReport(
                jobId,
                syllabusId
        );
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
}
