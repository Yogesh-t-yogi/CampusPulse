package com.campuspulse.service.student;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The "How to learn it" page crashed when the AI returned objects instead of strings. */
class RoadmapNormalizeTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private JsonNode normalize(String json) throws Exception {
        return StudentService.normalizeRoadmap(mapper, mapper.readTree(json));
    }

    @Test
    void objectsBecomePlainText() throws Exception {
        JsonNode out = normalize("""
                {"summary":{"text":"Containers"},
                 "steps":[{"title":"Basics","action":{"do":"Install Docker","why":"to start"},"hours":"about 5 hours"},
                          "Build an image"],
                 "project":{"title":"Todo API","description":"Containerise it"}}
                """);
        assertEquals("Containers", out.get("summary").asText());
        assertEquals("Install Docker — to start", out.get("steps").get(0).get("action").asText());
        assertEquals(5, out.get("steps").get(0).get("hours").asInt());
        assertEquals("Step 2", out.get("steps").get(1).get("title").asText());
        assertEquals("Build an image", out.get("steps").get(1).get("action").asText());
        assertEquals("Todo API — Containerise it", out.get("project").asText());
        assertTrue(out.get("project").isTextual());
    }

    @Test
    void acceptsOtherStepKeysAndMissingFields() throws Exception {
        JsonNode out = normalize("{\"roadmap\":[{\"name\":\"Learn SQL\",\"description\":\"SELECT, JOIN\"}]}");
        assertEquals("Learn SQL", out.get("steps").get(0).get("title").asText());
        assertEquals("", out.get("summary").asText());
        assertTrue(out.get("steps").get(0).get("hours").isNull());
    }

    @Test
    void noUsableStepsMeansNull() throws Exception {
        assertNull(normalize("{\"summary\":\"x\"}"));
        assertNull(normalize("{\"steps\":[{},\"\"]}"));
        assertNull(normalize("[1,2]"));
    }

    // ------------------------------------------------------------------ TC-20

    /** The page renders only {summary: text, steps: [{title: text, action: text, hours: int|null}], project: text}. */
    private static void assertRenderable(JsonNode out) {
        if (out == null) return; // "no usable steps" is reported to the user, never rendered
        assertTrue(out.get("summary").isTextual());
        assertTrue(out.get("project").isTextual());
        assertTrue(out.get("steps").isArray());
        assertTrue(out.get("steps").size() >= 1 && out.get("steps").size() <= StudentService.MAX_ROADMAP_STEPS);
        for (JsonNode step : out.get("steps")) {
            assertTrue(step.get("title").isTextual() && !step.get("title").asText().isBlank());
            assertTrue(step.get("action").isTextual());
            JsonNode hours = step.get("hours");
            assertTrue(hours.isNull() || (hours.isInt() && hours.asInt() >= 1 && hours.asInt() <= 200), hours.toString());
        }
    }

    @org.junit.jupiter.params.ParameterizedTest(name = "TC-20 malformed AI output #{index}")
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "{}",
            "null",
            "[]",
            "\"just a sentence\"",
            "42",
            "{\"steps\":null}",
            "{\"steps\":{\"title\":\"not a list\"}}",
            "{\"steps\":[null, 1, true, {\"title\":null}, [], {}]}",
            "{\"steps\":[[\"Learn\",\"SQL\"],[{\"deep\":{\"deeper\":\"Joins\"}}]]}",
            "{\"steps\":[{\"title\":\"Basics\",\"hours\":-5},{\"title\":\"More\",\"hours\":99999},{\"title\":\"x\",\"hours\":\"lots\"},{\"title\":\"y\",\"hours\":[3]}]}",
            "{\"summary\":[\"a\",{\"b\":\"c\"}],\"learningRoadmap\":[\"Install React\"],\"project\":[1,2]}",
            "{\"steps\":[\"\",\"   \",{\"action\":\"\"}]}",
            "{\"roadmap\":[{\"step\":1,\"what\":\"Kubernetes pods\",\"hours\":2.6}]}"
    })
    void tc20_malformedAiOutputNeverThrows(String json) {
        JsonNode out = org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> normalize(json));
        assertRenderable(out);
    }

    @Test
    void tc20_tooManyStepsAreCappedAndHoursClamped() throws Exception {
        StringBuilder steps = new StringBuilder();
        for (int i = 0; i < 20; i++) steps.append(i == 0 ? "" : ",").append("{\"title\":\"S").append(i).append("\",\"hours\":").append(i * 50 - 10).append("}");
        JsonNode out = normalize("{\"steps\":[" + steps + "]}");
        assertRenderable(out);
        assertEquals(StudentService.MAX_ROADMAP_STEPS, out.get("steps").size());
        assertEquals(1, out.get("steps").get(0).get("hours").asInt(), "negative hours clamp to 1");
        assertEquals(200, out.get("steps").get(7).get("hours").asInt(), "huge hours clamp to 200");
    }

    private StudentService roadmapService(com.campuspulse.service.ai.LlmService llm,
                                          org.springframework.jdbc.core.JdbcTemplate jdbc) {
        return new StudentService(jdbc, null, null, null, null, null,
                new com.campuspulse.service.job.SkillAliasService(), null, llm, mapper);
    }

    @Test
    void tc20_roadmapForSeveralSkillsNormalizesOddAnswersAndFailsCleanlyOnGarbage() {
        var llm = org.mockito.Mockito.mock(com.campuspulse.service.ai.LlmService.class);
        var jdbc = org.mockito.Mockito.mock(org.springframework.jdbc.core.JdbcTemplate.class); // nothing cached
        StudentService service = roadmapService(llm, jdbc);

        java.util.Map<String, String> answers = new java.util.LinkedHashMap<>();
        answers.put("Docker", "{\"summary\":{\"text\":\"Containers\"},\"steps\":[{\"title\":\"Basics\",\"action\":{\"do\":\"Install\"},\"hours\":\"5 h\"}],\"project\":{\"title\":\"API\"}}");
        answers.put("SQL", "{\"roadmap\":[\"SELECT and JOIN\",\"Indexes\"]}");
        answers.put("React", "{\"learningRoadmap\":[{\"name\":\"Components\",\"details\":[\"props\",\"state\"]}]}");
        answers.put("Kubernetes", "{\"steps\":[{\"title\":\"Pods\",\"hours\":3}],\"summary\":null,\"project\":null}");
        for (var entry : answers.entrySet()) {
            org.mockito.Mockito.when(llm.generateJson(org.mockito.ArgumentMatchers.contains("\"" + entry.getKey() + "\"")))
                    .thenReturn(entry.getValue());
        }
        for (String skill : answers.keySet()) {
            JsonNode roadmap = org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> service.roadmap(skill), skill);
            assertRenderable(roadmap);
            org.junit.jupiter.api.Assertions.assertNotNull(roadmap, skill);
        }

        // Garbage / empty / step-less answers: a readable message for the page, never a crash or raw parser error.
        for (String garbage : new String[] {"Sorry, I can't help with that.", "", "{\"summary\":\"only text\"}", "[1,2,3]", "{\"steps\":[{}]}"}) {
            org.mockito.Mockito.when(llm.generateJson(org.mockito.ArgumentMatchers.contains("\"Git\""))).thenReturn(garbage);
            IllegalStateException e = org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                    () -> service.roadmap("Git"), "answer: " + garbage);
            assertTrue(e.getMessage().endsWith("Try again."), e.getMessage());
        }
        // Blank skill is rejected before any AI call.
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> service.roadmap("  "));
    }

    @Test
    void tc20_badCachedRoadmapIsDroppedAndRegenerated() {
        var llm = org.mockito.Mockito.mock(com.campuspulse.service.ai.LlmService.class);
        var jdbc = org.mockito.Mockito.mock(org.springframework.jdbc.core.JdbcTemplate.class);
        org.mockito.Mockito.when(jdbc.queryForList(org.mockito.ArgumentMatchers.contains("skill_roadmap"),
                        org.mockito.ArgumentMatchers.eq(String.class), org.mockito.ArgumentMatchers.<Object>any()))
                .thenReturn(java.util.List.of("{\"summary\":\"old shape, no steps\"}"));
        org.mockito.Mockito.when(llm.generateJson(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn("{\"steps\":[{\"title\":\"Fresh\",\"action\":\"Do it\",\"hours\":4}]}");
        JsonNode roadmap = roadmapService(llm, jdbc).roadmap("Docker");
        assertEquals("Fresh", roadmap.get("steps").get(0).get("title").asText());
        org.mockito.Mockito.verify(jdbc).update(org.mockito.ArgumentMatchers.startsWith("DELETE FROM skill_roadmap"),
                org.mockito.ArgumentMatchers.<Object>any());
    }
}
