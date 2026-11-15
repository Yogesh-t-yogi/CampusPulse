package com.campuspulse.dto;

import com.campuspulse.dto.matching.SemanticGapReport;
import com.campuspulse.dto.matching.SemanticSkillMatch;
import com.campuspulse.dto.recommendation.AiRecommendation;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Saved analysis results are stored as JSON and must read back unchanged. */
class SavedResultJsonTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void gapReportRoundTrips() throws Exception {
        SemanticGapReport report = new SemanticGapReport(1L, 2L, 2, 1, 0, 1, 50.0, List.of(
                new SemanticSkillMatch("Java", 11L, "OOP in Java", 0.81, "MATCHED"),
                new SemanticSkillMatch("Docker", null, null, 0.2, "GAP")));

        SemanticGapReport back = mapper.readValue(
                mapper.writeValueAsString(report), SemanticGapReport.class);

        assertEquals(50.0, back.getCoveragePercentage());
        assertEquals(2, back.getSkillDetails().size());
        assertEquals("OOP in Java", back.getSkillDetails().get(0).getTopic());
        assertNull(back.getSkillDetails().get(1).getTopicId());
    }

    @Test
    void recommendationsRoundTrip() throws Exception {
        String json = """
                [{"skill":"Docker","status":"GAP","explanation":"e","curriculumRecommendation":"c",
                  "studentRecommendation":"s","priority":"HIGH",
                  "learningRoadmap":[{"title":"t","action":"a"}]}]
                """;
        List<AiRecommendation> list = mapper.readValue(json, new TypeReference<>() {});
        String again = mapper.writeValueAsString(list);
        List<AiRecommendation> back = mapper.readValue(again, new TypeReference<>() {});
        assertEquals("Docker", back.get(0).getSkill());
        assertEquals(1, back.get(0).getLearningRoadmap().size());
    }
}
