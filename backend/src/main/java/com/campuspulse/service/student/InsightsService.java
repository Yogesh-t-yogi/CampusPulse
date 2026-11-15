package com.campuspulse.service.student;

import com.campuspulse.dto.matching.SemanticSkillMatch;
import com.campuspulse.entity.job.JobPosting;
import com.campuspulse.entity.job.JobRequiredSkill;
import com.campuspulse.entity.syllabus.Syllabus;
import com.campuspulse.entity.user.AppUser;
import com.campuspulse.repository.job.JobRequiredSkillRepository;
import com.campuspulse.service.job.JobPostingService;
import com.campuspulse.service.job.SkillAliasService;
import com.campuspulse.service.matching.SemanticMatchingService;
import com.campuspulse.service.syllabus.SyllabusService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Market insights (plan 13 §5): demand by skill, role and location over the jobs the user can see,
 * with optional location / keyword filters, and "your gaps vs the market" for the top skills.
 */
@Service
public class InsightsService {

    private static final int TOP_SKILLS = 20;

    private final JobPostingService jobPostingService;
    private final JobRequiredSkillRepository jobSkills;
    private final SkillAliasService skillAliasService;
    private final SemanticMatchingService semanticMatchingService;
    private final SyllabusService syllabusService;
    private final StudentService studentService;

    public InsightsService(
            JobPostingService jobPostingService,
            JobRequiredSkillRepository jobSkills,
            SkillAliasService skillAliasService,
            SemanticMatchingService semanticMatchingService,
            SyllabusService syllabusService,
            StudentService studentService) {
        this.jobPostingService = jobPostingService;
        this.jobSkills = jobSkills;
        this.skillAliasService = skillAliasService;
        this.semanticMatchingService = semanticMatchingService;
        this.syllabusService = syllabusService;
        this.studentService = studentService;
    }

    public Map<String, Object> market(AppUser user, String location, String keyword) {
        List<JobPosting> all = jobPostingService.getAccessibleJobs(user.getId());
        List<JobPosting> jobs = filter(all, location, keyword);

        Map<Long, List<String>> skillsByJob = new HashMap<>();
        if (!jobs.isEmpty()) {
            for (JobRequiredSkill skill : jobSkills.findByJobPostingIdIn(jobs.stream().map(JobPosting::getId).toList())) {
                skillsByJob.computeIfAbsent(skill.getJobPosting().getId(), id -> new ArrayList<>()).add(skill.getSkill());
            }
        }

        Map<String, String> display = new HashMap<>();
        Map<String, Long> skillCounts = new HashMap<>();
        for (List<String> skills : skillsByJob.values()) {
            for (String skill : skills.stream().map(skillAliasService::normalizeSkill).distinct().toList()) {
                String k = skill.toLowerCase(Locale.ROOT);
                display.putIfAbsent(k, skill);
                skillCounts.merge(k, 1L, Long::sum);
            }
        }

        Map<String, Object> view = new LinkedHashMap<>();
        view.put("totalJobs", jobs.size());
        view.put("skills", top(skillCounts, TOP_SKILLS, display, "skill", "jobCount"));
        view.put("roles", top(count(jobs, JobPosting::getTitle), 10, null, "title", "jobCount"));
        // Locations come from all jobs so the filter list doesn't shrink while filtering.
        view.put("locations", top(count(all, JobPosting::getLocation), 25, null, "location", "jobCount"));
        return view;
    }

    /**
     * Top market skills checked against the user's situation.
     * Students: HAVE / TAUGHT / PARTLY / TO_LEARN (profile + course). Staff: TAUGHT / PARTLY / NOT_TAUGHT
     * against the given syllabus.
     */
    public Map<String, Object> gaps(AppUser user, Long syllabusId, String location, String keyword) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> top = (List<Map<String, Object>>) market(user, location, keyword).get("skills");
        boolean student = "STUDENT".equals(user.getRole());

        Syllabus syllabus = student
                ? studentService.resolveCourse(user)
                : syllabusId == null ? null : syllabusService.getSyllabusById(syllabusId, user.getId()).orElse(null);

        Map<String, SemanticSkillMatch> taught = new HashMap<>();
        if (syllabus != null && !top.isEmpty()) {
            for (SemanticSkillMatch match : semanticMatchingService.matchSkillNames(
                    syllabus.getId(), top.stream().map(row -> row.get("skill").toString()).toList())) {
                taught.put(match.getSkill().toLowerCase(Locale.ROOT), match);
            }
        }
        Set<String> have = student ? studentService.haveKeys(user) : Set.of();

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> row : top) {
            String skill = row.get("skill").toString();
            String k = skill.toLowerCase(Locale.ROOT);
            SemanticSkillMatch match = taught.get(k);
            String status;
            if (student && have.contains(k)) status = "HAVE";
            else if (match != null && "MATCHED".equals(match.getStatus())) status = "TAUGHT";
            else if (match != null && "PARTIAL".equals(match.getStatus())) status = "PARTLY";
            else status = student ? "TO_LEARN" : (syllabus == null ? "UNKNOWN" : "NOT_TAUGHT");
            Map<String, Object> out = new LinkedHashMap<>(row);
            out.put("status", status);
            out.put("topic", match == null ? null : match.getTopic());
            rows.add(out);
        }
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("syllabus", syllabus == null ? null : Map.of("id", syllabus.getId(), "name", syllabus.getName()));
        view.put("perspective", student ? "STUDENT" : "CURRICULUM");
        view.put("skills", rows);
        return view;
    }

    private static List<JobPosting> filter(List<JobPosting> jobs, String location, String keyword) {
        String loc = location == null ? "" : location.trim().toLowerCase(Locale.ROOT);
        String q = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        return jobs.stream()
                .filter(job -> loc.isEmpty() || (job.getLocation() != null && job.getLocation().toLowerCase(Locale.ROOT).contains(loc)))
                .filter(job -> q.isEmpty() || (job.getTitle() + " " + (job.getDescription() == null ? "" : job.getDescription()))
                        .toLowerCase(Locale.ROOT).contains(q))
                .toList();
    }

    private static Map<String, Long> count(List<JobPosting> jobs, java.util.function.Function<JobPosting, String> field) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (JobPosting job : jobs) {
            String value = field.apply(job);
            if (value != null && !value.isBlank()) counts.merge(value.trim(), 1L, Long::sum);
        }
        return counts;
    }

    private static List<Map<String, Object>> top(Map<String, Long> counts, int limit, Map<String, String> display,
                                                 String nameKey, String countKey) {
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey(String.CASE_INSENSITIVE_ORDER)))
                .limit(limit)
                .map(entry -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put(nameKey, display == null ? entry.getKey() : display.getOrDefault(entry.getKey(), entry.getKey()));
                    row.put(countKey, entry.getValue());
                    return row;
                })
                .toList();
    }
}
