package com.campuspulse.repository.job;

import com.campuspulse.entity.job.JobPosting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobPostingRepository
        extends JpaRepository<JobPosting, Long> {
}