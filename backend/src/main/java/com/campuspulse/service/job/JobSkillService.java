package com.campuspulse.service.job;

import com.campuspulse.entity.job.JobPosting;
import com.campuspulse.entity.job.JobRequiredSkill;
import com.campuspulse.repository.job.JobPostingRepository;
import com.campuspulse.repository.job.JobRequiredSkillRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class JobSkillService {

    private final JobPostingRepository jobPostingRepository;
    private final JobRequiredSkillRepository jobRequiredSkillRepository;

    public JobSkillService(
            JobPostingRepository jobPostingRepository,
            JobRequiredSkillRepository jobRequiredSkillRepository) {

        this.jobPostingRepository = jobPostingRepository;
        this.jobRequiredSkillRepository = jobRequiredSkillRepository;
    }

    public JobRequiredSkill addSkill(Long jobId, String skill) {

        JobPosting jobPosting = jobPostingRepository.findById(jobId)
                .orElseThrow(() ->
                        new RuntimeException("Job not found: " + jobId));

        JobRequiredSkill requiredSkill = new JobRequiredSkill();
        requiredSkill.setSkill(skill);
        requiredSkill.setJobPosting(jobPosting);

        return jobRequiredSkillRepository.save(requiredSkill);
    }

    public List<JobRequiredSkill> getSkills(Long jobId) {
        return jobRequiredSkillRepository.findByJobPostingId(jobId);
    }
}