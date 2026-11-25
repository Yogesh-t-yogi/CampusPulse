package com.campuspulse.service.outcome;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class OutcomeServiceTest {

    private static Map<String, Object> co(String code, Map<String, Integer> mapping,
                                         List<Map<String, Object>> skills, List<String> gaps) {
        Map<String, Object> co = new HashMap<>();
        co.put("code", code);
        co.put("mapping", mapping);
        co.put("skills", new ArrayList<>(skills));
        co.put("gapSkills", new ArrayList<>(gaps));
        return co;
    }

    private static Map<String, Object> po(long id, String code) {
        return Map.of("id", id, "code", code, "title", code, "kind", "PO");
    }

    @Test
    void poCoverageWeightsByMappingLevel() {
        // CO1 (PO1 level 3): Java MATCHED, SQL PARTIAL. CO2 (PO1 level 1): one GAP (Docker).
        var cos = List.of(
                co("CO1", Map.of("1", 3), List.of(
                        Map.of("skill", "Java", "status", "MATCHED"),
                        Map.of("skill", "SQL", "status", "PARTIAL")), List.of()),
                co("CO2", Map.of("1", 1), List.of(), List.of("Docker")));

        var rows = OutcomeService.poCoverage(cos, List.of(po(1, "PO1"), po(2, "PO2")));

        // earned = 3*1 + 3*0.5 = 4.5 ; possible = 3*2 + 1*1 = 7 → 64.3%
        assertEquals(64.3, rows.get(0).get("coverage"));
        assertEquals(List.of("CO1", "CO2"), rows.get(0).get("courseOutcomes"));
        assertNull(rows.get(1).get("coverage"), "PO2 has no mapped COs");
    }

    @Test
    void vectorHelpers() {
        assertArrayEquals(new double[]{0.5, -1.0, 2.0}, OutcomeService.parseVector("[0.5,-1,2]"));
        int best = OutcomeService.nearest(new double[]{1, 0}, List.of(List.of(0.0, 1.0), List.of(0.9, 0.1)));
        assertEquals(1, best);
    }

    @Test
    void twelveNbaOutcomes() {
        assertEquals(12, OutcomeService.NBA_POS.length);
        assertEquals("PO12", OutcomeService.NBA_POS[11][0]);
    }

    // ------------------------------------------------------------------ TC-17

    private static Map<String, Object> storedCo(long id, String code, String description, Map<String, Integer> mapping) {
        Map<String, Object> co = new HashMap<>();
        co.put("id", id);
        co.put("subjectId", 100L);
        co.put("code", code);
        co.put("description", description);
        co.put("mapping", new HashMap<>(mapping));
        return co;
    }

    private static java.sql.ResultSet topicRow(long topicId, String vector) throws java.sql.SQLException {
        java.sql.ResultSet rs = org.mockito.Mockito.mock(java.sql.ResultSet.class);
        org.mockito.Mockito.when(rs.getLong(1)).thenReturn(topicId);
        org.mockito.Mockito.when(rs.getString(2)).thenReturn(vector);
        return rs;
    }

    /**
     * TC-17: for a syllabus whose COs are mapped to POs, outcomesReport places each job skill under
     * the CO nearest to its topic (gaps under the nearest CO) and returns level-weighted PO coverage.
     */
    @Test
    void tc17_outcomesReportReturnsPoCoverageForSyllabusWithCos() throws Exception {
        org.springframework.jdbc.core.JdbcTemplate jdbc = org.mockito.Mockito.mock(org.springframework.jdbc.core.JdbcTemplate.class);
        com.campuspulse.service.embedding.EmbeddingService embeddings =
                org.mockito.Mockito.mock(com.campuspulse.service.embedding.EmbeddingService.class);
        com.campuspulse.service.analysis.AnalysisResultService analysis =
                org.mockito.Mockito.mock(com.campuspulse.service.analysis.AnalysisResultService.class);
        com.campuspulse.service.embedding.SkillEmbeddingCache skillVectors =
                org.mockito.Mockito.mock(com.campuspulse.service.embedding.SkillEmbeddingCache.class);

        // Program POs and the syllabus subject with CO1 (PO1 level 3) and CO2 (PO1 level 1, PO2 level 2).
        List<Map<String, Object>> pos = List.of(po(1, "PO1"), po(2, "PO2"), po(3, "PO3"));
        Map<String, Object> subject = new HashMap<>();
        subject.put("id", 100L);
        subject.put("name", "Web Technologies");
        subject.put("outcomes", new ArrayList<>(List.of(
                storedCo(11, "CO1", "Build object oriented programs with databases", Map.of("1", 3)),
                storedCo(12, "CO2", "Design REST web services", Map.of("1", 1, "2", 2)))));
        Map<String, Object> stored = new HashMap<>();
        stored.put("programOutcomes", pos);
        stored.put("subjects", List.of(subject));

        OutcomeService service = new OutcomeService(jdbc, null, null, analysis, embeddings, skillVectors) {
            @Override
            public Map<String, Object> syllabusOutcomes(com.campuspulse.entity.syllabus.Syllabus syllabus) {
                return stored;
            }
        };

        org.mockito.Mockito.when(embeddings.generateEmbeddings(org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(List.of(List.of(1.0, 0.0), List.of(0.0, 1.0)));
        // Topic 10 is closest to CO1, topic 20 to CO2.
        org.mockito.Mockito.doAnswer(inv -> {
            org.springframework.jdbc.core.RowCallbackHandler handler = inv.getArgument(1);
            handler.processRow(topicRow(10L, "[0.9,0.1]"));
            handler.processRow(topicRow(20L, "[0.1,0.9]"));
            return null;
        }).when(jdbc).query(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(org.springframework.jdbc.core.RowCallbackHandler.class),
                org.mockito.ArgumentMatchers.<Object>any());
        org.mockito.Mockito.when(analysis.getReport(5L, 1L, false)).thenReturn(new com.campuspulse.dto.matching.SemanticGapReport(
                5L, 1L, 4, 2, 1, 1, 62.5, List.of(
                new com.campuspulse.dto.matching.SemanticSkillMatch("Java", 10L, "OOP in Java", 0.8, "MATCHED"),
                new com.campuspulse.dto.matching.SemanticSkillMatch("SQL", 10L, "JDBC", 0.4, "PARTIAL"),
                new com.campuspulse.dto.matching.SemanticSkillMatch("REST API", 20L, "Web services", 0.7, "MATCHED"),
                new com.campuspulse.dto.matching.SemanticSkillMatch("Docker", null, null, 0.1, "GAP"))));
        org.mockito.Mockito.when(skillVectors.vectorsFor(List.of("Docker"))).thenReturn(Map.of("Docker", "[0.2,0.8]"));

        com.campuspulse.entity.syllabus.Syllabus syllabus = new com.campuspulse.entity.syllabus.Syllabus();
        org.springframework.test.util.ReflectionTestUtils.setField(syllabus, "id", 1L);
        Map<String, Object> view = service.outcomesReport(syllabus, 5L);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> cos = (List<Map<String, Object>>) view.get("courseOutcomes");
        assertEquals(2, ((List<?>) cos.get(0).get("skills")).size(), "Java + SQL under CO1");
        assertEquals(List.of("Docker"), cos.get(1).get("gapSkills"), "gap placed under nearest CO");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> coverage = (List<Map<String, Object>>) view.get("poCoverage");
        assertEquals(List.of("PO1", "PO2", "PO3"), coverage.stream().map(r -> r.get("code")).toList());
        // PO1: earned 3*(1+0.5) + 1*1 = 5.5 ; possible 3*2 + 1*(1+1) = 8 -> 68.8 %
        assertEquals(68.8, coverage.get(0).get("coverage"));
        assertEquals(List.of("CO1", "CO2"), coverage.get(0).get("courseOutcomes"));
        // PO2: earned 2*1 ; possible 2*(1+1) = 4 -> 50 %
        assertEquals(50.0, coverage.get(1).get("coverage"));
        assertNull(coverage.get(2).get("coverage"), "PO3 has no mapped CO");
    }

    @Test
    void syllabusWithoutCosHasEmptyCoverageAndAMessage() {
        OutcomeService service = new OutcomeService(null, null, null, null, null, null) {
            @Override
            public Map<String, Object> syllabusOutcomes(com.campuspulse.entity.syllabus.Syllabus syllabus) {
                return Map.of("programOutcomes", List.of(po(1, "PO1")), "subjects", List.of());
            }
        };
        Map<String, Object> view = service.outcomesReport(new com.campuspulse.entity.syllabus.Syllabus(), 5L);
        assertEquals(List.of(), view.get("poCoverage"));
        assertEquals("This syllabus has no course outcomes yet.", view.get("message"));
    }
}
