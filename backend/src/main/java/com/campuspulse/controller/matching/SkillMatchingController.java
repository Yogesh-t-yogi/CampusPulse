
package com.campuspulse.controller.matching;

import com.campuspulse.dto.matching.SkillMatchResponse;
import com.campuspulse.service.CurrentUserService;
import com.campuspulse.service.matching.SkillMatchingService;
import com.campuspulse.service.syllabus.SyllabusService;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/matching")
public class SkillMatchingController {

    private final SkillMatchingService skillMatchingService;
    private final SyllabusService syllabusService;
    private final CurrentUserService currentUserService;

    public SkillMatchingController(
            SkillMatchingService skillMatchingService,
            SyllabusService syllabusService,
            CurrentUserService currentUserService) {

        this.skillMatchingService = skillMatchingService;
        this.syllabusService = syllabusService;
        this.currentUserService = currentUserService;
    }

    // MATCH JOB SKILLS AGAINST THE USER'S SYLLABUS
    @GetMapping("/jobs/{jobId}/syllabus/{syllabusId}")
    public SkillMatchResponse matchSkills(
            @PathVariable Long jobId,
            @PathVariable Long syllabusId) {

        verifySyllabusOwnership(syllabusId);

        return skillMatchingService.matchSkills(
                jobId,
                syllabusId
        );
    }

    // VERIFY SYLLABUS OWNERSHIP
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
