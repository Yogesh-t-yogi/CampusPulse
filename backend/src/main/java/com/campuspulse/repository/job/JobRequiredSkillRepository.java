package com.campuspulse.repository.job;

import com.campuspulse.entity.job.JobRequiredSkill;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface JobRequiredSkillRepository
        extends JpaRepository<JobRequiredSkill, Long> {

    List<JobRequiredSkill> findByJobPostingId(Long jobPostingId);
}