package com.campuspulse.controller.matching;

import com.campuspulse.dto.matching.SkillMatchResponse;
import com.campuspulse.service.matching.SkillMatchingService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/matching")
public class SkillMatchingController {

    private final SkillMatchingService skillMatchingService;

    public SkillMatchingController(
            SkillMatchingService skillMatchingService) {

        this.skillMatchingService = skillMatchingService;
    }

    @GetMapping("/jobs/{jobId}/syllabus/{syllabusId}")
    public SkillMatchResponse matchSkills(
            @PathVariable Long jobId,
            @PathVariable Long syllabusId) {

        return skillMatchingService.matchSkills(
                jobId,
                syllabusId);
    }
}