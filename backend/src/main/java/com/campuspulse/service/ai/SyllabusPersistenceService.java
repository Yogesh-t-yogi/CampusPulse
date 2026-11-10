
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

    private static final int EMBEDDING_BATCH_SIZE = 64;

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

        // Embed topics in batches (one HTTP call per batch, not per topic).
        for (int start = 0; start < topics.size(); start += EMBEDDING_BATCH_SIZE) {
            List<Topic> batch = topics.subList(
                    start, Math.min(start + EMBEDDING_BATCH_SIZE, topics.size()));
            try {
                List<String> texts = batch.stream()
                        .map(SyllabusPersistenceService::embeddingText)
                        .toList();
                List<List<Double>> vectors = embeddingService.generateEmbeddings(texts);

                for (int i = 0; i < batch.size(); i++) {
                    topicEmbeddingRepository.saveEmbedding(
                            batch.get(i).getId(),
                            EmbeddingService.toVectorLiteral(vectors.get(i)),
                            EmbeddingService.MODEL);
                    generated++;
                }
            } catch (WebClientException
                     | DataAccessException
                     | IllegalStateException exception) {
                failed += batch.size();
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
                // First batch failed completely: service is down, stop here.
                if (generated == 0) {
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

    private static String embeddingText(Topic topic) {
        String text = topic.getName();
        if (topic.getDescription() != null
                && !topic.getDescription().isBlank()) {
            text += " " + topic.getDescription();
        }
        return text;
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
