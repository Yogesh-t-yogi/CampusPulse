package com.campuspulse.service.analysis;

import com.campuspulse.dto.matching.SemanticGapReport;
import com.campuspulse.dto.matching.SemanticSkillMatch;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Phase 5.3: report history (coverage over time, across syllabus versions) and the
 * difference between two syllabus versions. Callers check read access to every syllabus first.
 */
@Service
public class HistoryService {

    static final int MAX_CHAIN = 20;

    private final JdbcTemplate jdbc;
    private final AnalysisResultService analysisResultService;

    public HistoryService(JdbcTemplate jdbc, AnalysisResultService analysisResultService) {
        this.jdbc = jdbc;
        this.analysisResultService = analysisResultService;
    }

    /** This syllabus and the versions before it, newest first. */
    public List<Map<String, Object>> versions(Long syllabusId) {
        List<Map<String, Object>> chain = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        Long current = syllabusId;
        while (current != null && seen.add(current) && chain.size() < MAX_CHAIN) {
            List<Map<String, Object>> rows = jdbc.queryForList("""
                    SELECT s.id, s.name, s.file_name AS "fileName", s.version_no AS "versionNo",
                           s.previous_version_id AS "previousVersionId", s.created_at AS "createdAt",
                           (SELECT count(*) FROM topic t JOIN unit u ON u.id = t.unit_id
                              JOIN subject sb ON sb.id = u.subject_id WHERE sb.syllabus_id = s.id) AS "topicCount"
                    FROM syllabus s WHERE s.id = ?
                    """, current);
            if (rows.isEmpty()) break;
            chain.add(rows.get(0));
            Object previous = rows.get(0).get("previousVersionId");
            current = previous == null ? null : ((Number) previous).longValue();
        }
        return chain;
    }

    /** Every saved run of this job against the given syllabus versions, oldest first. */
    public List<Map<String, Object>> reportHistory(List<Long> syllabusIds, Long jobId) {
        if (syllabusIds.isEmpty()) return List.of();
        String in = String.join(",", syllabusIds.stream().map(id -> "?").toList());
        List<Object> args = new ArrayList<>(syllabusIds);
        args.add(jobId);
        return jdbc.queryForList("""
                SELECT r.id, r.syllabus_id AS "syllabusId", s.version_no AS "versionNo", r.coverage,
                       r.total_skills AS "totalSkills", r.matched_skills AS "matchedSkills",
                       r.partial_skills AS "partialSkills", r.gap_skills AS "gapSkills",
                       r.algo_version AS "algoVersion", r.created_at AS "createdAt"
                FROM report_snapshot r JOIN syllabus s ON s.id = r.syllabus_id
                WHERE r.syllabus_id IN (%s) AND r.job_id = ?
                ORDER BY r.created_at
                """.formatted(in), args.toArray());
    }

    /**
     * Topics added / removed per subject between two syllabi, and (with a job) the coverage of
     * each and the skills whose status changed.
     */
    public Map<String, Object> diff(Long olderId, Long newerId, Long jobId) {
        Map<String, Set<String>> older = topics(olderId);
        Map<String, Set<String>> newer = topics(newerId);
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("olderId", olderId);
        view.put("newerId", newerId);
        view.put("subjects", topicDiff(older, newer));

        if (jobId != null) {
            SemanticGapReport before = analysisResultService.getReport(jobId, olderId, false);
            SemanticGapReport after = analysisResultService.getReport(jobId, newerId, false);
            view.put("coverageBefore", before.getCoveragePercentage());
            view.put("coverageAfter", after.getCoveragePercentage());
            view.put("skillChanges", skillChanges(before, after));
        }
        return view;
    }

    /** subject name → topic names, both normalised for comparison but shown as written. */
    private Map<String, Set<String>> topics(Long syllabusId) {
        Map<String, Set<String>> bySubject = new TreeMap<>();
        jdbc.query("""
                SELECT sb.name AS subject, t.name AS topic FROM topic t
                JOIN unit u ON u.id = t.unit_id JOIN subject sb ON sb.id = u.subject_id
                WHERE sb.syllabus_id = ?
                """, rs -> {
            bySubject.computeIfAbsent(clean(rs.getString("subject")), k -> new java.util.TreeSet<>())
                    .add(clean(rs.getString("topic")));
        }, syllabusId);
        return bySubject;
    }

    static List<Map<String, Object>> topicDiff(Map<String, Set<String>> older, Map<String, Set<String>> newer) {
        Map<String, String> olderKeys = keys(older.keySet());
        Map<String, String> newerKeys = keys(newer.keySet());
        Set<String> all = new java.util.TreeSet<>(olderKeys.keySet());
        all.addAll(newerKeys.keySet());
        List<Map<String, Object>> rows = new ArrayList<>();
        for (String key : all) {
            Set<String> before = olderKeys.containsKey(key) ? older.get(olderKeys.get(key)) : Set.of();
            Set<String> after = newerKeys.containsKey(key) ? newer.get(newerKeys.get(key)) : Set.of();
            Map<String, String> beforeKeys = keys(before);
            Map<String, String> afterKeys = keys(after);
            List<String> added = afterKeys.entrySet().stream()
                    .filter(e -> !beforeKeys.containsKey(e.getKey())).map(Map.Entry::getValue).toList();
            List<String> removed = beforeKeys.entrySet().stream()
                    .filter(e -> !afterKeys.containsKey(e.getKey())).map(Map.Entry::getValue).toList();
            if (added.isEmpty() && removed.isEmpty()) continue;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("subject", newerKeys.containsKey(key) ? newerKeys.get(key) : olderKeys.get(key));
            row.put("status", !olderKeys.containsKey(key) ? "NEW" : !newerKeys.containsKey(key) ? "REMOVED" : "CHANGED");
            row.put("added", added);
            row.put("removed", removed);
            rows.add(row);
        }
        return rows;
    }

    static List<Map<String, Object>> skillChanges(SemanticGapReport before, SemanticGapReport after) {
        Map<String, String> old = new LinkedHashMap<>();
        before.getSkillDetails().forEach(m -> old.put(m.getSkill().toLowerCase(Locale.ROOT), m.getStatus()));
        List<Map<String, Object>> changes = new ArrayList<>();
        for (SemanticSkillMatch m : after.getSkillDetails()) {
            String was = old.get(m.getSkill().toLowerCase(Locale.ROOT));
            if (was != null && !was.equals(m.getStatus())) {
                changes.add(Map.of("skill", m.getSkill(), "before", was, "after", m.getStatus()));
            }
        }
        return changes;
    }

    private static Map<String, String> keys(Set<String> names) {
        Map<String, String> map = new TreeMap<>();
        for (String name : names) map.putIfAbsent(name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim(), name);
        return map;
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }
}
