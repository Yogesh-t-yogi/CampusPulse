package com.campuspulse.service.ai;

import com.campuspulse.dto.ai.AiSubject;
import com.campuspulse.dto.ai.AiSyllabusResponse;
import com.campuspulse.dto.ai.AiTopic;
import com.campuspulse.dto.ai.AiUnit;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SyllabusChunkerTest {

    @Test
    void shortTextIsOnePart() {
        assertEquals(1, SyllabusChunker.split("Module 1: Intro\nTopic A").size());
    }

    @Test
    void longTextSplitsAtModuleHeadingsWithinLimit() {
        StringBuilder text = new StringBuilder();
        for (int m = 1; m <= 6; m++) {
            text.append("Module ").append(m).append(": Title ").append(m).append('\n');
            for (int t = 0; t < 20; t++) {
                text.append("Topic line number ").append(t).append(" with some words\n");
            }
        }
        List<String> parts = SyllabusChunker.split(text.toString(), 1500);
        assertTrue(parts.size() > 1);
        parts.forEach(part -> assertTrue(part.length() <= 1500));
        // Parts after the first start at a module heading.
        assertTrue(parts.get(1).startsWith("Module"));
        assertEquals(text.toString().replace("\n", "").length(),
                String.join("", parts).replace("\n", "").length());
    }

    @Test
    void mergeJoinsSameSubjectAndContinuedUnit() {
        AiSyllabusResponse first = response("Java Programming", "CS101",
                unit(1, "Basics", "Variables", "Loops"));
        AiSyllabusResponse second = response("java programming", null,
                unit(1, "Basics", "Loops", "Arrays"),
                unit(2, "OOP", "Classes"));

        AiSyllabusResponse merged = SyllabusChunker.merge(List.of(first, second));

        assertEquals(1, merged.getSubjects().size());
        AiSubject subject = merged.getSubjects().get(0);
        assertEquals("CS101", subject.getCode());
        assertEquals(2, subject.getUnits().size());
        assertEquals(3, subject.getUnits().get(0).getTopics().size()); // Variables, Loops, Arrays
    }

    private static AiSyllabusResponse response(String name, String code, AiUnit... units) {
        AiSubject subject = new AiSubject();
        subject.setName(name);
        subject.setCode(code);
        subject.setUnits(new ArrayList<>(List.of(units)));
        AiSyllabusResponse response = new AiSyllabusResponse();
        response.setSubjects(new ArrayList<>(List.of(subject)));
        return response;
    }

    private static AiUnit unit(int number, String name, String... topics) {
        AiUnit unit = new AiUnit();
        unit.setUnitNumber(number);
        unit.setName(name);
        List<AiTopic> list = new ArrayList<>();
        for (String t : topics) {
            AiTopic topic = new AiTopic();
            topic.setName(t);
            topic.setDescription("d");
            list.add(topic);
        }
        unit.setTopics(list);
        return unit;
    }
}
