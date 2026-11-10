package com.campuspulse.service.ai;

import com.campuspulse.dto.ai.AiSubject;
import com.campuspulse.dto.ai.AiSyllabusResponse;
import com.campuspulse.dto.ai.AiTopic;
import com.campuspulse.dto.ai.AiUnit;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Long syllabi are sent to the AI in parts so the answer is not cut off.
 * split() cuts text at line breaks, preferring "Module 3" / "Unit 2" / heading lines;
 * merge() joins the per-part results back into one structure.
 */
public final class SyllabusChunker {

    /** Above this many characters the text is split. */
    public static final int MAX_CHARS = 12_000;

    // A line that starts a new section: "Module 1", "UNIT - II", "Course Code:", ...
    private static final Pattern SECTION_START = Pattern.compile(
            "^\\s*(module|unit|chapter|part|course\\s*(code|title|name)|subject\\s*(code|name))\\b.*",
            Pattern.CASE_INSENSITIVE);

    private SyllabusChunker() {
    }

    public static List<String> split(String text) {
        return split(text, MAX_CHARS);
    }

    static List<String> split(String text, int maxChars) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return chunks;
        }
        if (text.length() <= maxChars) {
            chunks.add(text);
            return chunks;
        }

        String[] lines = text.split("\\R");
        StringBuilder current = new StringBuilder();
        for (String line : lines) {
            boolean wouldOverflow = current.length() + line.length() + 1 > maxChars;
            // Past 60% of the size, start a new part at the next section heading.
            boolean goodBreak = current.length() > maxChars * 0.6
                    && SECTION_START.matcher(line).matches();
            if (current.length() > 0 && (wouldOverflow || goodBreak)) {
                chunks.add(current.toString());
                current.setLength(0);
            }
            // A single very long line is cut hard.
            while (line.length() > maxChars) {
                chunks.add(line.substring(0, maxChars));
                line = line.substring(maxChars);
            }
            current.append(line).append('\n');
        }
        if (!current.toString().isBlank()) {
            chunks.add(current.toString());
        }
        return chunks;
    }

    /**
     * Joins part results: subjects with the same name (or code) become one subject;
     * a unit that continues across parts (same number and name) gets its topics merged;
     * duplicate topic names inside a unit are dropped.
     */
    public static AiSyllabusResponse merge(List<AiSyllabusResponse> parts) {
        Map<String, AiSubject> subjects = new LinkedHashMap<>();

        for (AiSyllabusResponse part : parts) {
            if (part == null || part.getSubjects() == null) {
                continue;
            }
            for (AiSubject subject : part.getSubjects()) {
                String key = subjectKey(subject);
                AiSubject target = subjects.get(key);
                if (target == null) {
                    target = new AiSubject();
                    target.setName(subject.getName());
                    target.setCode(subject.getCode());
                    target.setUnits(new ArrayList<>());
                    subjects.put(key, target);
                } else if (isBlank(target.getCode()) && !isBlank(subject.getCode())) {
                    target.setCode(subject.getCode());
                }
                mergeUnits(target.getUnits(), subject.getUnits());
            }
        }

        AiSyllabusResponse merged = new AiSyllabusResponse();
        merged.setSubjects(new ArrayList<>(subjects.values()));
        return merged;
    }

    private static void mergeUnits(List<AiUnit> into, List<AiUnit> units) {
        if (units == null) {
            return;
        }
        for (AiUnit unit : units) {
            AiUnit same = into.stream()
                    .filter(existing -> java.util.Objects.equals(existing.getUnitNumber(), unit.getUnitNumber())
                            && norm(existing.getName()).equals(norm(unit.getName())))
                    .findFirst()
                    .orElse(null);
            if (same == null) {
                AiUnit copy = new AiUnit();
                copy.setUnitNumber(unit.getUnitNumber());
                copy.setName(unit.getName());
                copy.setTopics(new ArrayList<>());
                into.add(copy);
                same = copy;
            }
            if (unit.getTopics() != null) {
                for (AiTopic topic : unit.getTopics()) {
                    boolean duplicate = same.getTopics().stream()
                            .anyMatch(existing -> norm(existing.getName()).equals(norm(topic.getName())));
                    if (!duplicate) {
                        same.getTopics().add(topic);
                    }
                }
            }
        }
    }

    private static String subjectKey(AiSubject subject) {
        String name = norm(subject.getName());
        return name.isEmpty() ? "code:" + norm(subject.getCode()) : name;
    }

    private static String norm(String value) {
        return value == null
                ? ""
                : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
