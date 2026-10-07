package com.campuspulse.service.matching;

import com.campuspulse.dto.matching.SkillMatchResponse;
import com.campuspulse.entity.job.JobRequiredSkill;
import com.campuspulse.entity.syllabus.Syllabus;
import com.campuspulse.entity.syllabus.Subject;
import com.campuspulse.entity.syllabus.Topic;
import com.campuspulse.entity.syllabus.Unit;
import com.campuspulse.repository.job.JobRequiredSkillRepository;
import com.campuspulse.repository.syllabus.SyllabusRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class SkillMatchingService {

    private final JobRequiredSkillRepository jobRequiredSkillRepository;
    private final SyllabusRepository syllabusRepository;

    public SkillMatchingService(
            JobRequiredSkillRepository jobRequiredSkillRepository,
            SyllabusRepository syllabusRepository) {

        this.jobRequiredSkillRepository = jobRequiredSkillRepository;
        this.syllabusRepository = syllabusRepository;
    }

    public SkillMatchResponse matchSkills(
            Long jobId,
            Long syllabusId) {

        // 1. Get job skills
        List<JobRequiredSkill> jobSkills =
                jobRequiredSkillRepository
                        .findByJobPostingId(jobId);

        if (jobSkills.isEmpty()) {
            throw new RuntimeException(
                    "No required skills found for job: " + jobId);
        }

        // 2. Get syllabus
        Syllabus syllabus =
                syllabusRepository.findById(syllabusId)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Syllabus not found: "
                                                + syllabusId));

        // 3. Extract all syllabus topics
        Set<String> syllabusTopics =
                extractSyllabusTopics(syllabus);

        // 4. Compare job skills against syllabus topics
        List<String> matchedSkills = new ArrayList<>();
        List<String> missingSkills = new ArrayList<>();

        for (JobRequiredSkill jobSkill : jobSkills) {

            String skill = jobSkill.getSkill();

            if (skill == null || skill.isBlank()) {
                continue;
            }

            if (isSkillPresent(skill, syllabusTopics)) {
                matchedSkills.add(skill);
            } else {
                missingSkills.add(skill);
            }
        }

        // 5. Calculate percentage
        int totalSkills =
                matchedSkills.size() + missingSkills.size();

        double matchPercentage = totalSkills == 0
                ? 0.0
                : ((double) matchedSkills.size()
                    / totalSkills) * 100;

        // Round to 2 decimal places
        matchPercentage =
                Math.round(matchPercentage * 100.0) / 100.0;

        return new SkillMatchResponse(
                jobId,
                syllabusId,
                matchedSkills,
                missingSkills,
                totalSkills,
                matchedSkills.size(),
                matchPercentage
        );
    }

    private Set<String> extractSyllabusTopics(
            Syllabus syllabus) {

        Set<String> topics = new HashSet<>();

        for (Subject subject : syllabus.getSubjects()) {

            for (Unit unit : subject.getUnits()) {

                for (Topic topic : unit.getTopics()) {

                    if (topic.getName() != null) {

                        topics.add(
                                normalize(topic.getName()));
                    }
                }
            }
        }

        return topics;
    }

    private boolean isSkillPresent(
            String jobSkill,
            Set<String> syllabusTopics) {

        String normalizedSkill =
                normalize(jobSkill);

        // Exact match
        if (syllabusTopics.contains(normalizedSkill)) {
            return true;
        }

        // Basic partial matching
        for (String topic : syllabusTopics) {

            if (topic.contains(normalizedSkill)
                    || normalizedSkill.contains(topic)) {

                return true;
            }
        }

        return false;
    }

    private String normalize(String value) {

        return value
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9+#. ]", "")
                .replaceAll("\\s+", " ")
                .trim();
    }
}