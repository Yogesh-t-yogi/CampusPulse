package com.campuspulse.controller.syllabus;

import com.campuspulse.entity.syllabus.Subject;
import com.campuspulse.entity.syllabus.Topic;
import com.campuspulse.entity.syllabus.Unit;
import com.campuspulse.service.CurrentUserService;
import com.campuspulse.service.syllabus.CurriculumEditService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/** Hand edits to a syllabus' curriculum (owner only). Bodies are small JSON objects. */
@RestController
@RequestMapping("/api/syllabus/{syllabusId}")
public class CurriculumEditController {

    private final CurriculumEditService editService;
    private final CurrentUserService currentUserService;

    public CurriculumEditController(
            CurriculumEditService editService,
            CurrentUserService currentUserService) {
        this.editService = editService;
        this.currentUserService = currentUserService;
    }

    @PutMapping("/subjects/{subjectId}")
    public Map<String, Object> updateSubject(
            @PathVariable Long syllabusId,
            @PathVariable Long subjectId,
            @RequestBody Map<String, Object> body) {
        Subject subject = editService.updateSubject(
                syllabusId, userId(), subjectId, text(body, "name"), text(body, "code"));
        return view("id", subject.getId(), "name", subject.getName(), "code", subject.getCode());
    }

    @PostMapping("/subjects/{subjectId}/units")
    public Map<String, Object> addUnit(
            @PathVariable Long syllabusId,
            @PathVariable Long subjectId,
            @RequestBody Map<String, Object> body) {
        return unitView(editService.addUnit(
                syllabusId, userId(), subjectId, text(body, "name"), number(body, "unitNumber")));
    }

    @PutMapping("/units/{unitId}")
    public Map<String, Object> updateUnit(
            @PathVariable Long syllabusId,
            @PathVariable Long unitId,
            @RequestBody Map<String, Object> body) {
        return unitView(editService.updateUnit(
                syllabusId, userId(), unitId, text(body, "name"), number(body, "unitNumber")));
    }

    @DeleteMapping("/units/{unitId}")
    public ResponseEntity<Void> deleteUnit(@PathVariable Long syllabusId, @PathVariable Long unitId) {
        editService.deleteUnit(syllabusId, userId(), unitId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/units/{unitId}/topics")
    public Map<String, Object> addTopic(
            @PathVariable Long syllabusId,
            @PathVariable Long unitId,
            @RequestBody Map<String, Object> body) {
        return topicView(editService.addTopic(
                syllabusId, userId(), unitId, text(body, "name"), text(body, "description")));
    }

    @PutMapping("/topics/{topicId}")
    public Map<String, Object> updateTopic(
            @PathVariable Long syllabusId,
            @PathVariable Long topicId,
            @RequestBody Map<String, Object> body) {
        Integer moveTo = number(body, "unitId");
        return topicView(editService.updateTopic(
                syllabusId, userId(), topicId, text(body, "name"), text(body, "description"),
                moveTo == null ? null : moveTo.longValue()));
    }

    @DeleteMapping("/topics/{topicId}")
    public ResponseEntity<Void> deleteTopic(@PathVariable Long syllabusId, @PathVariable Long topicId) {
        editService.deleteTopic(syllabusId, userId(), topicId);
        return ResponseEntity.noContent().build();
    }

    private Long userId() {
        return currentUserService.getCurrentUser().getId();
    }

    private static String text(Map<String, Object> body, String key) {
        Object value = body.get(key);
        return value == null ? null : value.toString();
    }

    private static Integer number(Map<String, Object> body, String key) {
        Object value = body.get(key);
        if (value == null || value.toString().isBlank()) {
            return null;
        }
        try {
            return value instanceof Number n ? n.intValue() : Integer.valueOf(value.toString().trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(key + " must be a number.");
        }
    }

    private static Map<String, Object> unitView(Unit unit) {
        return view("id", unit.getId(), "name", unit.getName(), "unitNumber", unit.getUnitNumber());
    }

    private static Map<String, Object> topicView(Topic topic) {
        return view("id", topic.getId(), "name", topic.getName(),
                "description", topic.getDescription(), "unitId", topic.getUnit().getId());
    }

    // Map.of() rejects nulls (e.g. empty code); HashMap allows them.
    private static Map<String, Object> view(Object... pairs) {
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((String) pairs[i], pairs[i + 1]);
        }
        return map;
    }
}
