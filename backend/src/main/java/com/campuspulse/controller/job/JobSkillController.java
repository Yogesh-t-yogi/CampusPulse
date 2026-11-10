package com.campuspulse.controller.job;

import com.campuspulse.entity.job.JobRequiredSkill;
import com.campuspulse.service.CurrentUserService;
import com.campuspulse.service.analysis.AnalysisResultService;
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
    private final AnalysisResultService analysisResultService;

    public JobSkillController(
            JobSkillService jobSkillService,
            JobPostingService jobPostingService,
            CurrentUserService currentUserService,
            AnalysisResultService analysisResultService) {
        this.jobSkillService = jobSkillService;
        this.jobPostingService = jobPostingService;
        this.currentUserService = currentUserService;
        this.analysisResultService = analysisResultService;
    }

    @PostMapping("/{jobId}/skills")
    public JobRequiredSkill addSkill(
            @PathVariable Long jobId,
            @RequestParam String skill) {

        jobPostingService.requireModifiableJob(
                jobId, currentUserService.getCurrentUser());
        JobRequiredSkill added = jobSkillService.addSkill(jobId, skill);
        analysisResultService.clearForJob(jobId);
        return added;
    }

    @GetMapping("/{jobId}/skills")
    public List<JobRequiredSkill> getSkills(
            @PathVariable Long jobId) {

        verifyJobAccess(jobId);
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

    private void verifyJobAccess(Long jobId) {
        Long userId = currentUserService.getCurrentUser().getId();
        jobPostingService.requireAccessibleJob(jobId, userId);
    }
}