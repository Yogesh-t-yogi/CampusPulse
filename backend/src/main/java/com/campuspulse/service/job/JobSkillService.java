package com.campuspulse.service.job;

import com.campuspulse.entity.job.JobPosting;
import com.campuspulse.entity.job.JobRequiredSkill;
import com.campuspulse.repository.job.JobPostingRepository;
import com.campuspulse.repository.job.JobRequiredSkillRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class JobSkillService {

    private final JobPostingRepository jobPostingRepository;
    private final JobRequiredSkillRepository jobRequiredSkillRepository;
    private final SkillAliasService skillAliasService;

    public JobSkillService(
            JobPostingRepository jobPostingRepository,
            JobRequiredSkillRepository jobRequiredSkillRepository,
            SkillAliasService skillAliasService) {

        this.jobPostingRepository = jobPostingRepository;
        this.jobRequiredSkillRepository = jobRequiredSkillRepository;
        this.skillAliasService = skillAliasService;
    }

    public JobRequiredSkill addSkill(Long jobId, String skill) {

        JobPosting jobPosting = jobPostingRepository.findById(jobId)
                .orElseThrow(() ->
                        new RuntimeException("Job not found: " + jobId));

        String normalizedSkill = skillAliasService.normalizeSkill(skill);
        if (!skillAliasService.isMeaningfulSkill(normalizedSkill)) {
            throw new IllegalArgumentException("Skill cannot be empty.");
        }

        JobRequiredSkill requiredSkill = new JobRequiredSkill();
        requiredSkill.setSkill(normalizedSkill);
        requiredSkill.setJobPosting(jobPosting);

        return jobRequiredSkillRepository.save(requiredSkill);
    }

    public List<JobRequiredSkill> getSkills(Long jobId) {
        return jobRequiredSkillRepository.findByJobPostingId(jobId);
    }

    public Map<Long, List<String>> getSkillsForJobs(Set<Long> jobIds) {
        if (jobIds.isEmpty()) {
            return Map.of();
        }

        return jobRequiredSkillRepository
                .findByJobPostingIdIn(List.copyOf(jobIds))
                .stream()
                .collect(Collectors.groupingBy(
                        skill -> skill.getJobPosting().getId(),
                        Collectors.mapping(
                                JobRequiredSkill::getSkill,
                                Collectors.toList()
                        )
                ));
    }
}