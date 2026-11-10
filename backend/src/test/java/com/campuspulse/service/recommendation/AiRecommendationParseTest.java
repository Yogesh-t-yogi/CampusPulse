package com.campuspulse.service.recommendation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AiRecommendationParseTest {

    private final AiRecommendationService service =
            new AiRecommendationService(null, new ObjectMapper());

    @Test
    void readsObjectFormat() {
        var list = service.parseRecommendations(
                "{\"recommendations\":[{\"skill\":\"Docker\",\"status\":\"GAP\",\"priority\":\"HIGH\"}]}");
        assertEquals("Docker", list.get(0).getSkill());
    }

    @Test
    void readsOldArrayFormatAndCodeFences() {
        var list = service.parseRecommendations("```json\n[{\"skill\":\"Git\"}]\n```");
        assertEquals("Git", list.get(0).getSkill());
    }

    @Test
    void rejectsMissingList() {
        assertThrows(RuntimeException.class,
                () -> service.parseRecommendations("{\"other\":1}"));
    }
}
