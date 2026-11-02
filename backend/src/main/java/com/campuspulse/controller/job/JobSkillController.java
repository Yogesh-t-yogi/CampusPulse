package com.campuspulse.controller.job;

import com.campuspulse.entity.job.JobRequiredSkill;
import com.campuspulse.service.CurrentUserService;
import com.campuspulse.service.job.JobPostingService;
import com.campuspulse.service.job.JobSkillService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.List;

@RestController
@RequestMapping("/api/jobs")
public class JobSkillController {

    private final JobSkillService jobSkillService;
    private final JobPostingService jobPostingService;
    private final CurrentUserService currentUserService;

    public JobSkillController(
            JobSkillService jobSkillService,
            JobPostingService jobPostingService,
            CurrentUserService currentUserService) {
        this.jobSkillService = jobSkillService;
        this.jobPostingService = jobPostingService;
        this.currentUserService = currentUserService;
    }

    @PostMapping("/{jobId}/skills")
    public JobRequiredSkill addSkill(
            @PathVariable Long jobId,
            @RequestParam String skill) {

        verifyJobOwnership(jobId);
        return jobSkillService.addSkill(jobId, skill);
    }

    @GetMapping("/{jobId}/skills")
    public List<JobRequiredSkill> getSkills(
            @PathVariable Long jobId) {

        verifyJobOwnership(jobId);
        return jobSkillService.getSkills(jobId);
    }

    @GetMapping("/skills")
    public Map<Long, List<String>> getAllAccessibleSkills() {
        Long userId = currentUserService.getCurrentUser().getId();
        Set<Long> accessibleJobIds = jobPostingService
                .getAccessibleJobs(userId)
                .stream()
                .map(job -> job.getId())
                .collect(Collectors.toSet());
        return jobSkillService.getSkillsForJobs(accessibleJobIds);
    }

    private void verifyJobOwnership(Long jobId) {
        Long userId = currentUserService.getCurrentUser().getId();
        jobPostingService.requireAccessibleJob(jobId, userId);
    }
}