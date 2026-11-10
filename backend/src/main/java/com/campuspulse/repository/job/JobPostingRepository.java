package com.campuspulse.repository.job;

import com.campuspulse.entity.job.JobPosting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface JobPostingRepository
        extends JpaRepository<JobPosting, Long> {

    @Query("""
            SELECT job
            FROM JobPosting job
            WHERE job.owner IS NULL OR job.owner.id = :userId
            ORDER BY job.createdAt DESC
            """)
    List<JobPosting> findAccessibleJobs(@Param("userId") Long userId);

    @Query("""
            SELECT job
            FROM JobPosting job
            WHERE job.id = :jobId
                AND (job.owner IS NULL OR job.owner.id = :userId)
            """)
    Optional<JobPosting> findAccessibleJob(
            @Param("jobId") Long jobId,
            @Param("userId") Long userId);

    List<JobPosting> findBySourceOrderByCreatedAtDesc(String source);

    boolean existsBySourceAndTitle(String source, String title);

    List<JobPosting> findAllByOwner_Id(Long ownerId);
}