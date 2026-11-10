package com.campuspulse.service.analysis;

import com.campuspulse.dto.matching.SemanticGapReport;
import com.campuspulse.dto.matching.SemanticSkillMatch;
import com.campuspulse.entity.job.JobPosting;
import com.campuspulse.service.job.JobPostingService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * One syllabus compared with several jobs at once. Uses each job's saved gap report
 * (AnalysisResultService), so nothing is recomputed when results already exist.
 * A skill gets the same status for every job (status depends on skill + syllabus only).
 */
@Service
public class MultiRoleReportService {

    public static final int MAX_JOBS = 10;

    public record JobSummary(Long jobId, String title, String company, double coverage, int totalSkills) {
    }

    public record SkillRow(String skill, String status, String topic, List<Long> jobIds, int jobCount) {
    }

    public record MultiRoleReport(
            Long syllabusId,
            List<JobSummary> jobs,
            List<SkillRow> skills,
            double combinedCoverage,
            List<String> topGaps) {
    }

    private final AnalysisResultService analysisResultService;
    private final JobPostingService jobPostingService;

    public MultiRoleReportService(
            AnalysisResultService analysisResultService,
            JobPostingService jobPostingService) {
        this.analysisResultService = analysisResultService;
        this.jobPostingService = jobPostingService;
    }

    /** Caller must have checked the syllabus belongs to the user; jobs are checked here. */
    public MultiRoleReport build(Long syllabusId, List<Long> jobIds, Long userId) {
        List<Long> distinct = jobIds.stream().distinct().toList();
        if (distinct.isEmpty()) {
            throw new IllegalArgumentException("Choose at least one job.");
        }
        if (distinct.size() > MAX_JOBS) {
            throw new IllegalArgumentException("Compare at most " + MAX_JOBS + " jobs at once.");
        }

        List<JobSummary> jobs = new ArrayList<>();
        List<SemanticGapReport> reports = new ArrayList<>();
        for (Long jobId : distinct) {
            JobPosting job = jobPostingService.requireAccessibleJob(jobId, userId);
            SemanticGapReport report = analysisResultService.getReport(jobId, syllabusId, false);
            reports.add(report);
            jobs.add(new JobSummary(jobId, job.getTitle(), job.getCompany(),
                    report.getCoveragePercentage(), report.getTotalSkills()));
        }
        return combine(syllabusId, jobs, reports);
    }

    static MultiRoleReport combine(Long syllabusId, List<JobSummary> jobs, List<SemanticGapReport> reports) {
        Map<String, SkillRow> rows = new LinkedHashMap<>();
        for (SemanticGapReport report : reports) {
            for (SemanticSkillMatch match : report.getSkillDetails()) {
                String key = match.getSkill().trim().toLowerCase(Locale.ROOT);
                SkillRow existing = rows.get(key);
                List<Long> ids = existing == null ? new ArrayList<>() : new ArrayList<>(existing.jobIds());
                if (!ids.contains(report.getJobId())) {
                    ids.add(report.getJobId());
                }
                rows.put(key, new SkillRow(
                        existing == null ? match.getSkill() : existing.skill(),
                        existing == null ? match.getStatus() : existing.status(),
                        existing == null ? match.getTopic() : existing.topic(),
                        ids,
                        ids.size()));
            }
        }

        // Skills wanted by more of the selected jobs count more.
        double weighted = 0;
        double weights = 0;
        for (SkillRow row : rows.values()) {
            weights += row.jobCount();
            weighted += row.jobCount() * switch (row.status()) {
                case "MATCHED" -> 1.0;
                case "PARTIAL" -> 0.5;
                default -> 0.0;
            };
        }
        double combined = weights == 0 ? 0 : Math.round(weighted / weights * 10000) / 100.0;

        List<SkillRow> sorted = rows.values().stream()
                .sorted(Comparator.comparingInt(SkillRow::jobCount).reversed()
                        .thenComparing(SkillRow::skill, String.CASE_INSENSITIVE_ORDER))
                .toList();
        List<String> topGaps = sorted.stream()
                .filter(row -> "GAP".equals(row.status()))
                .limit(10)
                .map(SkillRow::skill)
                .toList();

        return new MultiRoleReport(syllabusId, jobs, sorted, combined, topGaps);
    }
}
