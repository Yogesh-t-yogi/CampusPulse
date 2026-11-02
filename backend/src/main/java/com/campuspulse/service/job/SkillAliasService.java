package com.campuspulse.service.job;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class SkillAliasService {

    private static final Pattern NON_ALNUM = Pattern.compile("[^a-z0-9]+");

    private static final Map<String, String> ALIASES = new LinkedHashMap<>();

    static {
        ALIASES.put("js", "JavaScript");
        ALIASES.put("javascript", "JavaScript");
        ALIASES.put("ecmascript", "JavaScript");
        ALIASES.put("ts", "TypeScript");
        ALIASES.put("typescript", "TypeScript");
        ALIASES.put("postgres", "PostgreSQL");
        ALIASES.put("postgresql", "PostgreSQL");
        ALIASES.put("sql", "SQL");
        ALIASES.put("db", "Database");
        ALIASES.put("database", "Database");
        ALIASES.put("node", "Node.js");
        ALIASES.put("nodejs", "Node.js");
        ALIASES.put("node js", "Node.js");
        ALIASES.put("reactjs", "React");
        ALIASES.put("react", "React");
        ALIASES.put("aws", "AWS");
        ALIASES.put("docker", "Docker");
        ALIASES.put("kubernetes", "Kubernetes");
        ALIASES.put("ml", "Machine Learning");
        ALIASES.put("data science", "Data Science");
        ALIASES.put("data-science", "Data Science");
        ALIASES.put("qa", "Quality Assurance");
        ALIASES.put("uiux", "UI/UX");
        ALIASES.put("ux", "UX");
        ALIASES.put("ui", "UI");
        ALIASES.put("ci cd", "CI/CD");
        ALIASES.put("rest api", "REST API");
        ALIASES.put("rest apis", "REST API");
    }

    public String normalizeSkill(String skill) {
        if (skill == null) {
            return "";
        }

        String cleaned = skill.trim();
        if (cleaned.isEmpty()) {
            return "";
        }

        String key = NON_ALNUM.matcher(cleaned.toLowerCase(Locale.ROOT))
                .replaceAll(" ")
                .trim();

        if (key.isEmpty()) {
            return cleaned;
        }

        String canonical = ALIASES.getOrDefault(key, cleaned);
        String normalized = canonical.trim();
        if (normalized.isEmpty()) {
            return cleaned;
        }

        if (normalized.equalsIgnoreCase("sql")) {
            return "SQL";
        }

        return normalized;
    }

    public boolean isMeaningfulSkill(String skill) {
        return skill != null && !skill.isBlank() && !skill.equals("-");
    }
}
