package com.campuspulse.controller.outcome;

import com.campuspulse.entity.org.Department;
import com.campuspulse.entity.org.Program;
import com.campuspulse.entity.syllabus.Syllabus;
import com.campuspulse.entity.user.AppUser;
import com.campuspulse.repository.org.DepartmentRepository;
import com.campuspulse.repository.org.ProgramRepository;
import com.campuspulse.service.CurrentUserService;
import com.campuspulse.service.org.TenantService;
import com.campuspulse.service.outcome.OutcomeService;
import com.campuspulse.service.syllabus.SyllabusService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * NBA outcomes. POs: read by anyone in the college, changed by the college admin or the HOD
 * of the program's department. COs and CO–PO mapping: read with the syllabus, changed by whoever
 * may change the syllabus (SyllabusAccess).
 */
@RestController
@RequestMapping("/api/outcomes")
public class OutcomeController {

    private final OutcomeService outcomeService;
    private final SyllabusService syllabusService;
    private final ProgramRepository programRepository;
    private final DepartmentRepository departmentRepository;
    private final CurrentUserService currentUserService;
    private final TenantService tenantService;

    public OutcomeController(
            OutcomeService outcomeService,
            SyllabusService syllabusService,
            ProgramRepository programRepository,
            DepartmentRepository departmentRepository,
            CurrentUserService currentUserService,
            TenantService tenantService) {
        this.outcomeService = outcomeService;
        this.syllabusService = syllabusService;
        this.programRepository = programRepository;
        this.departmentRepository = departmentRepository;
        this.currentUserService = currentUserService;
        this.tenantService = tenantService;
    }

    // ---------------- program outcomes ----------------

    @GetMapping("/programs/{programId}")
    public List<Map<String, Object>> programOutcomes(@PathVariable Long programId) {
        readableProgram(programId);
        return outcomeService.listProgramOutcomes(programId);
    }

    @PostMapping("/programs/{programId}/nba")
    public Map<String, Object> addNba(@PathVariable Long programId) {
        manageableProgram(programId);
        return Map.of("added", outcomeService.addNbaProgramOutcomes(programId));
    }

    @PostMapping("/programs/{programId}")
    public Map<String, Object> createProgramOutcome(@PathVariable Long programId, @RequestBody Map<String, String> body) {
        manageableProgram(programId);
        return unique(() -> outcomeService.saveProgramOutcome(programId, null, body));
    }

    @PutMapping("/programs/{programId}/{id}")
    public Map<String, Object> updateProgramOutcome(
            @PathVariable Long programId, @PathVariable Long id, @RequestBody Map<String, String> body) {
        manageableProgram(programId);
        return unique(() -> outcomeService.saveProgramOutcome(programId, id, body));
    }

    @DeleteMapping("/programs/{programId}/{id}")
    public ResponseEntity<Void> deleteProgramOutcome(@PathVariable Long programId, @PathVariable Long id) {
        manageableProgram(programId);
        outcomeService.deleteProgramOutcome(programId, id);
        return ResponseEntity.noContent().build();
    }

    // ---------------- course outcomes ----------------

    @GetMapping("/syllabus/{syllabusId}")
    public Map<String, Object> syllabusOutcomes(@PathVariable Long syllabusId) {
        return outcomeService.syllabusOutcomes(readableSyllabus(syllabusId));
    }

    /** AI: read COs (and suggested CO–PO levels) from the syllabus text. */
    @PostMapping("/syllabus/{syllabusId}/extract")
    public Map<String, Object> extract(
            @PathVariable Long syllabusId,
            @RequestParam(defaultValue = "false") boolean replace) {
        Syllabus syllabus = syllabusService.requireManageable(syllabusId);
        return outcomeService.extractCourseOutcomes(syllabus, replace);
    }

    @PostMapping("/subjects/{subjectId}")
    public Map<String, Object> createCourseOutcome(@PathVariable Long subjectId, @RequestBody Map<String, String> body) {
        Long syllabusId = outcomeService.syllabusOfSubject(subjectId);
        if (syllabusId == null) throw notFound("Subject");
        syllabusService.requireManageable(syllabusId);
        return Map.of("id", unique(() -> outcomeService.saveCourseOutcome(subjectId, null, body)));
    }

    @PutMapping("/cos/{id}")
    public Map<String, Object> updateCourseOutcome(@PathVariable Long id, @RequestBody Map<String, String> body) {
        manageableCourseOutcome(id);
        return Map.of("id", unique(() -> outcomeService.saveCourseOutcome(null, id, body)));
    }

    @DeleteMapping("/cos/{id}")
    public ResponseEntity<Void> deleteCourseOutcome(@PathVariable Long id) {
        manageableCourseOutcome(id);
        outcomeService.deleteCourseOutcome(id);
        return ResponseEntity.noContent().build();
    }

    /** Body: {"<programOutcomeId>": 0..3, …}; 0 removes the link. */
    @PutMapping("/cos/{id}/mapping")
    public Map<String, Object> setMapping(@PathVariable Long id, @RequestBody Map<String, Object> levels) {
        Syllabus syllabus = manageableCourseOutcome(id);
        if (syllabus.getProgramId() == null) {
            throw new IllegalArgumentException("Set the syllabus' program first (Syllabi → Edit details).");
        }
        outcomeService.setMapping(id, levels, syllabus.getProgramId());
        return Map.of("id", id);
    }

    // ---------------- report ----------------

    @GetMapping("/report")
    public Map<String, Object> report(@RequestParam Long syllabusId, @RequestParam Long jobId) {
        Syllabus syllabus = readableSyllabus(syllabusId);
        return outcomeService.outcomesReport(syllabus, jobId);
    }

    // ---------------- helpers ----------------

    private Syllabus readableSyllabus(Long syllabusId) {
        return syllabusService.getSyllabusById(syllabusId, null).orElseThrow(() -> notFound("Syllabus"));
    }

    private Syllabus manageableCourseOutcome(Long courseOutcomeId) {
        Long syllabusId = outcomeService.syllabusOfCourseOutcome(courseOutcomeId);
        if (syllabusId == null) throw notFound("Course outcome");
        return syllabusService.requireManageable(syllabusId);
    }

    private Department readableProgram(Long programId) {
        AppUser user = currentUserService.getCurrentUser();
        Program program = programRepository.findById(programId).orElseThrow(() -> notFound("Program"));
        Department department = departmentRepository.findById(program.getDepartmentId()).orElseThrow();
        if (!tenantService.isSuperAdmin(user)
                && !Objects.equals(department.getInstitutionId(), user.getInstitutionId())) {
            throw notFound("Program");
        }
        return department;
    }

    private void manageableProgram(Long programId) {
        Department department = readableProgram(programId);
        tenantService.requireDepartmentManager(currentUserService.getCurrentUser(), department);
    }

    private static <T> T unique(java.util.function.Supplier<T> action) {
        try {
            return action.get();
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "That code is already used.");
        }
    }

    private static ResponseStatusException notFound(String what) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, what + " not found");
    }
}
