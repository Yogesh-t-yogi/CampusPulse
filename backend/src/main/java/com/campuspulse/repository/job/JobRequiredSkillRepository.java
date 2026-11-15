package com.campuspulse.repository.job;

import com.campuspulse.entity.job.JobRequiredSkill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface JobRequiredSkillRepository
        extends JpaRepository<JobRequiredSkill, Long> {

    List<JobRequiredSkill> findByJobPostingId(Long jobPostingId);

    List<JobRequiredSkill> findByJobPostingIdIn(List<Long> jobPostingIds);

    @Query("""
            SELECT requiredSkill.skill, COUNT(DISTINCT job.id)
            FROM JobRequiredSkill requiredSkill
            JOIN requiredSkill.jobPosting job
            WHERE job.source = 'DATASET'
                AND (job.institutionId IS NULL OR job.institutionId = :institutionId)
            GROUP BY requiredSkill.skill
            ORDER BY COUNT(DISTINCT job.id) DESC
            """)
    List<Object[]> findDatasetSkillDemand(@Param("institutionId") Long institutionId);
}