package com.campuspulse.controller.job;

import com.campuspulse.service.job.JobSkillAiService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ai/jobs")
public class JobSkillAiController {

    private final JobSkillAiService jobSkillAiService;

    public JobSkillAiController(JobSkillAiService jobSkillAiService) {
        this.jobSkillAiService = jobSkillAiService;
    }

    @PostMapping("/{jobId}/extract-skills")
    public Map<String, Object> extractSkills(
            @PathVariable Long jobId) {

        List<String> skills =
                jobSkillAiService.extractSkills(jobId);

        return Map.of(
                "jobId", jobId,
                "skills", skills,
                "count", skills.size()
        );
    }
}