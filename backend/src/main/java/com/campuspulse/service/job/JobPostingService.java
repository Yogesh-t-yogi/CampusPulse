package com.campuspulse.service.job;

import com.campuspulse.dto.job.CustomJobRequest;
import com.campuspulse.dto.job.JobForm;
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

    /**
     * Jobs can be read by everyone who can access them, but only changed by
     * their owner. Shared catalog jobs (no owner) can only be changed by admins.
     */
    public JobPosting requireModifiableJob(Long jobId, AppUser user) {
        JobPosting job = requireAccessibleJob(jobId, user.getId());
        boolean isAdmin = "ADMIN".equals(user.getRole());
        Long ownerId = job.getOwner() == null ? null : job.getOwner().getId();
        if (!isAdmin && !user.getId().equals(ownerId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Only an admin can change shared catalog jobs.");
        }
        return job;
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

    /** Edit title/company/location/description and replace the skill list. */
    @Transactional
    public JobPosting updateJob(Long jobId, AppUser user, JobForm form) {
        JobPosting job = requireModifiableJob(jobId, user);
        if (form.title() == null || form.title().isBlank()) {
            throw new IllegalArgumentException("Job title is required.");
        }
        job.setTitle(form.title().trim());
        job.setCompany(blankToNull(form.company()));
        job.setLocation(blankToNull(form.location()));
        job.setDescription(form.description() == null ? "" : form.description().trim());
        JobPosting saved = jobPostingRepository.save(job);

        if (form.skills() != null) {
            jobRequiredSkillRepository.deleteAll(jobRequiredSkillRepository.findByJobPostingId(jobId));
            List<JobRequiredSkill> skills = form.skills().stream()
                    .map(skillAliasService::normalizeSkill)
                    .filter(skillAliasService::isMeaningfulSkill)
                    .distinct()
                    .map(skill -> {
                        JobRequiredSkill requiredSkill = new JobRequiredSkill();
                        requiredSkill.setSkill(skill);
                        requiredSkill.setJobPosting(saved);
                        return requiredSkill;
                    })
                    .toList();
            jobRequiredSkillRepository.saveAll(skills);
        }
        return saved;
    }

    /** Deletes the job and its skills; saved reports go with it (DB cascade). */
    @Transactional
    public void deleteJob(Long jobId, AppUser user) {
        JobPosting job = requireModifiableJob(jobId, user);
        jobRequiredSkillRepository.deleteAll(jobRequiredSkillRepository.findByJobPostingId(jobId));
        jobPostingRepository.delete(job);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
