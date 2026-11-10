package com.campuspulse.controller.job;

import com.campuspulse.dto.job.CustomJobRequest;
import com.campuspulse.entity.job.JobPosting;
import com.campuspulse.repository.job.JobPostingRepository;
import com.campuspulse.service.CurrentUserService;
import com.campuspulse.dto.job.JobForm;
import com.campuspulse.service.analysis.AnalysisResultService;
import com.campuspulse.service.job.JobCatalogProvider;
import com.campuspulse.service.job.JobPostingService;
import com.campuspulse.service.job.JobSkillAiService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

import java.util.List;

@RestController
@RequestMapping("/api/jobs")
public class JobPostingController {

    private final JobPostingRepository jobPostingRepository;
    private final JobPostingService jobPostingService;
    private final JobSkillAiService jobSkillAiService;
    private final CurrentUserService currentUserService;
    private final JobCatalogProvider jobCatalogProvider;
    private final AnalysisResultService analysisResultService;

    public JobPostingController(
            JobPostingRepository jobPostingRepository,
            JobPostingService jobPostingService,
            JobSkillAiService jobSkillAiService,
            CurrentUserService currentUserService,
            JobCatalogProvider jobCatalogProvider,
            AnalysisResultService analysisResultService) {
        this.jobPostingRepository = jobPostingRepository;
        this.jobPostingService = jobPostingService;
        this.jobSkillAiService = jobSkillAiService;
        this.currentUserService = currentUserService;
        this.jobCatalogProvider = jobCatalogProvider;
        this.analysisResultService = analysisResultService;
    }

    // ADMIN: add a shared catalog job by hand (SecurityConfig: POST /api/jobs is ADMIN only)
    @PostMapping
    public JobPosting createJob(@RequestBody JobForm form) {
        var user = currentUserService.getCurrentUser();
        Long catalog = "SUPER_ADMIN".equals(user.getRole()) ? null : user.getInstitutionId();
        return jobCatalogProvider.createCatalogJob(
                form.title(), form.company(), form.location(), form.description(), form.skills(), catalog);
    }

    // Edit: own private jobs, or any job for admins
    @PutMapping("/{id}")
    public JobPosting updateJob(@PathVariable Long id, @RequestBody JobForm form) {
        JobPosting job = jobPostingService.updateJob(id, currentUserService.getCurrentUser(), form);
        analysisResultService.clearForJob(id);
        return job;
    }

    // Delete: own private jobs, or any job for admins
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteJob(@PathVariable Long id) {
        jobPostingService.deleteJob(id, currentUserService.getCurrentUser());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/custom")
    public ResponseEntity<JobPosting> createCustomJob(
            @Valid @RequestBody CustomJobRequest request) {
        var owner = currentUserService.getCurrentUser();
        List<String> skills =
                jobSkillAiService.extractSkillsFromDescription(
                        request.getDescription());
        JobPosting job = jobPostingService.createCustomJob(
                request,
                owner,
                skills
        );
        return ResponseEntity.ok(job);
    }

    // Get all job postings
    @GetMapping
    public List<JobPosting> getAllJobs() {
        Long userId = currentUserService.getCurrentUser().getId();
        return jobPostingService.getAccessibleJobs(userId);
    }

    // Get job posting by ID
    @GetMapping("/{id}")
    public ResponseEntity<JobPosting> getJobById(@PathVariable Long id) {
        Long userId = currentUserService.getCurrentUser().getId();
        return jobPostingService.getAccessibleJob(id, userId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}