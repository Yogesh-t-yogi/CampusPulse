package com.campuspulse.service.matching;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Exact-word evidence: does the skill name appear in a topic's name or description?
 * Used with semantic similarity so an explicit mention is never reported as a gap.
 */
public final class KeywordTopicMatcher {

    public record TopicText(Long id, String name, String description) {
    }

    public record Hit(Long topicId, String topicName, boolean inName) {
    }

    private KeywordTopicMatcher() {
    }

    public static Optional<Hit> find(String skill, List<TopicText> topics) {
        String needle = normalize(skill);
        if (needle.isEmpty()) {
            return Optional.empty();
        }
        // Very short skills (C, R, Go) only count when they are the whole topic name.
        boolean shortSkill = needle.replace(" ", "").length() < 3;
        Pattern word = Pattern.compile("(^| )" + Pattern.quote(needle) + "( |$)");

        Hit descriptionHit = null;
        for (TopicText topic : topics) {
            String name = normalize(topic.name());
            if (shortSkill ? name.equals(needle) : word.matcher(name).find()) {
                return Optional.of(new Hit(topic.id(), topic.name(), true));
            }
            if (!shortSkill && descriptionHit == null
                    && word.matcher(normalize(topic.description())).find()) {
                descriptionHit = new Hit(topic.id(), topic.name(), false);
            }
        }
        return Optional.ofNullable(descriptionHit);
    }

    // Lower case; keep letters, digits and + # . (C++, C#, Node.js); everything else = space.
    static String normalize(String text) {
        if (text == null) {
            return "";
        }
        return text.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9+#.]+", " ")
                .replaceAll("(?<![a-z0-9])\\.|\\.(?![a-z0-9])", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
