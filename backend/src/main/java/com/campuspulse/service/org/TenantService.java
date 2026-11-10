package com.campuspulse.service.org;

import com.campuspulse.entity.org.Department;
import com.campuspulse.entity.user.AppUser;
import com.campuspulse.repository.org.InstitutionRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * Who may act where. A request acts in one college: the user's own, or — for a
 * SUPER_ADMIN only — the one named in the X-Institution-Id header.
 * Role order: SUPER_ADMIN > ADMIN > HOD > FACULTY > STUDENT.
 */
@Service
public class TenantService {

    public static final String HEADER = "X-Institution-Id";

    private static final Map<String, Integer> RANK = Map.of(
            "SUPER_ADMIN", 4, "ADMIN", 3, "HOD", 2, "FACULTY", 1, "STUDENT", 0);

    private final InstitutionRepository institutionRepository;

    public TenantService(InstitutionRepository institutionRepository) {
        this.institutionRepository = institutionRepository;
    }

    public static int rank(String role) {
        return RANK.getOrDefault(role, -1);
    }

    public static boolean isRole(String role) {
        return RANK.containsKey(role);
    }

    public boolean isSuperAdmin(AppUser user) {
        return "SUPER_ADMIN".equals(user.getRole());
    }

    /** College the request acts in (may be null only for a super admin without a college). */
    public Long actingInstitutionId(AppUser user) {
        if (isSuperAdmin(user)) {
            String header = headerValue();
            if (header != null && !header.isBlank()) {
                try {
                    Long id = Long.valueOf(header.trim());
                    if (institutionRepository.existsById(id)) {
                        return id;
                    }
                } catch (NumberFormatException ignored) {
                    // fall through to the user's own college
                }
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown college.");
            }
        }
        return user.getInstitutionId();
    }

    /** College-level management (departments, programs, users, college catalog). */
    public void requireCollegeAdmin(AppUser user, Long institutionId) {
        boolean ok = isSuperAdmin(user)
                || ("ADMIN".equals(user.getRole()) && institutionId != null
                    && institutionId.equals(user.getInstitutionId()));
        if (!ok) {
            throw forbidden("Only a college admin can do this.");
        }
    }

    /** Department-level management: college admin, or the HOD of that department. */
    public void requireDepartmentManager(AppUser user, Department department) {
        if (isSuperAdmin(user)) {
            return;
        }
        boolean sameCollege = department.getInstitutionId().equals(user.getInstitutionId());
        boolean ok = sameCollege && ("ADMIN".equals(user.getRole())
                || ("HOD".equals(user.getRole()) && department.getId().equals(user.getDepartmentId())));
        if (!ok) {
            throw forbidden("Only the college admin or this department's HOD can do this.");
        }
    }

    /** Only HOD and above assign roles: admins their own role or lower, HODs only lower roles. */
    public void requireCanAssignRole(AppUser actor, String role) {
        if (!isRole(role)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Role must be SUPER_ADMIN, ADMIN, HOD, FACULTY or STUDENT.");
        }
        int mine = rank(actor.getRole());
        // Only HOD and above manage accounts at all.
        boolean ok = mine >= rank("HOD") && rank(role) < mine
                || (rank(role) == mine && mine >= rank("ADMIN"));
        if (!ok) {
            throw forbidden("You can't give the " + role + " role.");
        }
    }

    public static ResponseStatusException forbidden(String message) {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, message);
    }

    private static String headerValue() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            HttpServletRequest request = attributes.getRequest();
            return request.getHeader(HEADER);
        }
        return null;
    }
}
