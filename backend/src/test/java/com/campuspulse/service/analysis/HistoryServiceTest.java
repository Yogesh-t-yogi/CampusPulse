package com.campuspulse.service.analysis;

import com.campuspulse.dto.matching.SemanticGapReport;
import com.campuspulse.dto.matching.SemanticSkillMatch;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HistoryServiceTest {

    @Test
    void topicDiffFindsAddedRemovedAndNewSubjectsIgnoringCase() {
        Map<String, Set<String>> older = Map.of(
                "Data Structures", Set.of("Arrays", "Linked lists"),
                "Old Elective", Set.of("COBOL"));
        Map<String, Set<String>> newer = Map.of(
                "data structures", Set.of("arrays", "Graphs"),
                "Cloud Computing", Set.of("Docker"));

        List<Map<String, Object>> rows = HistoryService.topicDiff(older, newer);

        assertEquals(3, rows.size());
        Map<String, Object> cloud = rows.get(0);
        assertEquals("NEW", cloud.get("status"));
        Map<String, Object> ds = rows.get(1);
        assertEquals("CHANGED", ds.get("status"));
        assertEquals(List.of("Graphs"), ds.get("added"));
        assertEquals(List.of("Linked lists"), ds.get("removed"));
        assertEquals("REMOVED", rows.get(2).get("status"));
    }

    @Test
    void skillChangesListsOnlyChangedStatuses() {
        SemanticGapReport before = new SemanticGapReport(1L, 1L, 2, 1, 0, 1, 50, List.of(
                new SemanticSkillMatch("Java", 1L, "OOP", 0.9, "MATCHED"),
                new SemanticSkillMatch("Docker", null, null, 0.1, "GAP")));
        SemanticGapReport after = new SemanticGapReport(1L, 2L, 2, 2, 0, 0, 100, List.of(
                new SemanticSkillMatch("Java", 1L, "OOP", 0.9, "MATCHED"),
                new SemanticSkillMatch("docker", 5L, "Containers", 0.8, "MATCHED")));

        List<Map<String, Object>> changes = HistoryService.skillChanges(before, after);

        assertEquals(1, changes.size());
        assertEquals("GAP", changes.get(0).get("before"));
        assertEquals("MATCHED", changes.get(0).get("after"));
    }
}
