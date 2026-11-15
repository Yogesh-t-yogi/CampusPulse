package com.campuspulse.controller.student;

import com.campuspulse.entity.user.AppUser;
import com.campuspulse.service.CurrentUserService;
import com.campuspulse.service.student.InsightsService;
import com.campuspulse.service.student.LearningResources;
import com.campuspulse.service.student.StudentService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Student profile, dashboard, learning plan (/api/student) and market insights (/api/insights).
 * Every signed-in user can use them for themselves; the UI shows the student pages to students.
 */
@RestController
public class StudentController {

    private final StudentService studentService;
    private final InsightsService insightsService;
    private final CurrentUserService currentUserService;

    public StudentController(
            StudentService studentService,
            InsightsService insightsService,
            CurrentUserService currentUserService) {
        this.studentService = studentService;
        this.insightsService = insightsService;
        this.currentUserService = currentUserService;
    }

    private AppUser me() {
        return currentUserService.getCurrentUser();
    }

    // ---------- profile ----------

    @GetMapping("/api/student/profile")
    public Map<String, Object> profile() {
        return studentService.profile(me());
    }

    @PutMapping("/api/student/target-roles")
    public Map<String, Object> setTargetRoles(@RequestBody Map<String, List<Long>> body) {
        studentService.setTargetRoles(me(), body.get("jobIds"));
        return studentService.profile(me());
    }

    @PostMapping("/api/student/skills")
    public Map<String, Object> addSkills(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<String> skills = (List<String>) body.getOrDefault("skills", List.of());
        int added = studentService.addSkills(me(), skills, String.valueOf(body.getOrDefault("source", "SELF")));
        return Map.of("added", added);
    }

    @DeleteMapping("/api/student/skills")
    public ResponseEntity<Void> removeSkill(@RequestParam String skill) {
        studentService.removeSkill(me(), skill);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/api/student/course")
    public Map<String, Object> setCourse(@RequestBody Map<String, Long> body) {
        studentService.setCourse(me(), body.get("syllabusId"));
        return Map.of("syllabusId", body.get("syllabusId") == null ? "" : body.get("syllabusId"));
    }

    // ---------- dashboard and learning ----------

    @GetMapping("/api/student/dashboard")
    public Map<String, Object> dashboard() {
        return studentService.dashboard(me());
    }

    @GetMapping("/api/student/learning-plan")
    public Map<String, Object> learningPlan() {
        return studentService.learningPlan(me());
    }

    @PutMapping("/api/student/learning")
    public Map<String, Object> setLearningStatus(@RequestBody Map<String, String> body) {
        studentService.setLearningStatus(me(), body.get("skill"), body.get("status"));
        return Map.of("skill", body.get("skill"), "status", body.get("status"));
    }

    @GetMapping("/api/student/roadmap")
    public JsonNode roadmap(@RequestParam String skill) {
        return studentService.roadmap(skill);
    }

    @GetMapping("/api/student/resources")
    public List<LearningResources.Resource> resources(@RequestParam String skill) {
        return LearningResources.forSkill(skill);
    }

    // ---------- market insights ----------

    @GetMapping("/api/insights")
    public Map<String, Object> market(
            @RequestParam(required = false) String location,
            @RequestParam(required = false) String q) {
        return insightsService.market(me(), location, q);
    }

    @GetMapping("/api/insights/gaps")
    public Map<String, Object> gaps(
            @RequestParam(required = false) Long syllabusId,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) String q) {
        return insightsService.gaps(me(), syllabusId, location, q);
    }
}
