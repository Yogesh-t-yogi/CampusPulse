package com.campuspulse.service.user;

import com.campuspulse.entity.org.Program;
import com.campuspulse.entity.user.AppUser;
import com.campuspulse.repository.org.DepartmentRepository;
import com.campuspulse.repository.org.ProgramRepository;
import com.campuspulse.repository.user.AppUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Year;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Self-service profile edit (PUT /api/auth/me).
 * Everyone: name. Students: also roll number / USN, department, programme and batch year,
 * always inside their own college. Email and role stay with the HOD / college admin.
 */
@Service
public class ProfileService {

    private final AppUserRepository users;
    private final DepartmentRepository departments;
    private final ProgramRepository programs;

    public ProfileService(AppUserRepository users, DepartmentRepository departments, ProgramRepository programs) {
        this.users = users;
        this.departments = departments;
        this.programs = programs;
    }

    @Transactional
    public AppUser update(AppUser current, Map<String, String> body) {
        AppUser user = users.findById(current.getId()).orElseThrow();

        if (body.containsKey("name")) {
            String name = trim(body.get("name"));
            if (name == null || name.length() < 2 || name.length() > 100) {
                throw new IllegalArgumentException("Name must be 2 to 100 characters.");
            }
            user.setName(name);
        }

        if ("STUDENT".equals(user.getRole())) {
            if (body.containsKey("rollNumber")) {
                user.setRollNumber(rollNumber(user, body.get("rollNumber")));
            }
            if (body.containsKey("departmentId")) {
                Long departmentId = id(body.get("departmentId"));
                if (departmentId == null) {
                    throw new IllegalArgumentException("Choose your department.");
                }
                departments.findByIdAndInstitutionId(departmentId, user.getInstitutionId())
                        .orElseThrow(() -> new IllegalArgumentException("That department is not in your college."));
                if (!Objects.equals(departmentId, user.getDepartmentId())) {
                    user.setProgramId(null);       // a programme belongs to one department
                }
                user.setDepartmentId(departmentId);
            }
            if (body.containsKey("programId")) {
                Long programId = id(body.get("programId"));
                if (programId != null) {
                    Program program = programs.findById(programId)
                            .orElseThrow(() -> new IllegalArgumentException("Programme not found."));
                    if (!Objects.equals(program.getDepartmentId(), user.getDepartmentId())) {
                        throw new IllegalArgumentException("That programme is not in your department.");
                    }
                }
                user.setProgramId(programId);
            }
            if (body.containsKey("batchYear")) {
                user.setBatchYear(batchYear(body.get("batchYear")));
            }
        } else if (body.containsKey("rollNumber") || body.containsKey("departmentId")
                || body.containsKey("programId") || body.containsKey("batchYear")) {
            throw new IllegalArgumentException("Your department and programme are managed by your college admin.");
        }

        return users.save(user);
    }

    private String rollNumber(AppUser user, String raw) {
        String value = trim(raw);
        if (value == null) {
            return null;
        }
        value = value.toUpperCase(Locale.ROOT);
        if (!value.matches("[A-Z0-9/-]{3,20}")) {
            throw new IllegalArgumentException("USN can have 3 to 20 letters, digits, '-' or '/'.");
        }
        if (users.existsByInstitutionIdAndRollNumberIgnoreCaseAndIdNot(user.getInstitutionId(), value, user.getId())) {
            throw new IllegalArgumentException("Another student in your college already has this USN.");
        }
        return value;
    }

    private static Integer batchYear(String raw) {
        String value = trim(raw);
        if (value == null) {
            return null;
        }
        int year;
        try {
            year = Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Batch year must be a year, e.g. 2025.");
        }
        int now = Year.now().getValue();
        if (year < 2000 || year > now + 6) {
            throw new IllegalArgumentException("Batch year must be between 2000 and " + (now + 6) + ".");
        }
        return year;
    }

    private static Long id(String raw) {
        String value = trim(raw);
        if (value == null) {
            return null;
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid id: " + value);
        }
    }

    private static String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
