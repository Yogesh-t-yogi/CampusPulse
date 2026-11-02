package com.campuspulse.service.matching;

import com.campuspulse.dto.matching.SemanticSkillMatch;
import com.campuspulse.entity.job.JobRequiredSkill;
import com.campuspulse.repository.embedding.VectorSearchRepository;
import com.campuspulse.repository.job.JobRequiredSkillRepository;
import com.campuspulse.service.embedding.EmbeddingService;
import com.campuspulse.service.job.SkillAliasService;
import org.springframework.stereotype.Service;
import com.campuspulse.dto.matching.SemanticGapReport;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class SemanticMatchingService {

    private final JobRequiredSkillRepository jobRequiredSkillRepository;
    private final VectorSearchRepository vectorSearchRepository;
    private final EmbeddingService embeddingService;
    private final SkillAliasService skillAliasService;

    public SemanticMatchingService(
            JobRequiredSkillRepository jobRequiredSkillRepository,
            VectorSearchRepository vectorSearchRepository,
            EmbeddingService embeddingService,
            SkillAliasService skillAliasService) {

        this.jobRequiredSkillRepository =
                jobRequiredSkillRepository;

        this.vectorSearchRepository =
                vectorSearchRepository;

        this.embeddingService =
                embeddingService;
        this.skillAliasService = skillAliasService;
    }

    public List<SemanticSkillMatch> matchSkills(
            Long jobId,
            Long syllabusId) {

        List<JobRequiredSkill> jobSkills =
                jobRequiredSkillRepository
                        .findByJobPostingId(jobId);

        if (jobSkills.isEmpty()) {
            throw new RuntimeException(
                    "No required skills found for job: " + jobId);
        }

        List<SemanticSkillMatch> results =
                new ArrayList<>();

        for (JobRequiredSkill jobSkill : jobSkills) {

            if (jobSkill.getSkill() == null ||
                    jobSkill.getSkill().isBlank()) {
                continue;
            }

            String skill = jobSkill.getSkill();
            String normalizedSkill = skillAliasService.normalizeSkill(skill);

            // Generate embedding for the job skill
            List<Double> embedding =
                    embeddingService.generateEmbedding(normalizedSkill);

            String vector =
                    embedding.stream()
                            .map(String::valueOf)
                            .collect(Collectors.joining(
                                    ",",
                                    "[",
                                    "]"
                            ));

            // Search syllabus topics
            List<Map<String, Object>> matches =
                    vectorSearchRepository
                            .findSimilarTopicsByEmbedding(
                                    vector,
                                    syllabusId,
                                    1
                            );

            if (matches.isEmpty()) {
                results.add(
                        new SemanticSkillMatch(
                                skill,
                                null,
                                null,
                                0.0,
                                "GAP"
                        )
                );

                continue;
            }

            Map<String, Object> bestMatch =
                    matches.get(0);

            Long topicId =
                    ((Number) bestMatch.get("id"))
                            .longValue();

            String topic =
                    (String) bestMatch.get("name");

            double similarity =
                    ((Number) bestMatch.get("similarity"))
                            .doubleValue();

            String status = determineStatus(similarity);

                if ("GAP".equals(status)) {
                    topicId = null;
                    topic = null;
                }

                results.add(
                        new SemanticSkillMatch(
                                skill,
                                topicId,
                                topic,
                                similarity,
                                status
                        )
                );
        }

        return results;
    }

    private String determineStatus(double similarity) {

        if (similarity >= 0.70) {
            return "MATCHED";
        }

        if (similarity >= 0.50) {
            return "PARTIAL";
        }

        return "GAP";
    }
    public SemanticGapReport generateGapReport(
        Long jobId,
        Long syllabusId) {

    List<SemanticSkillMatch> matches =
            matchSkills(jobId, syllabusId);

    int totalSkills = matches.size();

    int matchedSkills = 0;
    int partialSkills = 0;
    int gapSkills = 0;

    for (SemanticSkillMatch match : matches) {

        switch (match.getStatus()) {

            case "MATCHED":
                matchedSkills++;
                break;

            case "PARTIAL":
                partialSkills++;
                break;

            case "GAP":
                gapSkills++;
                break;

            default:
                break;
        }
    }

    /*
     * Coverage calculation:
     *
     * MATCHED = 100%
     * PARTIAL = 50%
     * GAP     = 0%
     */
    double coveragePercentage =
            totalSkills == 0
                    ? 0.0
                    : ((matchedSkills * 100.0)
                       + (partialSkills * 50.0))
                       / totalSkills;

    coveragePercentage =
            Math.round(coveragePercentage * 100.0)
                    / 100.0;

    return new SemanticGapReport(
            jobId,
            syllabusId,
            totalSkills,
            matchedSkills,
            partialSkills,
            gapSkills,
            coveragePercentage,
            matches
    );
}
}