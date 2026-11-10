package com.campuspulse.service.matching;

import com.campuspulse.dto.matching.SemanticSkillMatch;
import com.campuspulse.entity.job.JobRequiredSkill;
import com.campuspulse.repository.embedding.VectorSearchRepository;
import com.campuspulse.repository.job.JobRequiredSkillRepository;
import com.campuspulse.service.embedding.SkillEmbeddingCache;
import com.campuspulse.service.job.SkillAliasService;
import com.campuspulse.repository.syllabus.TopicRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.campuspulse.dto.matching.SemanticGapReport;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class SemanticMatchingService {

    private final JobRequiredSkillRepository jobRequiredSkillRepository;
    private final VectorSearchRepository vectorSearchRepository;
    private final SkillEmbeddingCache skillEmbeddingCache;
    private final SkillAliasService skillAliasService;
    private final TopicRepository topicRepository;
    private final double matchedThreshold;
    private final double partialThreshold;

    public SemanticMatchingService(
            JobRequiredSkillRepository jobRequiredSkillRepository,
            VectorSearchRepository vectorSearchRepository,
            SkillEmbeddingCache skillEmbeddingCache,
            SkillAliasService skillAliasService,
            TopicRepository topicRepository,
            @Value("${matching.matched-threshold:0.50}") double matchedThreshold,
            @Value("${matching.partial-threshold:0.25}") double partialThreshold) {

        this.jobRequiredSkillRepository =
                jobRequiredSkillRepository;

        this.vectorSearchRepository =
                vectorSearchRepository;

        this.skillEmbeddingCache = skillEmbeddingCache;
        this.skillAliasService = skillAliasService;
        this.topicRepository = topicRepository;
        this.matchedThreshold = matchedThreshold;
        this.partialThreshold = partialThreshold;
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

        List<KeywordTopicMatcher.TopicText> topics = topicRepository
                .findBySyllabusId(syllabusId).stream()
                .map(t -> new KeywordTopicMatcher.TopicText(
                        t.getId(), t.getName(), t.getDescription()))
                .toList();

        // Vectors for all skills at once (cached after first use).
        Map<String, String> vectors = skillEmbeddingCache.vectorsFor(
                jobSkills.stream()
                        .map(JobRequiredSkill::getSkill)
                        .filter(skill -> skill != null && !skill.isBlank())
                        .map(skillAliasService::normalizeSkill)
                        .toList());

        for (JobRequiredSkill jobSkill : jobSkills) {

            if (jobSkill.getSkill() == null ||
                    jobSkill.getSkill().isBlank()) {
                continue;
            }

            String skill = jobSkill.getSkill();
            String vector = vectors.get(skillAliasService.normalizeSkill(skill));

            results.add(matchOne(skill, vector, syllabusId, topics));
        }

        return results;
    }

    /**
     * Semantic best topic decides the status; an explicit mention of the skill
     * raises it: named in a topic title gives MATCHED, only in a description at least PARTIAL.
     */
    SemanticSkillMatch matchOne(
            String skill,
            String vector,
            Long syllabusId,
            List<KeywordTopicMatcher.TopicText> topics) {

        List<Map<String, Object>> matches = vector == null
                ? List.of()
                : vectorSearchRepository.findSimilarTopicsByEmbedding(vector, syllabusId, 1);

        Long topicId = null;
        String topic = null;
        double similarity = 0.0;
        String status = "GAP";
        String matchedBy = null;

        if (!matches.isEmpty()) {
            Map<String, Object> best = matches.get(0);
            similarity = ((Number) best.get("similarity")).doubleValue();
            status = determineStatus(similarity);
            if (!"GAP".equals(status)) {
                topicId = ((Number) best.get("id")).longValue();
                topic = (String) best.get("name");
                matchedBy = "SEMANTIC";
            }
        }

        var hit = KeywordTopicMatcher.find(
                skillAliasService.normalizeSkill(skill), topics);
        if (hit.isPresent()) {
            String keywordStatus = hit.get().inName() ? "MATCHED" : "PARTIAL";
            if (rank(keywordStatus) > rank(status)) {
                status = keywordStatus;
                topicId = hit.get().topicId();
                topic = hit.get().topicName();
                matchedBy = "KEYWORD";
            }
        }

        return new SemanticSkillMatch(skill, topicId, topic, similarity, status, matchedBy);
    }

    private static int rank(String status) {
        return switch (status) {
            case "MATCHED" -> 2;
            case "PARTIAL" -> 1;
            default -> 0;
        };
    }

    private String determineStatus(double similarity) {

        if (similarity >= matchedThreshold) {
            return "MATCHED";
        }

        if (similarity >= partialThreshold) {
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