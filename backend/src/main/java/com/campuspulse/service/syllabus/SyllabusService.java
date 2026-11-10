
package com.campuspulse.service.syllabus;

import com.campuspulse.dto.syllabus.FullSyllabusResponse;
import com.campuspulse.entity.syllabus.Syllabus;
import com.campuspulse.entity.syllabus.Subject;
import com.campuspulse.entity.syllabus.Unit;
import com.campuspulse.entity.syllabus.Topic;
import com.campuspulse.repository.syllabus.SyllabusRepository;
import com.campuspulse.repository.embedding.TopicEmbeddingJdbcRepository;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class SyllabusService {

    private final SyllabusRepository syllabusRepository;
    private final JdbcTemplate jdbcTemplate;
    private final TopicEmbeddingJdbcRepository topicEmbeddingRepository;

    public SyllabusService(
            SyllabusRepository syllabusRepository,
            JdbcTemplate jdbcTemplate,
            TopicEmbeddingJdbcRepository topicEmbeddingRepository) {
        this.syllabusRepository = syllabusRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.topicEmbeddingRepository = topicEmbeddingRepository;
    }

    /** Light list for tables and pickers (no extracted text, no tree). */
    public List<Map<String, Object>> listSummaries(Long userId) {
        return jdbcTemplate.queryForList("""
                SELECT s.id, s.name, s.file_name AS "fileName", s.created_at AS "createdAt",
                       s.processing_status AS "processingStatus",
                       s.processing_message AS "processingMessage",
                       (SELECT count(*) FROM subject sb WHERE sb.syllabus_id = s.id) AS "subjectCount",
                       (SELECT count(*) FROM topic t
                          JOIN unit u ON u.id = t.unit_id
                          JOIN subject sb ON sb.id = u.subject_id
                         WHERE sb.syllabus_id = s.id) AS "topicCount"
                FROM syllabus s
                WHERE s.user_id = ?
                ORDER BY s.created_at DESC
                """, userId);
    }

    @Transactional
    public Syllabus rename(Long id, Long userId, String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Name can't be empty.");
        }
        Syllabus syllabus = syllabusRepository.findByIdAndOwner_Id(id, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Syllabus not found"));
        syllabus.setName(name.trim().length() > 255 ? name.trim().substring(0, 255) : name.trim());
        return syllabusRepository.save(syllabus);
    }

    /** Deletes the syllabus with its curriculum, vectors and saved reports. */
    @Transactional
    public void delete(Long id, Long userId) {
        Syllabus syllabus = syllabusRepository.findByIdAndOwner_Id(id, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Syllabus not found"));
        topicEmbeddingRepository.deleteBySyllabusId(id); // vectors reference topics
        syllabusRepository.delete(syllabus);             // subjects/units/topics cascade; analysis_result cascades in DB
    }

    // Create syllabus
    public Syllabus createSyllabus(Syllabus syllabus) {
        return syllabusRepository.save(syllabus);
    }

    // Get syllabi belonging to a particular user
    public List<Syllabus> getAllSyllabi(Long userId) {
        return syllabusRepository.findAllByOwner_Id(userId);
    }

    // Get a syllabus only if it belongs to the user
    public Optional<Syllabus> getSyllabusById(Long id, Long userId) {
        return syllabusRepository.findByIdAndOwner_Id(id, userId);
    }

    // Get the complete syllabus hierarchy for its owner
    @Transactional(readOnly = true)
    public Optional<FullSyllabusResponse> getFullSyllabusById(
            Long id, Long userId) {

        return syllabusRepository.findByIdAndOwner_Id(id, userId)
                .map(syllabus -> {

                    FullSyllabusResponse response =
                            new FullSyllabusResponse();

                    response.setId(syllabus.getId());
                    response.setName(syllabus.getName());
                    response.setFileName(syllabus.getFileName());
                    response.setCreatedAt(syllabus.getCreatedAt());

                    for (Subject subject : syllabus.getSubjects()) {

                        FullSyllabusResponse.SubjectResponse
                                subjectResponse =
                                new FullSyllabusResponse.SubjectResponse();

                        subjectResponse.setId(subject.getId());
                        subjectResponse.setName(subject.getName());
                        subjectResponse.setCode(subject.getCode());

                        for (Unit unit : subject.getUnits()) {

                            FullSyllabusResponse.UnitResponse
                                    unitResponse =
                                    new FullSyllabusResponse.UnitResponse();

                            unitResponse.setId(unit.getId());
                            unitResponse.setUnitNumber(
                                    unit.getUnitNumber()
                            );
                            unitResponse.setName(unit.getName());

                            for (Topic topic : unit.getTopics()) {

                                FullSyllabusResponse.TopicResponse
                                        topicResponse =
                                        new FullSyllabusResponse.TopicResponse();

                                topicResponse.setId(topic.getId());
                                topicResponse.setName(topic.getName());
                                topicResponse.setDescription(
                                        topic.getDescription()
                                );

                                unitResponse.getTopics()
                                        .add(topicResponse);
                            }

                            subjectResponse.getUnits()
                                    .add(unitResponse);
                        }

                        response.getSubjects()
                                .add(subjectResponse);
                    }

                    return response;
                });
    }
}
