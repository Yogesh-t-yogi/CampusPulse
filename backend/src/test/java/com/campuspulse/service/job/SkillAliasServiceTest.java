package com.campuspulse.service.job;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SkillAliasServiceTest {

    private final SkillAliasService skillAliasService =
            new SkillAliasService();

    @Test
    void normalizesCommonSkillAliases() {
        assertEquals("JavaScript", skillAliasService.normalizeSkill("JS"));
        assertEquals("PostgreSQL", skillAliasService.normalizeSkill("Postgres"));
        assertEquals("Node.js", skillAliasService.normalizeSkill("node js"));
        assertEquals("CI/CD", skillAliasService.normalizeSkill("ci-cd"));
    }

    @Test
    void preservesUnknownSkillNames() {
        assertEquals("Spring WebFlux",
                skillAliasService.normalizeSkill("Spring WebFlux"));
    }
}
