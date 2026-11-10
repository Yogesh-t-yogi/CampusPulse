
package com.campuspulse.service.syllabus;

import com.campuspulse.dto.syllabus.FullSyllabusResponse;
import com.campuspulse.entity.syllabus.Syllabus;
import com.campuspulse.entity.syllabus.Subject;
import com.campuspulse.entity.syllabus.Unit;
import com.campuspulse.entity.syllabus.Topic;
import com.campuspulse.repository.syllabus.SyllabusRepository;
import com.campuspulse.entity.org.Department;
import com.campuspulse.entity.org.Program;
import com.campuspulse.entity.user.AppUser;
import com.campuspulse.repository.embedding.TopicEmbeddingJdbcRepository;
import com.campuspulse.repository.org.DepartmentRepository;
import com.campuspulse.repository.org.ProgramRepository;
import com.campuspulse.service.CurrentUserService;
import com.campuspulse.service.org.TenantService;
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
    private final CurrentUserService currentUserService;
    private final TenantService tenantService;
    private final ProgramRepository programRepository;
    private final DepartmentRepository departmentRepository;

    public SyllabusService(
            SyllabusRepository syllabusRepository,
            JdbcTemplate jdbcTemplate,
            TopicEmbeddingJdbcRepository topicEmbeddingRepository,
            CurrentUserService currentUserService,
            TenantService tenantService,
            ProgramRepository programRepository,
            DepartmentRepository departmentRepository) {
        this.syllabusRepository = syllabusRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.topicEmbeddingRepository = topicEmbeddingRepository;
        this.currentUserService = currentUserService;
        this.tenantService = tenantService;
        this.programRepository = programRepository;
        this.departmentRepository = departmentRepository;
    }

    /**
     * Syllabi the current user may read (own + shared by college/department/program rules,
     * see SyllabusAccess), newest first. Light rows for tables and pickers.
     */
    public List<Map<String, Object>> listReadable(AppUser user) {
        Long college = tenantService.actingInstitutionId(user);
        String role = user.getRole();
        boolean collegeWide = "SUPER_ADMIN".equals(role) || "ADMIN".equals(role);
        boolean staff = "HOD".equals(role) || "FACULTY".equals(role);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT s.id, s.name, s.file_name AS "fileName", s.created_at AS "createdAt",
                       s.processing_status AS "processingStatus",
                       s.processing_message AS "processingMessage",
                       s.visibility, s.semester,
                       s.user_id AS "ownerId", owner.name AS "ownerName",
                       s.department_id AS "departmentId", d.name AS "departmentName",
                       s.program_id AS "programId", p.name AS "programName",
                       (SELECT count(*) FROM subject sb WHERE sb.syllabus_id = s.id) AS "subjectCount",
                       (SELECT count(*) FROM topic t
                          JOIN unit u ON u.id = t.unit_id
                          JOIN subject sb ON sb.id = u.subject_id
                         WHERE sb.syllabus_id = s.id) AS "topicCount"
                FROM syllabus s
                LEFT JOIN app_user owner ON owner.id = s.user_id
                LEFT JOIN department d ON d.id = s.department_id
                LEFT JOIN program p ON p.id = s.program_id
                WHERE s.user_id = ?
                   OR (s.institution_id = ? AND (
                          ?
                       OR (s.visibility = 'DEPARTMENT' AND ? AND s.department_id = ?)
                       OR (s.visibility = 'DEPARTMENT' AND ? AND s.program_id = ?)))
                ORDER BY s.created_at DESC
                """,
                user.getId(), college, collegeWide,
                staff, user.getDepartmentId(),
                "STUDENT".equals(role), user.getProgramId());

        for (Map<String, Object> row : rows) {
            boolean own = user.getId().equals(row.get("ownerId") == null ? null : ((Number) row.get("ownerId")).longValue());
            Object department = row.get("departmentId");
            boolean manage = own || collegeWide
                    || ("HOD".equals(role) && department != null && user.getDepartmentId() != null
                        && user.getDepartmentId().equals(((Number) department).longValue()));
            row.put("mine", own);
            row.put("canManage", manage);
        }
        return rows;
    }

    /** Syllabus the current user may read, else empty (callers answer 404). */
    public Optional<Syllabus> getSyllabusById(Long id, Long userId) {
        AppUser user = currentUserService.getCurrentUser();
        return syllabusRepository.findById(id).filter(syllabus -> SyllabusAccess.canRead(user, syllabus));
    }

    /** Syllabus the current user may change, else 404 (unreadable) or 403 (read-only). */
    public Syllabus requireManageable(Long id) {
        AppUser user = currentUserService.getCurrentUser();
        Syllabus syllabus = syllabusRepository.findById(id)
                .filter(found -> SyllabusAccess.canRead(user, found))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Syllabus not found"));
        if (!SyllabusAccess.canManage(user, syllabus)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only the owner, the HOD or the college admin can change this syllabus.");
        }
        return syllabus;
    }

    /** Edit name, program, semester and visibility. Only provided keys change. */
    @Transactional
    public Syllabus updateDetails(Long id, Map<String, Object> body) {
        Syllabus syllabus = requireManageable(id);
        if (body.containsKey("name")) {
            String name = body.get("name") == null ? "" : body.get("name").toString().trim();
            if (name.isEmpty()) {
                throw new IllegalArgumentException("Name can't be empty.");
            }
            syllabus.setName(name.length() > 255 ? name.substring(0, 255) : name);
        }
        if (body.containsKey("programId")) {
            applyProgram(syllabus, toLong(body.get("programId")));
        }
        if (body.containsKey("semester")) {
            syllabus.setSemester(validSemester(syllabus, toLong(body.get("semester"))));
        }
        if (body.containsKey("visibility")) {
            String visibility = String.valueOf(body.get("visibility")).toUpperCase(java.util.Locale.ROOT);
            if (!SyllabusAccess.PRIVATE.equals(visibility) && !SyllabusAccess.DEPARTMENT.equals(visibility)) {
                throw new IllegalArgumentException("Visibility must be PRIVATE or DEPARTMENT.");
            }
            syllabus.setVisibility(visibility);
        }
        return syllabusRepository.save(syllabus);
    }

    /** Program must be in the syllabus' college; it also sets the syllabus' department. */
    public void applyProgram(Syllabus syllabus, Long programId) {
        if (programId == null) {
            syllabus.setProgramId(null);
            return;
        }
        Program program = programRepository.findById(programId)
                .orElseThrow(() -> new IllegalArgumentException("Program not found."));
        Department department = departmentRepository.findById(program.getDepartmentId()).orElseThrow();
        if (!department.getInstitutionId().equals(syllabus.getInstitutionId())) {
            throw new IllegalArgumentException("Choose a program of this college.");
        }
        syllabus.setProgramId(program.getId());
        syllabus.setDepartmentId(department.getId());
    }

    public Integer validSemester(Syllabus syllabus, Long semester) {
        if (semester == null) {
            return null;
        }
        int max = syllabus.getProgramId() == null ? 12
                : programRepository.findById(syllabus.getProgramId()).map(Program::getDurationSemesters).orElse(12);
        if (semester < 1 || semester > max) {
            throw new IllegalArgumentException("Semester must be between 1 and " + max + ".");
        }
        return semester.intValue();
    }

    private static Long toLong(Object value) {
        if (value == null || value.toString().isBlank() || "null".equals(value.toString())) {
            return null;
        }
        try {
            return Long.valueOf(value.toString().trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid number: " + value);
        }
    }

    /** Deletes the syllabus with its curriculum, vectors and saved reports. */
    @Transactional
    public void delete(Long id, Long ignoredUserId) {
        Syllabus syllabus = requireManageable(id);
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


    // Get the complete syllabus hierarchy for its owner
    @Transactional(readOnly = true)
    public Optional<FullSyllabusResponse> getFullSyllabusById(
            Long id, Long userId) {

        return getSyllabusById(id, userId)
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
