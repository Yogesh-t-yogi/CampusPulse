package com.campuspulse.config;

import com.campuspulse.entity.org.Department;
import com.campuspulse.entity.org.Institution;
import com.campuspulse.entity.org.Program;
import com.campuspulse.entity.user.AppUser;
import com.campuspulse.repository.org.DepartmentRepository;
import com.campuspulse.repository.org.InstitutionRepository;
import com.campuspulse.repository.org.ProgramRepository;
import com.campuspulse.repository.user.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/**
 * Local/demo only: when DEMO_USERS_PASSWORD is set, makes sure a demo college exists
 * ("CampusPulse Demo College", code DEMO, departments CSE/IT/ECE/MCA with programs) and one
 * account per role, placed in it:
 *   superadmin@ (platform) · admin@ (college admin of DEMO) · hod@ + faculty@ (CSE) ·
 *   student@ (CSE, B.E. CSE program).
 * Existing accounts keep their password; they are moved into the demo college only while
 * they are still in the default college (so later manual changes are respected).
 * Leave DEMO_USERS_PASSWORD unset on any shared or real server.
 */
@Component
@Order(10)
public class DemoUserSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoUserSeeder.class);

    private record DemoUser(String name, String email, String role, String departmentCode, String programCode) {
    }

    private record DemoProgram(String departmentCode, String name, String degree, String code,
                               String regulation, int semesters) {
    }

    private static final List<DemoUser> DEMO_USERS = List.of(
            new DemoUser("Demo Platform Admin", "superadmin@campuspulse.local", "SUPER_ADMIN", null, null),
            new DemoUser("Demo College Admin", "admin@campuspulse.local", "ADMIN", null, null),
            new DemoUser("Demo HOD (CSE)", "hod@campuspulse.local", "HOD", "CSE", null),
            new DemoUser("Demo Faculty (CSE)", "faculty@campuspulse.local", "FACULTY", "CSE", null),
            new DemoUser("Demo Student (B.E. CSE)", "student@campuspulse.local", "STUDENT", "CSE", "CSE"));

    private static final List<String[]> DEPARTMENTS = List.of(
            new String[]{"CSE", "Computer Science and Engineering"},
            new String[]{"IT", "Information Technology"},
            new String[]{"ECE", "Electronics and Communication Engineering"},
            new String[]{"MCA", "Computer Applications"});

    private static final List<DemoProgram> PROGRAMS = List.of(
            new DemoProgram("CSE", "B.E. Computer Science and Engineering", "BE", "CSE", "2021", 8),
            new DemoProgram("IT", "B.Tech Information Technology", "BTECH", "IT", "2021", 8),
            new DemoProgram("ECE", "B.E. Electronics and Communication Engineering", "BE", "ECE", "2021", 8),
            new DemoProgram("MCA", "Master of Computer Applications", "MCA", "MCA", "2023", 4));

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final InstitutionRepository institutionRepository;
    private final DepartmentRepository departmentRepository;
    private final ProgramRepository programRepository;
    private final JdbcTemplate jdbcTemplate;
    private final String password;

    public DemoUserSeeder(
            AppUserRepository appUserRepository,
            PasswordEncoder passwordEncoder,
            InstitutionRepository institutionRepository,
            DepartmentRepository departmentRepository,
            ProgramRepository programRepository,
            JdbcTemplate jdbcTemplate,
            @Value("${app.demo-users-password:}") String password) {
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.institutionRepository = institutionRepository;
        this.departmentRepository = departmentRepository;
        this.programRepository = programRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (password == null || password.isBlank()) {
            return;
        }
        Institution college = demoCollege();
        Long defaultCollegeId = institutionRepository.findByCodeIgnoreCase("DEFAULT")
                .map(Institution::getId).orElse(null);

        for (DemoUser demo : DEMO_USERS) {
            AppUser user = appUserRepository.findByEmail(demo.email()).orElse(null);
            boolean isNew = user == null;
            if (isNew) {
                user = new AppUser();
                user.setName(demo.name());
                user.setEmail(demo.email());
                user.setPassword(passwordEncoder.encode(password));
                user.setRole(demo.role());
            } else if (user.getInstitutionId() != null
                    && !Objects.equals(user.getInstitutionId(), defaultCollegeId)) {
                continue; // already placed somewhere on purpose
            }

            Department department = demo.departmentCode() == null ? null : department(college, demo.departmentCode());
            user.setInstitutionId(college.getId());
            user.setDepartmentId(department == null ? null : department.getId());
            user.setProgramId(demo.programCode() == null || department == null ? null
                    : programRepository.findAllByDepartmentIdOrderByNameAsc(department.getId()).stream()
                        .filter(program -> demo.programCode().equals(program.getCode()))
                        .map(Program::getId)
                        .findFirst()
                        .orElse(null));
            AppUser saved = appUserRepository.save(user);
            // Their syllabi move with them.
            jdbcTemplate.update(
                    "UPDATE syllabus SET institution_id = ?, department_id = ? WHERE user_id = ?",
                    saved.getInstitutionId(), saved.getDepartmentId(), saved.getId());
            log.info("{} demo account {} ({}) in {}", isNew ? "Created" : "Placed",
                    demo.email(), demo.role(), college.getCode());
        }
    }

    private Institution demoCollege() {
        Institution college = institutionRepository.findByCodeIgnoreCase("DEMO").orElseGet(() -> {
            Institution created = new Institution();
            created.setName("CampusPulse Demo College");
            created.setCode("DEMO");
            created.setCity("Chennai");
            log.info("Created demo college DEMO");
            return institutionRepository.save(created);
        });

        for (String[] entry : DEPARTMENTS) {
            if (department(college, entry[0]) == null) {
                Department department = new Department();
                department.setInstitutionId(college.getId());
                department.setCode(entry[0]);
                department.setName(entry[1]);
                departmentRepository.save(department);
            }
        }
        for (DemoProgram demo : PROGRAMS) {
            Department department = department(college, demo.departmentCode());
            boolean exists = programRepository.findAllByDepartmentIdOrderByNameAsc(department.getId()).stream()
                    .anyMatch(program -> demo.code().equals(program.getCode())
                            && Objects.equals(demo.regulation(), program.getRegulation()));
            if (!exists) {
                Program program = new Program();
                program.setDepartmentId(department.getId());
                program.setName(demo.name());
                program.setDegree(demo.degree());
                program.setCode(demo.code());
                program.setRegulation(demo.regulation());
                program.setDurationSemesters(demo.semesters());
                programRepository.save(program);
            }
        }
        return college;
    }

    private Department department(Institution college, String code) {
        return departmentRepository.findAllByInstitutionIdOrderByNameAsc(college.getId()).stream()
                .filter(department -> code.equals(department.getCode()))
                .findFirst()
                .orElse(null);
    }
}
