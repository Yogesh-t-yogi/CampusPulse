package com.campuspulse.service.org;

import com.campuspulse.dto.matching.SemanticGapReport;
import com.campuspulse.dto.matching.SemanticSkillMatch;
import com.campuspulse.entity.job.JobPosting;
import com.campuspulse.entity.org.Department;
import com.campuspulse.entity.user.AppUser;
import com.campuspulse.repository.job.JobRequiredSkillRepository;
import com.campuspulse.repository.org.DepartmentRepository;
import com.campuspulse.repository.user.AppUserRepository;
import com.campuspulse.service.analysis.AnalysisResultService;
import com.campuspulse.service.job.JobPostingService;
import com.campuspulse.service.student.StudentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Department / college dashboard (Phase 6.6): syllabus coverage against benchmark roles,
 * most common gaps, students' readiness and popular target roles.
 * Scope: HOD and faculty → own department; college admin / platform admin → whole college
 * or one department.
 */
@Service
public class DashboardService {

    static final int MAX_SYLLABI = 12;
    static final int MAX_JOBS = 5;
    static final int MAX_STUDENTS = 300;
    private static final Logger log = LoggerFactory.getLogger(DashboardService.class);

    private final JdbcTemplate jdbc;
    private final TenantService tenantService;
    private final DepartmentRepository departments;
    private final AppUserRepository users;
    private final JobPostingService jobPostingService;
    private final JobRequiredSkillRepository jobSkills;
    private final AnalysisResultService analysisResultService;
    private final StudentService studentService;

    public DashboardService(
            JdbcTemplate jdbc,
            TenantService tenantService,
            DepartmentRepository departments,
            AppUserRepository users,
            JobPostingService jobPostingService,
            JobRequiredSkillRepository jobSkills,
            AnalysisResultService analysisResultService,
            StudentService studentService) {
        this.jdbc = jdbc;
        this.tenantService = tenantService;
        this.departments = departments;
        this.users = users;
        this.jobPostingService = jobPostingService;
        this.jobSkills = jobSkills;
        this.analysisResultService = analysisResultService;
        this.studentService = studentService;
    }

    public Map<String, Object> overview(AppUser actor, Long requestedDepartment, List<Long> requestedJobs) {
        Long college = tenantService.actingInstitutionId(actor);
        Long departmentId = scope(actor, college, requestedDepartment);

        // ---- syllabi in scope (processed, newest first)
        List<Map<String, Object>> syllabi = jdbc.queryForList("""
                SELECT s.id, s.name, s.semester, p.name AS "programName", d.name AS "departmentName",
                       (SELECT count(*) FROM topic t JOIN unit u ON u.id = t.unit_id
                          JOIN subject sb ON sb.id = u.subject_id WHERE sb.syllabus_id = s.id) AS "topicCount"
                FROM syllabus s
                LEFT JOIN program p ON p.id = s.program_id
                LEFT JOIN department d ON d.id = s.department_id
                WHERE s.institution_id = ? AND (CAST(? AS BIGINT) IS NULL OR s.department_id = ?)
                ORDER BY s.created_at DESC
                """, college, departmentId, departmentId);
        int syllabusTotal = syllabi.size();
        List<Map<String, Object>> processed = syllabi.stream()
                .filter(row -> ((Number) row.get("topicCount")).intValue() > 0)
                .limit(MAX_SYLLABI)
                .toList();

        // ---- students in scope
        List<AppUser> students = users.findAll().stream()
                .filter(u -> "STUDENT".equals(u.getRole()))
                .filter(u -> Objects.equals(u.getInstitutionId(), college))
                .filter(u -> departmentId == null || Objects.equals(u.getDepartmentId(), departmentId))
                .limit(MAX_STUDENTS)
                .toList();

        // ---- popular target roles among those students
        List<Map<String, Object>> popular = students.isEmpty() ? List.of() : jdbc.queryForList("""
                SELECT j.id AS "jobId", j.title, count(*) AS "students"
                FROM student_target_role t JOIN job_posting j ON j.id = t.job_id
                JOIN app_user u ON u.id = t.user_id
                WHERE u.role = 'STUDENT' AND u.institution_id = ? AND (CAST(? AS BIGINT) IS NULL OR u.department_id = ?)
                GROUP BY j.id, j.title ORDER BY count(*) DESC, j.title LIMIT 10
                """, college, departmentId, departmentId);

        // ---- benchmark jobs: requested, else most targeted, then catalog jobs with most skills
        List<JobPosting> visible = jobPostingService.getAccessibleJobs(actor.getId());
        Map<Long, JobPosting> visibleById = new HashMap<>();
        visible.forEach(job -> visibleById.put(job.getId(), job));
        List<Long> jobIds = new ArrayList<>();
        if (requestedJobs != null && !requestedJobs.isEmpty()) {
            requestedJobs.stream().filter(visibleById::containsKey).distinct().limit(MAX_JOBS).forEach(jobIds::add);
        } else {
            for (Map<String, Object> row : popular) {
                Long id = ((Number) row.get("jobId")).longValue();
                if (visibleById.containsKey(id) && jobIds.size() < MAX_JOBS) jobIds.add(id);
            }
            if (jobIds.size() < MAX_JOBS) {
                Map<Long, Integer> skillCount = new HashMap<>();
                jobSkills.findByJobPostingIdIn(visible.stream().map(JobPosting::getId).toList())
                        .forEach(s -> skillCount.merge(s.getJobPosting().getId(), 1, Integer::sum));
                visible.stream()
                        .filter(job -> job.getOwner() == null && !jobIds.contains(job.getId()))
                        .sorted((a, b) -> skillCount.getOrDefault(b.getId(), 0) - skillCount.getOrDefault(a.getId(), 0))
                        .limit(MAX_JOBS - jobIds.size())
                        .forEach(job -> jobIds.add(job.getId()));
            }
        }
        List<Map<String, Object>> jobs = jobIds.stream().map(id -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("jobId", id);
            row.put("title", visibleById.get(id).getTitle());
            return row;
        }).toList();

        // ---- coverage grid (saved reports; computed once) and common gaps
        Map<String, Integer> gapCounts = new HashMap<>();
        Map<String, String> gapNames = new HashMap<>();
        List<Map<String, Object>> grid = new ArrayList<>();
        double coverageSum = 0;
        int coverageCells = 0;
        for (Map<String, Object> syllabus : processed) {
            Long syllabusId = ((Number) syllabus.get("id")).longValue();
            Map<String, Object> row = new LinkedHashMap<>(syllabus);
            Map<String, Object> cells = new LinkedHashMap<>();
            for (Long jobId : jobIds) {
                try {
                    SemanticGapReport report = analysisResultService.getReport(jobId, syllabusId, false);
                    cells.put(String.valueOf(jobId), report.getCoveragePercentage());
                    coverageSum += report.getCoveragePercentage();
                    coverageCells++;
                    for (SemanticSkillMatch match : report.getSkillDetails()) {
                        if ("GAP".equals(match.getStatus())) {
                            String key = match.getSkill().toLowerCase(Locale.ROOT);
                            gapNames.putIfAbsent(key, match.getSkill());
                            gapCounts.merge(key, 1, Integer::sum);
                        }
                    }
                } catch (RuntimeException e) {
                    log.warn("Dashboard: no report for syllabus {} job {}: {}", syllabusId, jobId, e.getMessage());
                    cells.put(String.valueOf(jobId), null);
                }
            }
            row.put("coverage", cells);
            grid.add(row);
        }
        List<Map<String, Object>> topGaps = gapCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .limit(12)
                .map(entry -> Map.<String, Object>of("skill", gapNames.get(entry.getKey()), "reports", entry.getValue()))
                .toList();

        // ---- students' readiness
        List<Map<String, Object>> studentRows = new ArrayList<>();
        int ready = 0, growing = 0, needHelp = 0, noTargets = 0;
        for (AppUser student : students) {
            Map<String, Object> summary = studentService.readinessSummary(student);
            double best = ((Number) summary.get("best")).doubleValue();
            if (best < 0) noTargets++;
            else if (best >= 70) ready++;
            else if (best >= 40) growing++;
            else needHelp++;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", student.getId());
            row.put("name", student.getName());
            row.put("rollNumber", student.getRollNumber());
            row.put("batchYear", student.getBatchYear());
            row.putAll(summary);
            studentRows.add(row);
        }
        studentRows.sort((a, b) -> Double.compare(((Number) a.get("best")).doubleValue(), ((Number) b.get("best")).doubleValue()));

        Map<String, Object> view = new LinkedHashMap<>();
        view.put("scope", departmentId == null ? "COLLEGE" : "DEPARTMENT");
        view.put("departmentId", departmentId);
        view.put("departmentName", departmentId == null ? null : departments.findById(departmentId).map(Department::getName).orElse(null));
        view.put("syllabusTotal", syllabusTotal);
        view.put("syllabusProcessed", (int) syllabi.stream().filter(r -> ((Number) r.get("topicCount")).intValue() > 0).count());
        view.put("averageCoverage", coverageCells == 0 ? null : Math.round(coverageSum / coverageCells * 10) / 10.0);
        view.put("jobs", jobs);
        view.put("coverage", grid);
        view.put("topGaps", topGaps);
        view.put("popularRoles", popular);
        view.put("studentCount", students.size());
        view.put("readinessBands", Map.of("ready", ready, "growing", growing, "needHelp", needHelp, "noTargets", noTargets));
        view.put("students", studentRows);
        return view;
    }

    /** Department the dashboard covers (null = whole college). */
    private Long scope(AppUser actor, Long college, Long requested) {
        String role = actor.getRole();
        if ("HOD".equals(role) || "FACULTY".equals(role)) {
            if (actor.getDepartmentId() == null) {
                throw TenantService.forbidden("You aren't in a department yet. Ask your college admin.");
            }
            return actor.getDepartmentId();
        }
        if (!"ADMIN".equals(role) && !"SUPER_ADMIN".equals(role)) {
            throw TenantService.forbidden("The dashboard is for staff.");
        }
        if (requested == null) return null;
        Department department = departments.findById(requested)
                .filter(d -> Objects.equals(d.getInstitutionId(), college))
                .orElseThrow(() -> new IllegalArgumentException("Choose a department of this college."));
        return department.getId();
    }
}
