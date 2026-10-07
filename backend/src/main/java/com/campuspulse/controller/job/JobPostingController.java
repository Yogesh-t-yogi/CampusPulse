package com.campuspulse.controller.job;

import com.campuspulse.entity.job.JobPosting;
import com.campuspulse.repository.job.JobPostingRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/jobs")
public class JobPostingController {

    private final JobPostingRepository jobPostingRepository;

    public JobPostingController(JobPostingRepository jobPostingRepository) {
        this.jobPostingRepository = jobPostingRepository;
    }

    // Create a job posting
    @PostMapping
    public JobPosting createJob(@RequestBody JobPosting jobPosting) {
        return jobPostingRepository.save(jobPosting);
    }

    // Get all job postings
    @GetMapping
    public List<JobPosting> getAllJobs() {
        return jobPostingRepository.findAll();
    }

    // Get job posting by ID
    @GetMapping("/{id}")
    public ResponseEntity<JobPosting> getJobById(@PathVariable Long id) {

        return jobPostingRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}