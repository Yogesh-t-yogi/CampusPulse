package com.campuspulse.service.student;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LearningResourcesTest {

    @Test
    void officialDocsFirstThenSearchLinks() {
        var list = LearningResources.forSkill("Docker");
        assertEquals("Official documentation", list.get(0).label());
        assertTrue(list.get(0).url().startsWith("https://docs.docker.com"));
        assertTrue(list.stream().anyMatch(r -> r.url().contains("nptel")));
    }

    @Test
    void unknownSkillGetsEncodedSearchLinksOnly() {
        var list = LearningResources.forSkill("C++ & STL");
        assertTrue(list.stream().noneMatch(r -> "DOCS".equals(r.kind())));
        assertTrue(list.get(0).url().contains("C%2B%2B+%26+STL"));
    }
}
