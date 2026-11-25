package com.campuspulse.service.recommendation;

import com.campuspulse.dto.matching.SemanticGapReport;
import com.campuspulse.dto.matching.SemanticSkillMatch;
import com.campuspulse.service.ai.LlmService;
import com.campuspulse.dto.recommendation.AiRecommendation;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;


import java.util.List;

@Service
public class AiRecommendationService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AiRecommendationService.class);

    private final LlmService llmService;
    private final ObjectMapper objectMapper;

    public AiRecommendationService(
            LlmService llmService,
            ObjectMapper objectMapper) {

        this.llmService = llmService;
        this.objectMapper = objectMapper;
    }

    /** Skills per AI request; smaller batches answer faster and run in parallel. */
    static final int BATCH = 6;
    private static final java.util.concurrent.ExecutorService POOL =
            java.util.concurrent.Executors.newFixedThreadPool(3, runnable -> {
                Thread thread = new Thread(runnable, "recommendations");
                thread.setDaemon(true);
                return thread;
            });

    /**
     * Covered skills get a fixed note (no AI call); partly covered and gap skills go to the AI in
     * parallel batches of {@link #BATCH}. Result keeps the report's skill order.
     */
    public List<AiRecommendation> generateRecommendations(
            SemanticGapReport gapReport) {

        List<SemanticSkillMatch> all = gapReport.getSkillDetails() == null ? List.of() : gapReport.getSkillDetails();
        List<SemanticSkillMatch> open = all.stream().filter(match -> !"MATCHED".equals(match.getStatus())).toList();

        List<java.util.concurrent.CompletableFuture<List<AiRecommendation>>> calls = new java.util.ArrayList<>();
        for (int from = 0; from < open.size(); from += BATCH) {
            List<SemanticSkillMatch> batch = open.subList(from, Math.min(open.size(), from + BATCH));
            String prompt = buildPrompt(gapReport, batch);
            calls.add(java.util.concurrent.CompletableFuture.supplyAsync(
                    () -> parseRecommendations(llmService.generateJson(prompt)), POOL));
        }
        // A failed batch keeps the others: its skills get the generic advice. Only if every batch
        // fails is the error passed on (so nothing half-empty is saved while the AI is down).
        java.util.Map<String, AiRecommendation> fromAi = new java.util.HashMap<>();
        RuntimeException lastError = null;
        int failed = 0;
        for (var call : calls) {
            try {
                call.join().forEach(item -> {
                    if (item.getSkill() != null) fromAi.putIfAbsent(key(item.getSkill()), item);
                });
            } catch (java.util.concurrent.CompletionException e) {
                failed++;
                lastError = e.getCause() instanceof RuntimeException runtime ? runtime : e;
                log.warn("Recommendation batch failed: {}", lastError.getMessage());
            }
        }
        if (failed > 0 && failed == calls.size()) {
            throw lastError;
        }

        List<AiRecommendation> result = new java.util.ArrayList<>();
        for (SemanticSkillMatch match : all) {
            AiRecommendation item = "MATCHED".equals(match.getStatus())
                    ? covered(match)
                    : fromAi.getOrDefault(key(match.getSkill()), missing(match));
            result.add(item);
        }
        return result;
    }

    private static String key(String skill) {
        return skill == null ? "" : skill.trim().toLowerCase(java.util.Locale.ROOT);
    }

    /** Covered skill: no AI needed. */
    static AiRecommendation covered(SemanticSkillMatch match) {
        String topic = match.getTopic() == null ? "the syllabus" : "\"" + match.getTopic() + "\"";
        AiRecommendation item = new AiRecommendation(match.getSkill(), "MATCHED",
                "Covered by " + topic + ".",
                "Keep this topic; add a hands-on exercise if it is taught only in theory.",
                "Revise " + topic + " and use " + match.getSkill() + " in a small project.",
                "LOW");
        item.setLearningRoadmap(List.of());
        return item;
    }

    /** The AI skipped a skill: short generic advice instead of dropping it. */
    private static AiRecommendation missing(SemanticSkillMatch match) {
        boolean gap = "GAP".equals(match.getStatus());
        AiRecommendation item = new AiRecommendation(match.getSkill(), match.getStatus(),
                gap ? "The syllabus does not cover this skill." : "The syllabus covers this skill only partly.",
                (gap ? "Add " : "Extend coverage of ") + match.getSkill() + " with practical sessions.",
                "Learn " + match.getSkill() + " from free tutorials and practise it in a small project.",
                gap ? "HIGH" : "MEDIUM");
        item.setLearningRoadmap(List.of());
        return item;
    }

    List<AiRecommendation> parseRecommendations(String response) {
        try {

            String json = cleanJson(response);

            // Expected {"recommendations": [...]}; a bare array is accepted too.
            com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(json);
            com.fasterxml.jackson.databind.JsonNode list =
                    root.isArray() ? root : root.path("recommendations");
            if (!list.isArray()) {
                throw new IllegalArgumentException("No recommendations array in AI response");
            }
            return objectMapper.convertValue(
                    list,
                    new TypeReference<List<AiRecommendation>>() {}
            );

        } catch (Exception e) {

            throw new RuntimeException(
                    "Failed to parse AI recommendations: "
                            + e.getMessage(),
                    e
            );
        }
    }

    private String buildPrompt(
            SemanticGapReport gapReport, List<SemanticSkillMatch> batch) {

        StringBuilder skills = new StringBuilder();

        for (SemanticSkillMatch match : batch) {

            skills.append("""
                    Skill: %s
                    Status: %s
                    Similarity: %.2f
                    Curriculum Topic: %s

                    """
                    .formatted(
                            match.getSkill(),
                            match.getStatus(),
                            match.getSimilarity(),
                            match.getTopic() == null
                                    ? "None"
                                    : match.getTopic()
                    ));
        }

        return """
                You are an AI curriculum-to-industry
                gap analysis assistant.

                Analyze the following job skill requirements
                against the university curriculum.

                Job ID: %d
                Syllabus ID: %d

                Overall Coverage: %.2f%%

                Skill Analysis:
                %s

                For every skill, provide:

                1. skill
                2. status
                3. explanation
                4. curriculumRecommendation
                5. studentRecommendation
                6. priority
                7. learningRoadmap: for PARTIAL or GAP skills, provide 3
                   concise sequential steps, each with a title and an action.
                   Return one object for each skill listed above, using the same skill name.

                Roadmap actions must be practical, beginner-friendly, and
                achievable with free learning materials. Do not invent URLs.

                Priority must be one of:
                HIGH, MEDIUM, LOW

                Return ONLY a valid JSON object of the form
                {"recommendations": [ ...one object per skill... ]}.

                Do not use markdown.
                Do not use ```json.
                Do not add explanations outside the JSON.

                JSON format:

                {"recommendations": [
                  {
                    "skill": "PostgreSQL",
                    "status": "GAP",
                    "explanation": "The curriculum does not
                    appear to cover PostgreSQL directly.",
                    "curriculumRecommendation":
                    "Add PostgreSQL fundamentals and database
                    integration topics.",
                    "studentRecommendation":
                    "Learn PostgreSQL basics and practice
                    CRUD operations using Spring Boot.",
                    "priority": "HIGH",
                    "learningRoadmap": [
                      {
                        "title": "Learn the foundations",
                        "action": "Study tables, data types, and basic SELECT queries."
                      },
                      {
                        "title": "Practice core operations",
                        "action": "Create a small database and practice joins and CRUD queries."
                      },
                      {
                        "title": "Build a project",
                        "action": "Connect a small application to PostgreSQL and document the schema."
                      }
                    ]
                  }
                ]}
                """
                .formatted(
                        gapReport.getJobId(),
                        gapReport.getSyllabusId(),
                        gapReport.getCoveragePercentage(),
                        skills
                );
    }

    private String cleanJson(String response) {

        String json = response.trim();

        if (json.startsWith("```json")) {
            json = json.substring(7);
        }

        if (json.startsWith("```")) {
            json = json.substring(3);
        }

        if (json.endsWith("```")) {
            json = json.substring(
                    0,
                    json.length() - 3
            );
        }

        return json.trim();
    }
}
