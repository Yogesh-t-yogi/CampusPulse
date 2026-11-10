package com.campuspulse.service.syllabus;

import com.campuspulse.entity.syllabus.Subject;
import com.campuspulse.entity.syllabus.Syllabus;
import com.campuspulse.entity.syllabus.Topic;
import com.campuspulse.entity.syllabus.Unit;
import com.campuspulse.repository.embedding.TopicEmbeddingJdbcRepository;
import com.campuspulse.repository.syllabus.SubjectRepository;
import com.campuspulse.repository.syllabus.SyllabusRepository;
import com.campuspulse.repository.syllabus.TopicRepository;
import com.campuspulse.repository.syllabus.UnitRepository;
import com.campuspulse.service.analysis.AnalysisResultService;
import com.campuspulse.service.embedding.EmbeddingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CurriculumEditServiceTest {

    private final SyllabusRepository syllabi = mock(SyllabusRepository.class);
    private final SubjectRepository subjects = mock(SubjectRepository.class);
    private final UnitRepository units = mock(UnitRepository.class);
    private final TopicRepository topics = mock(TopicRepository.class);
    private final TopicEmbeddingJdbcRepository vectors = mock(TopicEmbeddingJdbcRepository.class);
    private final EmbeddingService embeddings = mock(EmbeddingService.class);
    private final AnalysisResultService results = mock(AnalysisResultService.class);

    private final CurriculumEditService service = new CurriculumEditService(
            syllabi, subjects, units, topics, vectors, embeddings, results);

    private Unit unitA;
    private Unit unitB;
    private Topic topic;

    @BeforeEach
    void setUp() {
        Syllabus syllabus = new Syllabus();
        ReflectionTestUtils.setField(syllabus, "id", 1L);
        Subject subject = new Subject();
        subject.setId(10L);
        subject.setSyllabus(syllabus);
        unitA = unit(20L, subject);
        unitB = unit(21L, subject);
        topic = new Topic();
        ReflectionTestUtils.setField(topic, "id", 30L);
        topic.setName("Loops");
        topic.setDescription("for and while");
        unitA.addTopic(topic);

        when(syllabi.findByIdAndOwner_Id(1L, 5L)).thenReturn(Optional.of(syllabus));
        when(syllabi.findByIdAndOwner_Id(eq(1L), eq(6L))).thenReturn(Optional.empty());
        when(topics.findById(30L)).thenReturn(Optional.of(topic));
        when(units.findById(21L)).thenReturn(Optional.of(unitB));
        when(topics.save(any(Topic.class))).thenAnswer(call -> call.getArgument(0));
        when(embeddings.generateEmbedding(anyString())).thenReturn(Collections.nCopies(384, 0.1));
    }

    private static Unit unit(long id, Subject subject) {
        Unit unit = new Unit();
        ReflectionTestUtils.setField(unit, "id", id);
        unit.setSubject(subject);
        unit.setTopics(new ArrayList<>());
        return unit;
    }

    @Test
    void otherUsersCannotEdit() {
        assertThrows(ResponseStatusException.class,
                () -> service.deleteTopic(1L, 6L, 30L));
        verify(vectors, never()).deleteByTopicId(anyLong());
    }

    @Test
    void renamingReindexesAndClearsReports() {
        service.updateTopic(1L, 5L, 30L, "Loops in Java", "for, while, do-while", null);
        assertEquals("Loops in Java", topic.getName());
        verify(vectors).saveEmbedding(eq(30L), anyString(), eq(EmbeddingService.MODEL));
        verify(results).clearForSyllabus(1L);
    }

    @Test
    void movingKeepsTopicAndDoesNotReindex() {
        service.updateTopic(1L, 5L, 30L, "Loops", "for and while", 21L);
        assertEquals(unitB, topic.getUnit());
        verify(topics, never()).delete(any());
        verify(vectors, never()).saveEmbedding(anyLong(), anyString(), anyString());
    }

    @Test
    void deletingRemovesVectorFirst() {
        service.deleteTopic(1L, 5L, 30L);
        verify(vectors).deleteByTopicId(30L);
        verify(topics).delete(topic);
        assertFalse(unitA.getTopics().contains(topic));
        verify(results).clearForSyllabus(1L);
    }

    @Test
    void blankNameIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> service.updateTopic(1L, 5L, 30L, "  ", null, null));
    }
}
