package com.campuspulse.controller.job;

import com.campuspulse.dto.job.CustomJobRequest;
import com.campuspulse.entity.job.JobPosting;
import com.campuspulse.repository.job.JobPostingRepository;
import com.campuspulse.service.CurrentUserService;
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

    public JobPostingController(
            JobPostingRepository jobPostingRepository,
            JobPostingService jobPostingService,
            JobSkillAiService jobSkillAiService,
            CurrentUserService currentUserService) {
        this.jobPostingRepository = jobPostingRepository;
        this.jobPostingService = jobPostingService;
        this.jobSkillAiService = jobSkillAiService;
        this.currentUserService = currentUserService;
    }

    // Create a job posting
    @PostMapping
    public JobPosting createJob(@RequestBody JobPosting jobPosting) {
        return jobPostingRepository.save(jobPosting);
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