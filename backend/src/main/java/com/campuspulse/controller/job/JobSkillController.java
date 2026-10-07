package com.campuspulse.controller.job;

import com.campuspulse.entity.job.JobRequiredSkill;
import com.campuspulse.service.job.JobSkillService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/jobs")
public class JobSkillController {

    private final JobSkillService jobSkillService;

    public JobSkillController(JobSkillService jobSkillService) {
        this.jobSkillService = jobSkillService;
    }

    @PostMapping("/{jobId}/skills")
    public JobRequiredSkill addSkill(
            @PathVariable Long jobId,
            @RequestParam String skill) {

        return jobSkillService.addSkill(jobId, skill);
    }

    @GetMapping("/{jobId}/skills")
    public List<JobRequiredSkill> getSkills(
            @PathVariable Long jobId) {

        return jobSkillService.getSkills(jobId);
    }
}