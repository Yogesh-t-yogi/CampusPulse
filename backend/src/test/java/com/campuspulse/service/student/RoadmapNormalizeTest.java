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
}
