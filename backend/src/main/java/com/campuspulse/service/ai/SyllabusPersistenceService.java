package com.campuspulse.service.ai;

import com.campuspulse.dto.ai.AiSyllabusResponse;
import com.campuspulse.dto.ai.AiSubject;
import com.campuspulse.dto.ai.AiTopic;
import com.campuspulse.dto.ai.AiUnit;
import com.campuspulse.entity.syllabus.Subject;
import com.campuspulse.entity.syllabus.Syllabus;
import com.campuspulse.entity.syllabus.Topic;
import com.campuspulse.entity.syllabus.Unit;
import com.campuspulse.repository.syllabus.SyllabusRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SyllabusPersistenceService {

    private final SyllabusRepository syllabusRepository;

    public SyllabusPersistenceService(
            SyllabusRepository syllabusRepository) {

        this.syllabusRepository = syllabusRepository;
    }

    @Transactional
    public Syllabus saveAiStructure(
            Syllabus syllabus,
            AiSyllabusResponse aiResponse) {

        if (aiResponse == null ||
                aiResponse.getSubjects() == null ||
                aiResponse.getSubjects().isEmpty()) {

            throw new IllegalArgumentException(
                    "AI response contains no subjects."
            );
        }

        // Remove previously generated subjects if they exist.
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
                    topic.setDescription(
                            aiTopic.getDescription()
                    );

                    unit.addTopic(topic);
                }
            }
        }

        return syllabusRepository.save(syllabus);
    }
}