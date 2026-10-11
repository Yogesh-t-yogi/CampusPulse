
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

        // Remove the previous generated structure.
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

        // Save first so the topic records receive database IDs.
        Syllabus savedSyllabus = syllabusRepository.save(syllabus);

        // Generate and store an embedding for every saved topic.
        for (Subject subject : savedSyllabus.getSubjects()) {
            for (Unit unit : subject.getUnits()) {
                for (Topic topic : unit.getTopics()) {

                    String text = topic.getName();

                    if (topic.getDescription() != null
                            && !topic.getDescription().isBlank()) {
                        text += " " + topic.getDescription();
                    }

                    var embedding =
                            embeddingService.generateEmbedding(text);

                    if (embedding == null || embedding.size() != 384) {
                        throw new IllegalStateException(
                                "Invalid embedding for topic ID "
                                        + topic.getId());
                    }

                    String vector = embedding.stream()
                            .map(String::valueOf)
                            .collect(java.util.stream.Collectors.joining(
                                    ",", "[", "]"));

                    topicEmbeddingRepository.saveEmbedding(
                            topic.getId(),
                            vector,
                            "sentence-transformers/all-MiniLM-L6-v2");
                }
            }
        }

        return savedSyllabus;
    }
}
