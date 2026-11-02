package com.campuspulse.service.job;

import com.campuspulse.dto.job.CustomJobRequest;
import com.campuspulse.entity.job.JobPosting;
import com.campuspulse.entity.job.JobRequiredSkill;
import com.campuspulse.entity.user.AppUser;
import com.campuspulse.repository.job.JobPostingRepository;
import com.campuspulse.repository.job.JobRequiredSkillRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

@Service
public class JobPostingService {

    private final JobPostingRepository jobPostingRepository;
    private final JobRequiredSkillRepository jobRequiredSkillRepository;
    private final SkillAliasService skillAliasService;

    public JobPostingService(
            JobPostingRepository jobPostingRepository,
            JobRequiredSkillRepository jobRequiredSkillRepository,
            SkillAliasService skillAliasService) {
        this.jobPostingRepository = jobPostingRepository;
        this.jobRequiredSkillRepository = jobRequiredSkillRepository;
        this.skillAliasService = skillAliasService;
    }

    public List<JobPosting> getAccessibleJobs(Long userId) {
        return jobPostingRepository.findAccessibleJobs(userId);
    }

    public Optional<JobPosting> getAccessibleJob(Long jobId, Long userId) {
        return jobPostingRepository.findAccessibleJob(jobId, userId);
    }

    public JobPosting requireAccessibleJob(Long jobId, Long userId) {
        return getAccessibleJob(jobId, userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Job posting not found"
                ));
    }

    @Transactional
    public JobPosting createCustomJob(
            CustomJobRequest request,
            AppUser owner,
            List<String> skills) {
        JobPosting job = new JobPosting();
        job.setTitle(request.getTitle().trim());
        job.setCompany(blankToNull(request.getCompany()));
        job.setLocation(blankToNull(request.getLocation()));
        job.setDescription(request.getDescription().trim());
        job.setSource("USER_PROVIDED");
        job.setOwner(owner);

        JobPosting savedJob = jobPostingRepository.save(job);
        List<JobRequiredSkill> requiredSkills = skills.stream()
                .map(skillAliasService::normalizeSkill)
                .filter(skillAliasService::isMeaningfulSkill)
                .distinct()
                .map(skill -> {
                    JobRequiredSkill requiredSkill = new JobRequiredSkill();
                    requiredSkill.setSkill(skill);
                    requiredSkill.setJobPosting(savedJob);
                    return requiredSkill;
                })
                .toList();
        jobRequiredSkillRepository.saveAll(requiredSkills);
        return savedJob;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
