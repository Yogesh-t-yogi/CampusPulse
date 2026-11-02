package com.campuspulse.controller.job;

import com.campuspulse.service.job.JobSkillAiService;
import com.campuspulse.service.CurrentUserService;
import com.campuspulse.service.job.JobPostingService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ai/jobs")
public class JobSkillAiController {

    private final JobSkillAiService jobSkillAiService;
    private final JobPostingService jobPostingService;
    private final CurrentUserService currentUserService;

    public JobSkillAiController(
            JobSkillAiService jobSkillAiService,
            JobPostingService jobPostingService,
            CurrentUserService currentUserService) {
        this.jobSkillAiService = jobSkillAiService;
        this.jobPostingService = jobPostingService;
        this.currentUserService = currentUserService;
    }

    @PostMapping("/{jobId}/extract-skills")
    public Map<String, Object> extractSkills(
            @PathVariable Long jobId) {

        Long userId = currentUserService.getCurrentUser().getId();
        jobPostingService.requireAccessibleJob(jobId, userId);
        List<String> skills =
                jobSkillAiService.extractSkills(jobId);

        return Map.of(
                "jobId", jobId,
                "skills", skills,
                "count", skills.size()
        );
    }
}