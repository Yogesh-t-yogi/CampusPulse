package com.campuspulse.service.matching;

import com.campuspulse.dto.matching.SemanticSkillMatch;
import com.campuspulse.repository.embedding.VectorSearchRepository;
import com.campuspulse.service.job.SkillAliasService;
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
        SemanticMatchingService service = serviceReturning(0.30, 3L, "Compiler design");

        SemanticSkillMatch named = service.matchOne("Java", "[0]", 1L, TOPICS);
        assertEquals("MATCHED", named.getStatus());
        assertEquals("KEYWORD", named.getMatchedBy());
        assertEquals("Introduction to Java", named.getTopic());

        SemanticSkillMatch described = service.matchOne("REST API", "[0]", 1L, TOPICS);
        assertEquals("PARTIAL", described.getStatus());

        SemanticSkillMatch missing = service.matchOne("Docker", "[0]", 1L, TOPICS);
        assertEquals("GAP", missing.getStatus());
        assertEquals(null, missing.getTopic());
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
                null, vectors, null, new SkillAliasService(), null, 0.70, 0.50);
    }
}
