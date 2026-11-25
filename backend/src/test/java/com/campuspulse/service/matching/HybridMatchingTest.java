package com.campuspulse.service.matching;

import com.campuspulse.dto.matching.SemanticSkillMatch;
import com.campuspulse.repository.embedding.VectorSearchRepository;
import com.campuspulse.service.job.SkillAliasService;
import com.campuspulse.support.AppDefaults;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HybridMatchingTest {

    /** The configured cut-offs (application.properties), not hard-coded copies. */
    private static final double MATCHED = AppDefaults.getDouble("matching.matched-threshold");
    private static final double PARTIAL = AppDefaults.getDouble("matching.partial-threshold");

    private static final List<KeywordTopicMatcher.TopicText> TOPICS = List.of(
            new KeywordTopicMatcher.TopicText(1L, "Introduction to Java", "Classes and objects"),
            new KeywordTopicMatcher.TopicText(2L, "Web services", "Building a REST API with JSON"),
            new KeywordTopicMatcher.TopicText(3L, "Compiler design", "Lexical analysis in C"));

    @Test
    void keywordFoundInTopicName() {
        var hit = KeywordTopicMatcher.find("Java", TOPICS).orElseThrow();
        assertEquals(1L, hit.topicId());
        assertTrue(hit.inName());
    }

    @Test
    void keywordOnlyInDescriptionIsWeaker() {
        var hit = KeywordTopicMatcher.find("REST API", TOPICS).orElseThrow();
        assertEquals(2L, hit.topicId());
        assertEquals(false, hit.inName());
    }

    @Test
    void wordBoundariesAndShortSkills() {
        // "Java" must not match "JavaScript"; "C" alone only matches a topic named "C".
        assertTrue(KeywordTopicMatcher.find("JavaScript", TOPICS).isEmpty());
        assertTrue(KeywordTopicMatcher.find("C", TOPICS).isEmpty());
        assertTrue(KeywordTopicMatcher.find("Node.js",
                List.of(new KeywordTopicMatcher.TopicText(9L, "Server side with Node.js", ""))).isPresent());
    }

    @Test
    void keywordRaisesLowSemanticScore() {
        // Below the partial threshold the semantic search alone says GAP; the keyword lifts it.
        SemanticMatchingService service = serviceReturning(PARTIAL - 0.05, 3L, "Compiler design");

        SemanticSkillMatch named = service.matchOne("Java", "[0]", 1L, TOPICS);
        assertEquals("MATCHED", named.getStatus());
        assertEquals("KEYWORD", named.getMatchedBy());
        assertEquals("Introduction to Java", named.getTopic());

        SemanticSkillMatch described = service.matchOne("REST API", "[0]", 1L, TOPICS);
        assertEquals("PARTIAL", described.getStatus());
        assertEquals("KEYWORD", described.getMatchedBy());
        assertEquals("Web services", described.getTopic());

        SemanticSkillMatch missing = service.matchOne("Docker", "[0]", 1L, TOPICS);
        assertEquals("GAP", missing.getStatus());
        assertEquals(null, missing.getTopic());
    }

    @Test
    void keywordInNameUpgradesSemanticPartial() {
        // Between the thresholds: semantic PARTIAL; a title keyword raises it, a description keyword does not.
        SemanticMatchingService service = serviceReturning(0.30, 3L, "Compiler design");

        SemanticSkillMatch named = service.matchOne("Java", "[0]", 1L, TOPICS);
        assertEquals("MATCHED", named.getStatus());
        assertEquals("KEYWORD", named.getMatchedBy());

        SemanticSkillMatch described = service.matchOne("REST API", "[0]", 1L, TOPICS);
        assertEquals("PARTIAL", described.getStatus());
        assertEquals("SEMANTIC", described.getMatchedBy(), "keyword PARTIAL is not stronger than semantic PARTIAL");

        SemanticSkillMatch docker = service.matchOne("Docker", "[0]", 1L, TOPICS);
        assertEquals("PARTIAL", docker.getStatus());
        assertEquals("Compiler design", docker.getTopic());
    }

    @Test
    void configuredThresholdsDecideTheSemanticStatus() {
        assertEquals(0.50, MATCHED, "matching.matched-threshold in application.properties");
        assertEquals(0.25, PARTIAL, "matching.partial-threshold in application.properties");
        assertEquals("MATCHED", serviceReturning(MATCHED, 3L, "Compiler design").matchOne("Docker", "[0]", 1L, TOPICS).getStatus());
        assertEquals("PARTIAL", serviceReturning(MATCHED - 0.01, 3L, "Compiler design").matchOne("Docker", "[0]", 1L, TOPICS).getStatus());
        assertEquals("PARTIAL", serviceReturning(PARTIAL, 3L, "Compiler design").matchOne("Docker", "[0]", 1L, TOPICS).getStatus());
        assertEquals("GAP", serviceReturning(PARTIAL - 0.01, 3L, "Compiler design").matchOne("Docker", "[0]", 1L, TOPICS).getStatus());
    }

    @Test
    void semanticMatchIsKeptWhenStronger() {
        SemanticMatchingService service = serviceReturning(0.82, 2L, "Web services");
        SemanticSkillMatch match = service.matchOne("REST API", "[0]", 1L, TOPICS);
        assertEquals("MATCHED", match.getStatus());
        assertEquals("SEMANTIC", match.getMatchedBy());
    }

    private static SemanticMatchingService serviceReturning(double similarity, long id, String name) {
        VectorSearchRepository vectors = mock(VectorSearchRepository.class);
        when(vectors.findSimilarTopicsByEmbedding(any(), anyLong(), anyInt()))
                .thenReturn(List.of(Map.of("id", id, "name", name, "similarity", similarity)));
        return new SemanticMatchingService(
                null, vectors, null, new SkillAliasService(), null, MATCHED, PARTIAL);
    }
}
