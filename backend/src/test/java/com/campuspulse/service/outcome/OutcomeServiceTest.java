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
}
