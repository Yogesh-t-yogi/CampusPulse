package com.campuspulse.service.job;

import com.campuspulse.entity.job.JobPosting;
import com.campuspulse.entity.job.JobRequiredSkill;
import com.campuspulse.repository.job.JobPostingRepository;
import com.campuspulse.repository.job.JobRequiredSkillRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PushbackReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class JobCatalogProvider {

    private static final long MAX_CSV_BYTES = 10 * 1024 * 1024;
    private static final int MAX_IMPORT_ROWS = 10_000;
    private static final int MAX_SKILL_DEMAND_RESULTS = 20;

    private final JobPostingRepository jobPostingRepository;
    private final JobRequiredSkillRepository jobRequiredSkillRepository;
    private final SkillAliasService skillAliasService;

    public JobCatalogProvider(
            JobPostingRepository jobPostingRepository,
            JobRequiredSkillRepository jobRequiredSkillRepository,
            SkillAliasService skillAliasService) {
        this.jobPostingRepository = jobPostingRepository;
        this.jobRequiredSkillRepository = jobRequiredSkillRepository;
        this.skillAliasService = skillAliasService;
    }

    @PostConstruct
    public void seedStarterCatalog() {
        for (StarterJob entry : starterJobs()) {
            if (!jobPostingRepository.existsBySourceAndTitle(
                    "DATASET", entry.title())) {
                createCatalogJob(
                        entry.title(),
                        entry.company(),
                        entry.location(),
                        entry.description(),
                        entry.skills()
                );
            }
        }
    }

    public List<JobPosting> searchJobs(
            String keyword,
            String location,
            int limit) {
        return searchJobs(keyword, location, limit, null);
    }

    public List<JobPosting> searchJobs(
            String keyword,
            String location,
            int limit,
            Long institutionId) {
        String normalizedKeyword = normalizeSearch(keyword);
        String normalizedLocation = normalizeSearch(location);

        return jobPostingRepository
                .findBySourceOrderByCreatedAtDesc("DATASET")
                .stream()
                .filter(job -> job.getInstitutionId() == null
                        || job.getInstitutionId().equals(institutionId))
                .filter(job -> {
                    if (normalizedKeyword.isBlank()) {
                        return true;
                    }
                    String haystack = (job.getTitle() + " "
                            + safe(job.getDescription()) + " "
                            + safe(job.getCompany()))
                            .toLowerCase(Locale.ROOT);
                    return haystack.contains(normalizedKeyword);
                })
                .filter(job -> normalizedLocation.isBlank()
                        || safe(job.getLocation())
                                .toLowerCase(Locale.ROOT)
                                .contains(normalizedLocation))
                .limit(limit > 0 ? limit : 20L)
                .toList();
    }

    /** Rows added, and rows skipped because the same title + company is already in that catalog. */
    public record ImportResult(int imported, int skipped) {
    }

    private boolean catalogHasJob(String title, String company, Long institutionId) {
        return institutionId == null
                ? jobPostingRepository.existsByOwnerIsNullAndInstitutionIdIsNullAndTitleIgnoreCaseAndCompanyIgnoreCase(title.trim(), company)
                : jobPostingRepository.existsByOwnerIsNullAndInstitutionIdAndTitleIgnoreCaseAndCompanyIgnoreCase(institutionId, title.trim(), company);
    }

    @Transactional
    public ImportResult importCsv(MultipartFile file) throws IOException {
        return importCsv(file, null);
    }

    @Transactional
    public ImportResult importCsv(MultipartFile file, Long institutionId) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException(
                    "CSV file is required for import."
            );
        }
        if (file.getSize() > MAX_CSV_BYTES) {
            throw new IllegalArgumentException(
                    "CSV files must be 10 MB or smaller."
            );
        }
        String filename = file.getOriginalFilename();
        if (filename != null
                && !filename.toLowerCase(Locale.ROOT).endsWith(".csv")) {
            throw new IllegalArgumentException("Upload a .csv file.");
        }

        int imported = 0;
        int skipped = 0;
        java.util.Set<String> seenInFile = new java.util.HashSet<>();
        try (PushbackReader reader = new PushbackReader(
                new BufferedReader(new InputStreamReader(
                        file.getInputStream(), StandardCharsets.UTF_8)), 1)) {
            List<String> headers = readCsvRecord(reader);
            if (headers == null || headers.isEmpty()) {
                throw new IllegalArgumentException("CSV file is empty.");
            }
            headers.set(0, headers.get(0).replaceFirst("^\uFEFF", ""));
            if (headers.stream().noneMatch(this::isTitleHeader)) {
                throw new IllegalArgumentException(
                        "CSV must include a title, job_title, or role column."
                );
            }

            List<String> values;
            int rowCount = 0;
            while ((values = readCsvRecord(reader)) != null) {
                if (values.stream().allMatch(String::isBlank)) {
                    continue;
                }
                if (++rowCount > MAX_IMPORT_ROWS) {
                    throw new IllegalArgumentException(
                            "CSV imports are limited to 10,000 rows."
                    );
                }

                Map<String, String> row = new HashMap<>();
                for (int i = 0; i < Math.min(headers.size(), values.size()); i++) {
                    row.put(
                            headers.get(i).trim().toLowerCase(Locale.ROOT),
                            values.get(i).trim()
                    );
                }

                String title = firstNonBlank(
                        row.get("title"), row.get("job_title"), row.get("role"));
                if (title == null || title.length() > 200) {
                    continue;
                }
                String company = firstNonBlank(
                        row.get("company"), row.get("employer"), "Dataset Company");
                String location = firstNonBlank(
                        row.get("location"), row.get("city"), row.get("place"),
                        "Bengaluru");
                String description = firstNonBlank(
                        row.get("description"), row.get("job_description"),
                        row.get("summary"), "");
                String rawSkills = firstNonBlank(
                        row.get("skills"), row.get("required_skills"),
                        row.get("skill_set"), "");

                // Re-importing the same file must not duplicate jobs.
                String key = (title + "|" + company).toLowerCase(Locale.ROOT).trim();
                if (!seenInFile.add(key) || catalogHasJob(title, company, institutionId)) {
                    skipped++;
                    continue;
                }
                createCatalogJob(
                        title, company, location, description,
                        parseSkills(rawSkills),
                        institutionId
                );
                imported++;
            }
        }

        return new ImportResult(imported, skipped);
    }

    @Transactional
    public JobPosting createCatalogJob(
            String title,
            String company,
            String location,
            String description,
            List<String> skills) {
        return createCatalogJob(title, company, location, description, skills, null);
    }

    /** institutionId null = global catalog; set = that college's catalog. */
    @Transactional
    public JobPosting createCatalogJob(
            String title,
            String company,
            String location,
            String description,
            List<String> skills,
            Long institutionId) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Job title is required.");
        }

        JobPosting job = new JobPosting();
        job.setTitle(title.trim());
        job.setCompany(blankToNull(company));
        job.setLocation(blankToNull(location));
        job.setDescription(description == null ? "" : description.trim());
        job.setSource("DATASET");
        job.setJobUrl(null);
        job.setOwner(null);
        job.setInstitutionId(institutionId);

        JobPosting savedJob = jobPostingRepository.save(job);
        List<String> normalizedSkills = skills == null ? List.of() : skills.stream()
                .map(skillAliasService::normalizeSkill)
                .filter(skillAliasService::isMeaningfulSkill)
                .distinct()
                .toList();

        if (!normalizedSkills.isEmpty()) {
            List<JobRequiredSkill> requiredSkills = normalizedSkills.stream()
                    .map(skill -> {
                        JobRequiredSkill requiredSkill = new JobRequiredSkill();
                        requiredSkill.setSkill(skill);
                        requiredSkill.setJobPosting(savedJob);
                        return requiredSkill;
                    })
                    .toList();
            jobRequiredSkillRepository.saveAll(requiredSkills);
        }
        return savedJob;
    }

    public List<Map<String, Object>> getSkillDemand(Long institutionId) {
        Map<String, Long> counts = new HashMap<>();
        for (Object[] row : jobRequiredSkillRepository.findDatasetSkillDemand(institutionId)) {
            if (row.length < 2 || row[0] == null || !(row[1] instanceof Number count)) {
                continue;
            }
            String skill = skillAliasService.normalizeSkill(row[0].toString());
            if (skillAliasService.isMeaningfulSkill(skill)) {
                counts.merge(skill, count.longValue(), Long::sum);
            }
        }

        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue(
                                Comparator.reverseOrder())
                        .thenComparing(Map.Entry.comparingByKey(
                                String.CASE_INSENSITIVE_ORDER)))
                .limit(MAX_SKILL_DEMAND_RESULTS)
                .map(entry -> Map.<String, Object>of(
                        "skill", entry.getKey(),
                        "jobCount", entry.getValue()))
                .toList();
    }

    private List<String> parseSkills(String rawSkills) {
        if (rawSkills == null || rawSkills.isBlank()) {
            return List.of();
        }
        return List.of(rawSkills.replace("|", ",")
                        .replace(";", ",")
                        .split(","))
                .stream()
                .map(String::trim)
                .filter(skill -> !skill.isBlank())
                .toList();
    }

    private List<String> readCsvRecord(PushbackReader reader)
            throws IOException {
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        boolean sawCharacter = false;
        int value;

        while ((value = reader.read()) != -1) {
            char character = (char) value;
            sawCharacter = true;
            if (character == '"') {
                if (inQuotes) {
                    int next = reader.read();
                    if (next == '"') {
                        field.append('"');
                    } else {
                        inQuotes = false;
                        if (next != -1) {
                            reader.unread(next);
                        }
                    }
                } else if (field.isEmpty()) {
                    inQuotes = true;
                } else {
                    throw new IllegalArgumentException(
                            "CSV contains a quote in an unquoted field."
                    );
                }
            } else if (character == ',' && !inQuotes) {
                fields.add(field.toString());
                field.setLength(0);
            } else if ((character == '\n' || character == '\r') && !inQuotes) {
                if (character == '\r') {
                    int next = reader.read();
                    if (next != '\n' && next != -1) {
                        reader.unread(next);
                    }
                }
                fields.add(field.toString());
                return fields;
            } else {
                field.append(character);
            }
        }

        if (inQuotes) {
            throw new IllegalArgumentException(
                    "CSV contains an unterminated quoted field."
            );
        }
        if (!sawCharacter && fields.isEmpty() && field.isEmpty()) {
            return null;
        }
        fields.add(field.toString());
        return fields;
    }

    private boolean isTitleHeader(String header) {
        String normalized = header.trim().toLowerCase(Locale.ROOT);
        return normalized.equals("title")
                || normalized.equals("job_title")
                || normalized.equals("role");
    }

    private String normalizeSearch(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private List<StarterJob> starterJobs() {
        return List.of(
                new StarterJob("Java Developer", "CampusPulse Labs", "Bengaluru",
                        "Develop enterprise Java services with Spring Boot, REST APIs, and PostgreSQL integrations.",
                        List.of("Java", "Spring Boot", "PostgreSQL", "REST API", "Git")),
                new StarterJob("Full Stack Developer", "NexaWorks", "Bengaluru",
                        "Build responsive web applications and API integrations using React, Node.js and cloud deployment workflows.",
                        List.of("JavaScript", "React", "Node.js", "REST API", "MongoDB")),
                new StarterJob("Python Developer", "DataMint", "Hyderabad",
                        "Create automation scripts, ETL pipelines, and backend microservices for analytics products.",
                        List.of("Python", "SQL", "FastAPI", "Pandas", "ETL")),
                new StarterJob("Data Analyst", "InsightForge", "Pune",
                        "Analyze business metrics, build dashboards, and convert raw data into actionable recommendations.",
                        List.of("SQL", "Excel", "Power BI", "Python", "Statistics")),
                new StarterJob("DevOps Engineer", "CloudBridge", "Bengaluru",
                        "Maintain CI/CD pipelines, cloud infrastructure, and deployment automation for distributed applications.",
                        List.of("AWS", "Docker", "Kubernetes", "Linux", "CI/CD")),
                new StarterJob("QA Automation Engineer", "TestPilot", "Bengaluru",
                        "Design automated regression tests and validate releases across API and UI flows.",
                        List.of("Java", "Selenium", "TestNG", "API Testing", "Quality Assurance")),
                new StarterJob("Frontend Developer", "PixelCraft", "Chennai",
                        "Build user-facing experiences using modern frontend tooling and reusable component systems.",
                        List.of("HTML", "CSS", "JavaScript", "React", "UI/UX")),
                new StarterJob("Data Engineer", "StreamWorks", "Bengaluru",
                        "Design scalable data pipelines, integration flows, and warehouse models to power analytics.",
                        List.of("Python", "SQL", "ETL", "Spark", "PostgreSQL"))
        );
    }

    private record StarterJob(
            String title,
            String company,
            String location,
            String description,
            List<String> skills) {
    }
}
