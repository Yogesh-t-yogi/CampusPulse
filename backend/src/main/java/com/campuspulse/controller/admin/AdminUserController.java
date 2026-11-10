package com.campuspulse.controller.admin;

import com.campuspulse.entity.job.JobPosting;
import com.campuspulse.entity.org.Department;
import com.campuspulse.entity.org.Program;
import com.campuspulse.entity.syllabus.Syllabus;
import com.campuspulse.entity.user.AppUser;
import com.campuspulse.repository.job.JobPostingRepository;
import com.campuspulse.repository.job.JobRequiredSkillRepository;
import com.campuspulse.repository.org.DepartmentRepository;
import com.campuspulse.repository.org.ProgramRepository;
import com.campuspulse.repository.syllabus.SyllabusRepository;
import com.campuspulse.repository.user.AppUserRepository;
import com.campuspulse.service.CurrentUserService;
import com.campuspulse.service.auth.TempPasswords;
import com.campuspulse.service.org.TenantService;
import com.campuspulse.service.syllabus.SyllabusService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Account management (SecurityConfig: SUPER_ADMIN, ADMIN, HOD).
 * SUPER_ADMIN: the acting college. ADMIN: own college. HOD: own department, faculty and students only.
 */
@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final AppUserRepository appUserRepository;
    private final CurrentUserService currentUserService;
    private final PasswordEncoder passwordEncoder;
    private final SyllabusRepository syllabusRepository;
    private final SyllabusService syllabusService;
    private final JobPostingRepository jobPostingRepository;
    private final JobRequiredSkillRepository jobRequiredSkillRepository;
    private final TenantService tenantService;
    private final DepartmentRepository departmentRepository;
    private final ProgramRepository programRepository;

    public AdminUserController(
            AppUserRepository appUserRepository,
            CurrentUserService currentUserService,
            PasswordEncoder passwordEncoder,
            SyllabusRepository syllabusRepository,
            SyllabusService syllabusService,
            JobPostingRepository jobPostingRepository,
            JobRequiredSkillRepository jobRequiredSkillRepository,
            TenantService tenantService,
            DepartmentRepository departmentRepository,
            ProgramRepository programRepository) {
        this.appUserRepository = appUserRepository;
        this.currentUserService = currentUserService;
        this.passwordEncoder = passwordEncoder;
        this.syllabusRepository = syllabusRepository;
        this.syllabusService = syllabusService;
        this.jobPostingRepository = jobPostingRepository;
        this.jobRequiredSkillRepository = jobRequiredSkillRepository;
        this.tenantService = tenantService;
        this.departmentRepository = departmentRepository;
        this.programRepository = programRepository;
    }

    @GetMapping
    public List<Map<String, Object>> listUsers() {
        AppUser actor = me();
        Long college = tenantService.actingInstitutionId(actor);
        Map<Long, Department> departments = departmentsOf(college);
        Map<Long, Program> programs = programsOf(departments);
        return appUserRepository.findAll().stream()
                .filter(user -> Objects.equals(user.getInstitutionId(), college))
                .filter(user -> !"HOD".equals(actor.getRole())
                        || user.getId().equals(actor.getId())
                        || (Objects.equals(user.getDepartmentId(), actor.getDepartmentId())
                            && TenantService.rank(user.getRole()) < TenantService.rank("HOD")))
                .sorted(Comparator.comparing(AppUser::getId))
                .map(user -> toView(user, departments, programs))
                .toList();
    }

    /** Create an account in the acting college; the one-time password is returned once. */
    @PostMapping
    public Map<String, Object> createUser(@RequestBody Map<String, String> body) {
        AppUser actor = me();
        String email = required(body.get("email"), "Email").toLowerCase(Locale.ROOT);
        if (!email.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) {
            throw bad("Enter a valid email address.");
        }
        if (appUserRepository.existsByEmail(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with this email already exists.");
        }
        AppUser user = new AppUser();
        user.setName(required(body.get("name"), "Name"));
        user.setEmail(email);
        user.setInstitutionId(tenantService.actingInstitutionId(actor));
        applyRoleAndPlacement(actor, user, body);

        String password = TempPasswords.generate();
        user.setPassword(passwordEncoder.encode(password));
        Map<String, Object> view = view(appUserRepository.save(user));
        view.put("temporaryPassword", password);
        return view;
    }

    /** Change role, department and/or program. */
    @PutMapping("/{id}")
    public Map<String, Object> updateUser(@PathVariable Long id, @RequestBody Map<String, String> body) {
        AppUser user = requireManageable(id);
        applyRoleAndPlacement(me(), user, body);
        return view(appUserRepository.save(user));
    }

    @PutMapping("/{id}/role")
    public Map<String, Object> changeRole(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return updateUser(id, body);
    }

    @PostMapping("/{id}/reset-password")
    public Map<String, Object> resetPassword(@PathVariable Long id) {
        AppUser user = requireManageable(id);
        String password = TempPasswords.generate();
        user.setPassword(passwordEncoder.encode(password));
        Map<String, Object> view = view(appUserRepository.save(user));
        view.put("temporaryPassword", password);
        return view;
    }

    /** Delete an account with its own syllabi and private jobs. Catalog jobs stay. */
    @Transactional
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        AppUser user = requireManageable(id);
        for (Syllabus syllabus : syllabusRepository.findAllByOwner_Id(id)) {
            syllabusService.delete(syllabus.getId(), id);
        }
        for (JobPosting job : jobPostingRepository.findAllByOwner_Id(id)) {
            jobRequiredSkillRepository.deleteAll(jobRequiredSkillRepository.findByJobPostingId(job.getId()));
            jobPostingRepository.delete(job);
        }
        appUserRepository.delete(user);
        return ResponseEntity.noContent().build();
    }

    // ---------- rules ----------

    private void applyRoleAndPlacement(AppUser actor, AppUser user, Map<String, String> body) {
        String role = body.containsKey("role")
                ? body.get("role").trim().toUpperCase(Locale.ROOT)
                : (user.getRole() == null ? "STUDENT" : user.getRole());
        tenantService.requireCanAssignRole(actor, role);
        user.setRole(role);

        if (body.containsKey("departmentId")) {
            Long departmentId = optionalLong(body.get("departmentId"));
            if (departmentId != null) {
                Department department = departmentRepository.findById(departmentId)
                        .filter(d -> d.getInstitutionId().equals(user.getInstitutionId()))
                        .orElseThrow(() -> bad("Choose a department of this college."));
                if ("HOD".equals(actor.getRole()) && !department.getId().equals(actor.getDepartmentId())) {
                    throw TenantService.forbidden("HODs can only place people in their own department.");
                }
            }
            user.setDepartmentId(departmentId);
        } else if ("HOD".equals(actor.getRole()) && user.getDepartmentId() == null) {
            user.setDepartmentId(actor.getDepartmentId());
        }

        if (body.containsKey("programId")) {
            Long programId = optionalLong(body.get("programId"));
            if (programId != null) {
                Program program = programRepository.findById(programId)
                        .orElseThrow(() -> bad("Program not found."));
                if (!program.getDepartmentId().equals(user.getDepartmentId())) {
                    throw bad("The program must belong to the person's department.");
                }
            }
            user.setProgramId(programId);
        }
    }

    private AppUser requireManageable(Long id) {
        AppUser actor = me();
        if (actor.getId().equals(id)) {
            throw bad("You can't change or delete your own account here.");
        }
        AppUser user = appUserRepository.findById(id).orElseThrow(() -> notFound());
        boolean ok;
        if (tenantService.isSuperAdmin(actor)) {
            ok = true;
        } else if ("ADMIN".equals(actor.getRole())) {
            ok = Objects.equals(user.getInstitutionId(), actor.getInstitutionId())
                    && TenantService.rank(user.getRole()) <= TenantService.rank("ADMIN");
        } else {
            ok = Objects.equals(user.getInstitutionId(), actor.getInstitutionId())
                    && Objects.equals(user.getDepartmentId(), actor.getDepartmentId())
                    && TenantService.rank(user.getRole()) < TenantService.rank("HOD");
        }
        if (!ok) {
            throw notFound();
        }
        return user;
    }

    // ---------- views ----------

    private Map<Long, Department> departmentsOf(Long college) {
        Map<Long, Department> map = new HashMap<>();
        if (college != null) {
            departmentRepository.findAllByInstitutionIdOrderByNameAsc(college).forEach(d -> map.put(d.getId(), d));
        }
        return map;
    }

    private Map<Long, Program> programsOf(Map<Long, Department> departments) {
        Map<Long, Program> map = new HashMap<>();
        if (!departments.isEmpty()) {
            programRepository.findAllByDepartmentIdInOrderByNameAsc(List.copyOf(departments.keySet()))
                    .forEach(p -> map.put(p.getId(), p));
        }
        return map;
    }

    private Map<String, Object> view(AppUser user) {
        Map<Long, Department> departments = departmentsOf(user.getInstitutionId());
        return toView(user, departments, programsOf(departments));
    }

    private static Map<String, Object> toView(AppUser user, Map<Long, Department> departments, Map<Long, Program> programs) {
        Map<String, Object> view = new HashMap<>();
        view.put("id", user.getId());
        view.put("name", user.getName());
        view.put("email", user.getEmail());
        view.put("role", user.getRole());
        view.put("createdAt", String.valueOf(user.getCreatedAt()));
        view.put("institutionId", user.getInstitutionId());
        view.put("departmentId", user.getDepartmentId());
        view.put("departmentName", user.getDepartmentId() == null || !departments.containsKey(user.getDepartmentId())
                ? null : departments.get(user.getDepartmentId()).getName());
        view.put("programId", user.getProgramId());
        view.put("programName", user.getProgramId() == null || !programs.containsKey(user.getProgramId())
                ? null : programs.get(user.getProgramId()).getName());
        return view;
    }

    private AppUser me() {
        return currentUserService.getCurrentUser();
    }

    private static Long optionalLong(String value) {
        if (value == null || value.isBlank() || "null".equals(value)) {
            return null;
        }
        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException e) {
            throw bad("Invalid id: " + value);
        }
    }

    private static String required(String value, String label) {
        if (value == null || value.isBlank()) {
            throw bad(label + " is required.");
        }
        return value.trim();
    }

    private static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
    }
}
