package com.campuspulse.service.analysis;

import com.campuspulse.dto.matching.SemanticGapReport;
import com.campuspulse.dto.matching.SemanticSkillMatch;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MultiRoleReportServiceTest {

    private static SemanticSkillMatch m(String skill, String status) {
        return new SemanticSkillMatch(skill, null, null, 0.5, status);
    }

    @Test
    void combinesSkillsAcrossJobsWeightedByDemand() {
        SemanticGapReport java = new SemanticGapReport(1L, 9L, 3, 1, 1, 1, 50.0, List.of(
                m("Java", "MATCHED"), m("SQL", "PARTIAL"), m("Docker", "GAP")));
        SemanticGapReport data = new SemanticGapReport(2L, 9L, 2, 0, 1, 1, 25.0, List.of(
                m("sql", "PARTIAL"), m("Docker", "GAP")));

        var report = MultiRoleReportService.combine(9L,
                List.of(new MultiRoleReportService.JobSummary(1L, "Java Dev", null, 50.0, 3),
                        new MultiRoleReportService.JobSummary(2L, "Data Eng", null, 25.0, 2)),
                List.of(java, data));

        // Unique skills: Java(1 job), SQL(2), Docker(2) — "sql" and "SQL" are the same skill.
        assertEquals(3, report.skills().size());
        assertEquals(2, report.skills().stream().filter(r -> r.skill().equals("SQL")).findFirst().orElseThrow().jobCount());
        // Weighted: Java 1*1 + SQL 2*0.5 + Docker 2*0 = 2 of 5 → 40%.
        assertEquals(40.0, report.combinedCoverage());
        assertEquals(List.of("Docker"), report.topGaps());
        // Most-demanded skills first.
        assertEquals(1, report.skills().get(report.skills().size() - 1).jobCount());
    }

    @Test
    void rejectsEmptyAndTooManyJobs() {
        MultiRoleReportService service = new MultiRoleReportService(null, null);
        assertThrows(IllegalArgumentException.class, () -> service.build(1L, List.of(), 1L));
        assertThrows(IllegalArgumentException.class, () -> service.build(1L,
                List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L, 11L), 1L));
    }
}
