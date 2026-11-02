package com.campuspulse.service.job;

import com.campuspulse.dto.job.ResumeJobMatch;
import com.campuspulse.dto.job.ResumeMatchResponse;
import com.campuspulse.entity.job.JobPosting;
import com.campuspulse.entity.job.JobRequiredSkill;
import com.campuspulse.repository.job.JobRequiredSkillRepository;
import com.campuspulse.service.syllabus.PdfTextExtractionService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class ResumeMatchingService {

    private static final long MAX_RESUME_BYTES = 5 * 1024 * 1024;
    private static final int MAX_MATCHES = 20;

    private final PdfTextExtractionService pdfTextExtractionService;
    private final JobSkillAiService jobSkillAiService;
    private final JobPostingService jobPostingService;
    private final JobRequiredSkillRepository jobRequiredSkillRepository;
    private final SkillAliasService skillAliasService;

    public ResumeMatchingService(
            PdfTextExtractionService pdfTextExtractionService,
            JobSkillAiService jobSkillAiService,
            JobPostingService jobPostingService,
            JobRequiredSkillRepository jobRequiredSkillRepository,
            SkillAliasService skillAliasService) {
        this.pdfTextExtractionService = pdfTextExtractionService;
        this.jobSkillAiService = jobSkillAiService;
        this.jobPostingService = jobPostingService;
        this.jobRequiredSkillRepository = jobRequiredSkillRepository;
        this.skillAliasService = skillAliasService;
    }

    public ResumeMatchResponse matchResume(
            MultipartFile resume,
            Long userId) throws IOException {
        if (resume == null || resume.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Choose a PDF resume to upload.");
        }
        if (resume.getSize() > MAX_RESUME_BYTES) {
            throw new ResponseStatusException(
                    HttpStatus.PAYLOAD_TOO_LARGE,
                    "Resume PDFs must be 5 MB or smaller.");
        }

        String resumeText = pdfTextExtractionService.extractText(resume);
        List<String> extractedSkills =
                jobSkillAiService.extractSkillsFromResume(resumeText);
        Set<String> normalizedResumeSkills = extractedSkills.stream()
                .map(skillAliasService::normalizeSkill)
                .filter(skillAliasService::isMeaningfulSkill)
                .map(skill -> skill.toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toSet());

        List<JobPosting> jobs = jobPostingService.getAccessibleJobs(userId);
        List<Long> jobIds = jobs.stream().map(JobPosting::getId).toList();
        if (jobIds.isEmpty()) {
            return new ResumeMatchResponse(List.copyOf(extractedSkills), List.of());
        }

        Map<Long, List<JobRequiredSkill>> skillsByJob = new HashMap<>();
        for (JobRequiredSkill skill
                : jobRequiredSkillRepository.findByJobPostingIdIn(jobIds)) {
            skillsByJob.computeIfAbsent(
                    skill.getJobPosting().getId(), ignored -> new ArrayList<>()
            ).add(skill);
        }

        List<ResumeJobMatch> matches = new ArrayList<>();
        for (JobPosting job : jobs) {
            List<String> matchedSkills = new ArrayList<>();
            List<String> missingSkills = new ArrayList<>();
            Set<String> countedSkills = new HashSet<>();

            for (JobRequiredSkill requiredSkill
                    : skillsByJob.getOrDefault(job.getId(), List.of())) {
                String skill = requiredSkill.getSkill();
                String normalized = skillAliasService.normalizeSkill(skill)
                        .toLowerCase(Locale.ROOT);
                if (!skillAliasService.isMeaningfulSkill(skill)
                        || !countedSkills.add(normalized)) {
                    continue;
                }
                if (normalizedResumeSkills.contains(normalized)) {
                    matchedSkills.add(skill);
                } else {
                    missingSkills.add(skill);
                }
            }

            int totalSkills = matchedSkills.size() + missingSkills.size();
            if (totalSkills == 0 || matchedSkills.isEmpty()) {
                continue;
            }
            int percentage = (int) Math.round(
                    matchedSkills.size() * 100.0 / totalSkills);
            matches.add(new ResumeJobMatch(
                    job.getId(),
                    job.getTitle(),
                    job.getCompany(),
                    job.getLocation(),
                    percentage,
                    List.copyOf(matchedSkills),
                    List.copyOf(missingSkills)
            ));
        }

        matches.sort(Comparator.comparingInt(ResumeJobMatch::matchPercentage)
                .reversed()
                .thenComparing(ResumeJobMatch::title,
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)));

        return new ResumeMatchResponse(
                List.copyOf(extractedSkills),
                matches.stream().limit(MAX_MATCHES).toList()
        );
    }
}
