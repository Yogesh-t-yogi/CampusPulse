package com.campuspulse.service.syllabus;

import com.campuspulse.entity.syllabus.Subject;
import com.campuspulse.entity.syllabus.Topic;
import com.campuspulse.entity.syllabus.Unit;
import com.campuspulse.repository.embedding.TopicEmbeddingJdbcRepository;
import com.campuspulse.repository.syllabus.SubjectRepository;
import com.campuspulse.repository.syllabus.SyllabusRepository;
import com.campuspulse.repository.syllabus.TopicRepository;
import com.campuspulse.repository.syllabus.UnitRepository;
import com.campuspulse.service.analysis.AnalysisResultService;
import com.campuspulse.service.embedding.EmbeddingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Hand edits to an AI-extracted curriculum. Every call checks that the item belongs to a
 * syllabus owned by the user, keeps topic vectors in sync (re-index edited topics, delete
 * vectors of removed ones) and clears saved reports for that syllabus.
 */
@Service
public class CurriculumEditService {

    private static final Logger log = LoggerFactory.getLogger(CurriculumEditService.class);

    private final SyllabusRepository syllabusRepository;
    private final SubjectRepository subjectRepository;
    private final UnitRepository unitRepository;
    private final TopicRepository topicRepository;
    private final TopicEmbeddingJdbcRepository topicEmbeddingRepository;
    private final EmbeddingService embeddingService;
    private final AnalysisResultService analysisResultService;
    private final SyllabusService syllabusService;

    public CurriculumEditService(
            SyllabusRepository syllabusRepository,
            SubjectRepository subjectRepository,
            UnitRepository unitRepository,
            TopicRepository topicRepository,
            TopicEmbeddingJdbcRepository topicEmbeddingRepository,
            EmbeddingService embeddingService,
            AnalysisResultService analysisResultService,
            SyllabusService syllabusService) {
        this.syllabusRepository = syllabusRepository;
        this.subjectRepository = subjectRepository;
        this.unitRepository = unitRepository;
        this.topicRepository = topicRepository;
        this.topicEmbeddingRepository = topicEmbeddingRepository;
        this.embeddingService = embeddingService;
        this.analysisResultService = analysisResultService;
        this.syllabusService = syllabusService;
    }

    // ---------- subjects ----------

    @Transactional
    public Subject updateSubject(Long syllabusId, Long userId, Long subjectId, String name, String code) {
        Subject subject = requireSubject(syllabusId, userId, subjectId);
        subject.setName(requireText(name, "Subject name"));
        subject.setCode(blankToNull(code));
        return subjectRepository.save(subject);
    }

    // ---------- units ----------

    @Transactional
    public Unit addUnit(Long syllabusId, Long userId, Long subjectId, String name, Integer unitNumber) {
        Subject subject = requireSubject(syllabusId, userId, subjectId);
        Unit unit = new Unit();
        unit.setName(requireText(name, "Unit name"));
        unit.setUnitNumber(unitNumber != null ? unitNumber : subject.getUnits().size() + 1);
        subject.addUnit(unit);
        Unit saved = unitRepository.save(unit);
        analysisResultService.clearForSyllabus(syllabusId);
        return saved;
    }

    @Transactional
    public Unit updateUnit(Long syllabusId, Long userId, Long unitId, String name, Integer unitNumber) {
        Unit unit = requireUnit(syllabusId, userId, unitId);
        unit.setName(requireText(name, "Unit name"));
        if (unitNumber != null) {
            unit.setUnitNumber(unitNumber);
        }
        return unitRepository.save(unit);
    }

    @Transactional
    public void deleteUnit(Long syllabusId, Long userId, Long unitId) {
        Unit unit = requireUnit(syllabusId, userId, unitId);
        topicEmbeddingRepository.deleteByUnitId(unitId);
        unit.getSubject().getUnits().remove(unit);
        unitRepository.delete(unit);
        analysisResultService.clearForSyllabus(syllabusId);
    }

    // ---------- topics ----------

    @Transactional
    public Topic addTopic(Long syllabusId, Long userId, Long unitId, String name, String description) {
        Unit unit = requireUnit(syllabusId, userId, unitId);
        Topic topic = new Topic();
        topic.setName(requireText(name, "Topic name"));
        topic.setDescription(blankToNull(description));
        unit.addTopic(topic);
        Topic saved = topicRepository.save(topic);
        reindex(saved);
        analysisResultService.clearForSyllabus(syllabusId);
        return saved;
    }

    /** Rename / re-describe a topic, and optionally move it to another unit of the same syllabus. */
    @Transactional
    public Topic updateTopic(Long syllabusId, Long userId, Long topicId,
                             String name, String description, Long moveToUnitId) {
        Topic topic = requireTopic(syllabusId, userId, topicId);
        boolean textChanged = !java.util.Objects.equals(topic.getName(), name)
                || !java.util.Objects.equals(topic.getDescription(), blankToNull(description));
        topic.setName(requireText(name, "Topic name"));
        topic.setDescription(blankToNull(description));
        if (moveToUnitId != null && !moveToUnitId.equals(topic.getUnit().getId())) {
            // Only change the parent link; touching the old unit's list would delete the topic.
            topic.setUnit(requireUnit(syllabusId, userId, moveToUnitId));
        }
        Topic saved = topicRepository.save(topic);
        if (textChanged) {
            reindex(saved);
        }
        analysisResultService.clearForSyllabus(syllabusId);
        return saved;
    }

    @Transactional
    public void deleteTopic(Long syllabusId, Long userId, Long topicId) {
        Topic topic = requireTopic(syllabusId, userId, topicId);
        topicEmbeddingRepository.deleteByTopicId(topicId);
        topic.getUnit().getTopics().remove(topic);
        topicRepository.delete(topic);
        analysisResultService.clearForSyllabus(syllabusId);
    }

    // ---------- helpers ----------

    // Topic vector = "name description", same as full processing.
    private void reindex(Topic topic) {
        String text = topic.getName()
                + (topic.getDescription() == null ? "" : " " + topic.getDescription());
        try {
            topicEmbeddingRepository.saveEmbedding(
                    topic.getId(),
                    EmbeddingService.toVectorLiteral(embeddingService.generateEmbedding(text)),
                    EmbeddingService.MODEL);
        } catch (RuntimeException e) {
            // Edit is kept; the topic simply isn't matched until the next re-process.
            log.warn("Could not index edited topic {}: {}", topic.getId(), e.getMessage());
            topicEmbeddingRepository.deleteByTopicId(topic.getId());
        }
    }

    private void requireOwnSyllabus(Long syllabusId, Long userId) {
        // Owner, HOD of its department or college admin (SyllabusAccess.canManage).
        syllabusService.requireManageable(syllabusId);
    }

    private Subject requireSubject(Long syllabusId, Long userId, Long subjectId) {
        requireOwnSyllabus(syllabusId, userId);
        Subject subject = subjectRepository.findById(subjectId).orElseThrow(() -> notFound("Subject"));
        if (!subject.getSyllabus().getId().equals(syllabusId)) {
            throw notFound("Subject");
        }
        return subject;
    }

    private Unit requireUnit(Long syllabusId, Long userId, Long unitId) {
        requireOwnSyllabus(syllabusId, userId);
        Unit unit = unitRepository.findById(unitId).orElseThrow(() -> notFound("Unit"));
        if (!unit.getSubject().getSyllabus().getId().equals(syllabusId)) {
            throw notFound("Unit");
        }
        return unit;
    }

    private Topic requireTopic(Long syllabusId, Long userId, Long topicId) {
        requireOwnSyllabus(syllabusId, userId);
        Topic topic = topicRepository.findById(topicId).orElseThrow(() -> notFound("Topic"));
        if (!topic.getUnit().getSubject().getSyllabus().getId().equals(syllabusId)) {
            throw notFound("Topic");
        }
        return topic;
    }

    private static ResponseStatusException notFound(String what) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, what + " not found");
    }

    private static String requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " can't be empty.");
        }
        String trimmed = value.trim();
        if (trimmed.length() > 255) {
            throw new IllegalArgumentException(label + " is too long (max 255 characters).");
        }
        return trimmed;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
