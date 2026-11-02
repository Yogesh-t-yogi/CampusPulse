package com.campuspulse.service.job;

import com.campuspulse.entity.job.JobPosting;

import java.util.List;

public interface JobSearchProvider {
    List<JobPosting> searchJobs(String keyword, String location, int limit);
}
