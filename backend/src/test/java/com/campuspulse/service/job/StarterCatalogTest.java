package com.campuspulse.service.job;

import com.campuspulse.entity.job.JobPosting;
import com.campuspulse.entity.job.JobRequiredSkill;
import com.campuspulse.repository.job.JobPostingRepository;
import com.campuspulse.repository.job.JobRequiredSkillRepository;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** The built-in catalog seeded at start-up: 20 distinct roles, each with enough usable skills. */
class StarterCatalogTest {

    @Test
    void twentyDistinctRolesEachWithSixOrMoreSkillsAfterCleanup() {
        JobPostingRepository jobs = mock(JobPostingRepository.class);
        JobRequiredSkillRepository skills = mock(JobRequiredSkillRepository.class);
        List<JobPosting> savedJobs = new ArrayList<>();
        Map<JobPosting, Integer> skillCount = new HashMap<>();
        when(jobs.save(any(JobPosting.class))).thenAnswer(call -> {
            savedJobs.add(call.getArgument(0));
            return call.getArgument(0);
        });
        when(skills.saveAll(any())).thenAnswer(call -> {
            Iterable<JobRequiredSkill> batch = call.getArgument(0);
            batch.forEach(s -> skillCount.merge(s.getJobPosting(), 1, Integer::sum));
            return List.of();
        });

        JobCatalogProvider provider = new JobCatalogProvider(jobs, skills, new SkillAliasService());
        assertEquals(20, provider.starterJobs().size());
        assertEquals(20, new HashSet<>(provider.starterJobs().stream().map(JobCatalogProvider.StarterJob::title).toList()).size());

        provider.seedStarterCatalog(); // repository says nothing exists yet → all 20 are created
        assertEquals(20, savedJobs.size());
        for (JobPosting job : savedJobs) {
            int n = skillCount.getOrDefault(job, 0);
            assertTrue(n >= 5, job.getTitle() + " kept only " + n + " skills");
        }
    }
}
