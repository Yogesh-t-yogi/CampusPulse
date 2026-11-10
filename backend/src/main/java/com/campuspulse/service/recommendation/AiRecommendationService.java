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

    private final LlmService llmService;
    private final ObjectMapper objectMapper;

    public AiRecommendationService(
            LlmService llmService,
            ObjectMapper objectMapper) {

        this.llmService = llmService;
        this.objectMapper = objectMapper;
    }

    public List<AiRecommendation> generateRecommendations(
            SemanticGapReport gapReport) {

        String prompt = buildPrompt(gapReport);

        String response =
                llmService.generateJson(prompt);

        return parseRecommendations(response);
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
            SemanticGapReport gapReport) {

        StringBuilder skills = new StringBuilder();

        for (SemanticSkillMatch match :
                gapReport.getSkillDetails()) {

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
                   For MATCHED skills return an empty array.

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
