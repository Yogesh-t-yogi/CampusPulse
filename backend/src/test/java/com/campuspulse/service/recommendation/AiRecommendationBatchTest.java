package com.campuspulse.service.recommendation;

import com.campuspulse.dto.matching.SemanticGapReport;
import com.campuspulse.dto.matching.SemanticSkillMatch;
import com.campuspulse.dto.recommendation.AiRecommendation;
import com.campuspulse.service.ai.LlmService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Covered skills skip the AI; the rest go in batches of 6; the report's order is kept. */
class AiRecommendationBatchTest {

    private static final Pattern SKILL = Pattern.compile("Skill: (.+)");

    /** Fake AI: one HIGH recommendation for each "Skill:" line in the prompt. */
    private static String answer(String prompt) {
        Matcher m = SKILL.matcher(prompt);
        List<String> items = new ArrayList<>();
        while (m.find()) {
            items.add("{\"skill\":\"" + m.group(1).trim() + "\",\"status\":\"GAP\",\"priority\":\"HIGH\",\"learningRoadmap\":[]}");
        }
        return "{\"recommendations\":[" + String.join(",", items) + "]}";
    }

    private static SemanticGapReport report(List<SemanticSkillMatch> skills) {
        return new SemanticGapReport(1L, 2L, skills.size(), 0, 0, 0, 0, skills);
    }

    @Test
    void coveredSkillsNeedNoAiCall() {
        LlmService llm = mock(LlmService.class);
        var service = new AiRecommendationService(llm, new ObjectMapper());
        List<AiRecommendation> out = service.generateRecommendations(report(List.of(
                new SemanticSkillMatch("Java", 1L, "Core Java", 0.8, "MATCHED"))));
        verify(llm, never()).generateJson(anyString());
        assertEquals("LOW", out.get(0).getPriority());
        assertTrue(out.get(0).getExplanation().contains("Core Java"));
    }

    @Test
    void gapsAreBatchedAndOrderIsKept() {
        LlmService llm = mock(LlmService.class);
        when(llm.generateJson(anyString())).thenAnswer(call -> answer(call.getArgument(0)));
        var service = new AiRecommendationService(llm, new ObjectMapper());

        List<SemanticSkillMatch> skills = new ArrayList<>();
        skills.add(new SemanticSkillMatch("Java", 1L, "Core Java", 0.8, "MATCHED"));
        for (int i = 1; i <= 7; i++) {
            skills.add(new SemanticSkillMatch("Skill" + i, null, null, 0.1, i % 2 == 0 ? "PARTIAL" : "GAP"));
        }
        List<AiRecommendation> out = service.generateRecommendations(report(skills));

        verify(llm, times(2)).generateJson(anyString());          // 7 open skills -> batches of 6 + 1
        assertEquals(8, out.size());
        for (int i = 0; i < skills.size(); i++) {
            assertEquals(skills.get(i).getSkill(), out.get(i).getSkill());
        }
        assertEquals("HIGH", out.get(3).getPriority());
    }

    @Test
    void skillTheAiSkippedGetsGenericAdvice() {
        LlmService llm = mock(LlmService.class);
        when(llm.generateJson(anyString())).thenReturn("{\"recommendations\":[]}");
        var service = new AiRecommendationService(llm, new ObjectMapper());
        List<AiRecommendation> out = service.generateRecommendations(report(List.of(
                new SemanticSkillMatch("Docker", null, null, 0.1, "GAP"))));
        assertEquals("Docker", out.get(0).getSkill());
        assertEquals("HIGH", out.get(0).getPriority());
    }

    @Test
    void oneFailedBatchKeepsTheOthers() {
        LlmService llm = mock(LlmService.class);
        when(llm.generateJson(anyString())).thenAnswer(call -> {
            String prompt = call.getArgument(0);
            if (prompt.contains("Skill: Skill7")) throw new RuntimeException("timeout");
            return answer(prompt);
        });
        var service = new AiRecommendationService(llm, new ObjectMapper());
        List<SemanticSkillMatch> skills = new ArrayList<>();
        for (int i = 1; i <= 7; i++) skills.add(new SemanticSkillMatch("Skill" + i, null, null, 0.1, "GAP"));
        List<AiRecommendation> out = service.generateRecommendations(report(skills));
        assertEquals(7, out.size());
        assertEquals("Skill7", out.get(6).getSkill());          // generic advice for the failed batch
    }

    @Test
    void allBatchesFailingIsAnError() {
        LlmService llm = mock(LlmService.class);
        when(llm.generateJson(anyString())).thenThrow(new RuntimeException("AI down"));
        var service = new AiRecommendationService(llm, new ObjectMapper());
        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class, () -> service.generateRecommendations(
                report(List.of(new SemanticSkillMatch("Docker", null, null, 0.1, "GAP")))));
    }
}
