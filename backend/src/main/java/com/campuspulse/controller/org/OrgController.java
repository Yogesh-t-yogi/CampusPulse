package com.campuspulse.controller.org;

import com.campuspulse.entity.org.Department;
import com.campuspulse.entity.org.Institution;
import com.campuspulse.entity.org.Program;
import com.campuspulse.entity.user.AppUser;
import com.campuspulse.repository.org.DepartmentRepository;
import com.campuspulse.repository.org.InstitutionRepository;
import com.campuspulse.repository.org.ProgramRepository;
import com.campuspulse.service.CurrentUserService;
import com.campuspulse.service.org.TenantService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Colleges, departments and programs.
 * Reads: anyone in the college (super admin: any college).
 * Writes: colleges → super admin; departments → college admin; programs → college admin or the HOD.
 */
@RestController
@RequestMapping("/api/org")
public class OrgController {

    private final InstitutionRepository institutions;
    private final DepartmentRepository departments;
    private final ProgramRepository programs;
    private final CurrentUserService currentUserService;
    private final TenantService tenantService;
    private final JdbcTemplate jdbcTemplate;

    public OrgController(
            InstitutionRepository institutions,
            DepartmentRepository departments,
            ProgramRepository programs,
            CurrentUserService currentUserService,
            TenantService tenantService,
            JdbcTemplate jdbcTemplate) {
        this.institutions = institutions;
        this.departments = departments;
        this.programs = programs;
        this.currentUserService = currentUserService;
        this.tenantService = tenantService;
        this.jdbcTemplate = jdbcTemplate;
    }

    // ---------------- colleges ----------------

    @GetMapping("/institutions")
    public List<Map<String, Object>> listInstitutions() {
        AppUser user = me();
        List<Institution> list = tenantService.isSuperAdmin(user)
                ? institutions.findAllByOrderByNameAsc()
                : institutions.findById(nonNull(user.getInstitutionId())).stream().toList();
        return list.stream().map(this::institutionView).toList();
    }

    @PostMapping("/institutions")
    public Map<String, Object> createInstitution(@RequestBody Map<String, String> body) {
        requireSuperAdmin();
        Institution institution = new Institution();
        applyInstitution(institution, body);
        return institutionView(saveUnique(() -> institutions.save(institution), "college code"));
    }

    @PutMapping("/institutions/{id}")
    public Map<String, Object> updateInstitution(@PathVariable Long id, @RequestBody Map<String, String> body) {
        requireSuperAdmin();
        Institution institution = institutions.findById(id).orElseThrow(() -> notFound("College"));
        applyInstitution(institution, body);
        return institutionView(saveUnique(() -> institutions.save(institution), "college code"));
    }

    @DeleteMapping("/institutions/{id}")
    public ResponseEntity<Void> deleteInstitution(@PathVariable Long id) {
        requireSuperAdmin();
        Institution institution = institutions.findById(id).orElseThrow(() -> notFound("College"));
        if (count("SELECT count(*) FROM app_user WHERE institution_id = ?", id) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This college still has users. Move or delete them first.");
        }
        institutions.delete(institution);
        return ResponseEntity.noContent().build();
    }

    // ---------------- departments ----------------

    @GetMapping("/departments")
    public List<Map<String, Object>> listDepartments(@RequestParam(required = false) Long institutionId) {
        Long college = readableCollege(institutionId);
        return departments.findAllByInstitutionIdOrderByNameAsc(college).stream()
                .map(this::departmentView).toList();
    }

    @PostMapping("/departments")
    public Map<String, Object> createDepartment(@RequestBody Map<String, String> body) {
        AppUser user = me();
        Long college = body.get("institutionId") != null && tenantService.isSuperAdmin(user)
                ? Long.valueOf(body.get("institutionId"))
                : tenantService.actingInstitutionId(user);
        tenantService.requireCollegeAdmin(user, college);
        Department department = new Department();
        department.setInstitutionId(college);
        applyDepartment(department, body);
        return departmentView(saveUnique(() -> departments.save(department), "department code"));
    }

    @PutMapping("/departments/{id}")
    public Map<String, Object> updateDepartment(@PathVariable Long id, @RequestBody Map<String, String> body) {
        Department department = departments.findById(id).orElseThrow(() -> notFound("Department"));
        tenantService.requireCollegeAdmin(me(), department.getInstitutionId());
        applyDepartment(department, body);
        return departmentView(saveUnique(() -> departments.save(department), "department code"));
    }

    @DeleteMapping("/departments/{id}")
    public ResponseEntity<Void> deleteDepartment(@PathVariable Long id) {
        Department department = departments.findById(id).orElseThrow(() -> notFound("Department"));
        tenantService.requireCollegeAdmin(me(), department.getInstitutionId());
        departments.delete(department); // programs cascade; users/syllabi keep the college, lose the department
        return ResponseEntity.noContent().build();
    }

    // ---------------- programs ----------------

    @GetMapping("/programs")
    public List<Map<String, Object>> listPrograms(
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) Long institutionId) {
        if (departmentId != null) {
            Department department = departments.findById(departmentId).orElseThrow(() -> notFound("Department"));
            readableCollege(department.getInstitutionId());
            return programs.findAllByDepartmentIdOrderByNameAsc(departmentId).stream()
                    .map(this::programView).toList();
        }
        Long college = readableCollege(institutionId);
        List<Long> departmentIds = departments.findAllByInstitutionIdOrderByNameAsc(college).stream()
                .map(Department::getId).toList();
        if (departmentIds.isEmpty()) {
            return List.of();
        }
        return programs.findAllByDepartmentIdInOrderByNameAsc(departmentIds).stream()
                .map(this::programView).toList();
    }

    @PostMapping("/programs")
    public Map<String, Object> createProgram(@RequestBody Map<String, String> body) {
        Department department = departments.findById(requiredLong(body, "departmentId"))
                .orElseThrow(() -> notFound("Department"));
        tenantService.requireDepartmentManager(me(), department);
        Program program = new Program();
        program.setDepartmentId(department.getId());
        applyProgram(program, body);
        return programView(saveUnique(() -> programs.save(program), "program code and regulation"));
    }

    @PutMapping("/programs/{id}")
    public Map<String, Object> updateProgram(@PathVariable Long id, @RequestBody Map<String, String> body) {
        Program program = programs.findById(id).orElseThrow(() -> notFound("Program"));
        tenantService.requireDepartmentManager(me(), departments.findById(program.getDepartmentId()).orElseThrow());
        applyProgram(program, body);
        return programView(saveUnique(() -> programs.save(program), "program code and regulation"));
    }

    @DeleteMapping("/programs/{id}")
    public ResponseEntity<Void> deleteProgram(@PathVariable Long id) {
        Program program = programs.findById(id).orElseThrow(() -> notFound("Program"));
        tenantService.requireDepartmentManager(me(), departments.findById(program.getDepartmentId()).orElseThrow());
        programs.delete(program);
        return ResponseEntity.noContent().build();
    }

    // ---------------- helpers ----------------

    private AppUser me() {
        return currentUserService.getCurrentUser();
    }

    private void requireSuperAdmin() {
        if (!tenantService.isSuperAdmin(me())) {
            throw TenantService.forbidden("Only the platform admin can manage colleges.");
        }
    }

    /** College whose data may be read: own college, or any for a super admin. */
    private Long readableCollege(Long requested) {
        AppUser user = me();
        Long acting = tenantService.actingInstitutionId(user);
        if (requested == null || requested.equals(acting)) {
            return nonNull(acting);
        }
        if (tenantService.isSuperAdmin(user)) {
            return requested;
        }
        throw notFound("College");
    }

    private static Long nonNull(Long id) {
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a college first.");
        }
        return id;
    }

    private void applyInstitution(Institution institution, Map<String, String> body) {
        institution.setName(required(body, "name"));
        institution.setCode(code(required(body, "code")));
        institution.setCity(blankToNull(body.get("city")));
    }

    private void applyDepartment(Department department, Map<String, String> body) {
        department.setName(required(body, "name"));
        department.setCode(code(required(body, "code")));
    }

    private void applyProgram(Program program, Map<String, String> body) {
        program.setName(required(body, "name"));
        program.setDegree(code(required(body, "degree")));
        program.setCode(code(required(body, "code")));
        program.setRegulation(blankToNull(body.get("regulation")));
        String semesters = body.get("durationSemesters");
        if (semesters != null && !semesters.isBlank()) {
            int value = Integer.parseInt(semesters.trim());
            if (value < 1 || value > 12) {
                throw new IllegalArgumentException("Semesters must be between 1 and 12.");
            }
            program.setDurationSemesters(value);
        }
    }

    private <T> T saveUnique(java.util.function.Supplier<T> save, String what) {
        try {
            return save.get();
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "That " + what + " is already used.");
        }
    }

    private long count(String sql, Object... args) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }

    private Map<String, Object> institutionView(Institution institution) {
        Map<String, Object> view = new HashMap<>();
        view.put("id", institution.getId());
        view.put("name", institution.getName());
        view.put("code", institution.getCode());
        view.put("city", institution.getCity());
        view.put("departmentCount", count("SELECT count(*) FROM department WHERE institution_id = ?", institution.getId()));
        view.put("userCount", count("SELECT count(*) FROM app_user WHERE institution_id = ?", institution.getId()));
        return view;
    }

    private Map<String, Object> departmentView(Department department) {
        Map<String, Object> view = new HashMap<>();
        view.put("id", department.getId());
        view.put("institutionId", department.getInstitutionId());
        view.put("name", department.getName());
        view.put("code", department.getCode());
        view.put("programCount", count("SELECT count(*) FROM program WHERE department_id = ?", department.getId()));
        view.put("userCount", count("SELECT count(*) FROM app_user WHERE department_id = ?", department.getId()));
        return view;
    }

    private Map<String, Object> programView(Program program) {
        Map<String, Object> view = new HashMap<>();
        view.put("id", program.getId());
        view.put("departmentId", program.getDepartmentId());
        view.put("name", program.getName());
        view.put("degree", program.getDegree());
        view.put("code", program.getCode());
        view.put("regulation", program.getRegulation());
        view.put("durationSemesters", program.getDurationSemesters());
        view.put("studentCount", count("SELECT count(*) FROM app_user WHERE program_id = ?", program.getId()));
        return view;
    }

    private static String required(Map<String, String> body, String key) {
        String value = body.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(key + " is required.");
        }
        return value.trim();
    }

    private static Long requiredLong(Map<String, String> body, String key) {
        try {
            return Long.valueOf(required(body, key));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(key + " must be a number.");
        }
    }

    private static String code(String value) {
        return value.trim().toUpperCase(Locale.ROOT).replaceAll("\\s+", "");
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static ResponseStatusException notFound(String what) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, what + " not found");
    }
}
