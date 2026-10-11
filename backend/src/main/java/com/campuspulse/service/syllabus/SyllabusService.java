
package com.campuspulse.service.syllabus;

import com.campuspulse.dto.syllabus.FullSyllabusResponse;
import com.campuspulse.entity.syllabus.Syllabus;
import com.campuspulse.entity.syllabus.Subject;
import com.campuspulse.entity.syllabus.Unit;
import com.campuspulse.entity.syllabus.Topic;
import com.campuspulse.repository.syllabus.SyllabusRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class SyllabusService {

    private final SyllabusRepository syllabusRepository;

    public SyllabusService(SyllabusRepository syllabusRepository) {
        this.syllabusRepository = syllabusRepository;
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
