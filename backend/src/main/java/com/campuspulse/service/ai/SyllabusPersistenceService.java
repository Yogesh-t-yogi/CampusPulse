
package com.campuspulse.service.ai;

import com.campuspulse.dto.ai.AiSyllabusResponse;
import com.campuspulse.dto.ai.AiSubject;
import com.campuspulse.dto.ai.AiTopic;
import com.campuspulse.dto.ai.AiUnit;
import com.campuspulse.entity.syllabus.Subject;
import com.campuspulse.entity.syllabus.Syllabus;
import com.campuspulse.entity.syllabus.Topic;
import com.campuspulse.entity.syllabus.Unit;
import com.campuspulse.repository.embedding.TopicEmbeddingJdbcRepository;
import com.campuspulse.repository.syllabus.SyllabusRepository;
import com.campuspulse.service.embedding.EmbeddingService;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClientException;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.List;

@Service
public class SyllabusPersistenceService {

    private final SyllabusRepository syllabusRepository;
    private final EmbeddingService embeddingService;
    private final TopicEmbeddingJdbcRepository topicEmbeddingRepository;

    public SyllabusPersistenceService(
            SyllabusRepository syllabusRepository,
            EmbeddingService embeddingService,
            TopicEmbeddingJdbcRepository topicEmbeddingRepository) {

        this.syllabusRepository = syllabusRepository;
        this.embeddingService = embeddingService;
        this.topicEmbeddingRepository = topicEmbeddingRepository;
    }

    @Transactional
    public Syllabus saveAiStructure(
            Syllabus syllabus,
            AiSyllabusResponse aiResponse) {

        if (aiResponse == null
                || aiResponse.getSubjects() == null
                || aiResponse.getSubjects().isEmpty()) {
            throw new IllegalArgumentException(
                    "AI response contains no subjects.");
        }

        // Remove the previous generated structure (vectors first, then topics).
        if (syllabus.getId() != null) {
            topicEmbeddingRepository.deleteBySyllabusId(syllabus.getId());
        }
        syllabus.getSubjects().clear();

        for (AiSubject aiSubject : aiResponse.getSubjects()) {
            Subject subject = new Subject();
            subject.setName(aiSubject.getName());
            subject.setCode(aiSubject.getCode());
            syllabus.addSubject(subject);

            if (aiSubject.getUnits() == null) {
                continue;
            }

            for (AiUnit aiUnit : aiSubject.getUnits()) {
                Unit unit = new Unit();
                unit.setUnitNumber(aiUnit.getUnitNumber());
                unit.setName(aiUnit.getName());
                subject.addUnit(unit);

                if (aiUnit.getTopics() == null) {
                    continue;
                }

                for (AiTopic aiTopic : aiUnit.getTopics()) {
                    Topic topic = new Topic();
                    topic.setName(aiTopic.getName());
                    topic.setDescription(aiTopic.getDescription());
                    unit.addTopic(topic);
                }
            }
        }

        return syllabusRepository.save(syllabus);
    }

    public EmbeddingGenerationResult generateEmbeddings(
            Syllabus syllabus) {
        List<Topic> topics = syllabus.getSubjects().stream()
                .flatMap(subject -> subject.getUnits().stream())
                .flatMap(unit -> unit.getTopics().stream())
                .toList();

        int generated = 0;
        int failed = 0;
        String firstError = "";
        for (Topic topic : topics) {
            try {
                String text = topic.getName();

                if (topic.getDescription() != null
                        && !topic.getDescription().isBlank()) {
                    text += " " + topic.getDescription();
                }

                var embedding = embeddingService.generateEmbedding(text);

                if (embedding == null || embedding.size() != 384) {
                    throw new IllegalStateException(
                            "Embedding service returned an invalid vector.");
                }

                String vector = embedding.stream()
                        .map(String::valueOf)
                        .collect(java.util.stream.Collectors.joining(
                                ",", "[", "]"));

                topicEmbeddingRepository.saveEmbedding(
                        topic.getId(),
                        vector,
                        "sentence-transformers/all-MiniLM-L6-v2");
                generated++;
            } catch (WebClientException
                     | DataAccessException
                     | IllegalStateException exception) {
                failed++;
                if (firstError.isEmpty()) {
                    String detail = getEmbeddingFailureDetail(exception);
                    if (detail == null || detail.isBlank()) {
                        detail = exception.getClass().getSimpleName();
                    }
                    if (detail.length() > 500) {
                        detail = detail.substring(0, 500);
                    }
                    firstError = detail;
                }
                // Stop early if the service is clearly down (first 3 all failed).
                if (generated == 0 && failed >= 3) {
                    failed = topics.size();
                    break;
                }
            }
        }

        if (failed == 0) {
            return new EmbeddingGenerationResult(generated, 0, "");
        }
        return new EmbeddingGenerationResult(
                generated,
                failed,
                failed + " of " + topics.size()
                        + " topics could not be indexed for matching: "
                        + firstError
                        + " Re-run processing from the Curriculum page once the"
                        + " AI service is running.");
    }

    private String getEmbeddingFailureDetail(RuntimeException exception) {
        if (exception instanceof WebClientResponseException responseException) {
            int status = responseException.getStatusCode().value();
            return "The AI embedding service returned HTTP " + status + ".";
        }
        if (exception instanceof WebClientRequestException) {
            return "The AI embedding service is not reachable. "
                    + "Start it on port 8000 (see README).";
        }
        return exception.getMessage();
    }

    public record EmbeddingGenerationResult(
            int generated,
            int failed,
            String warning) {
    }
}
