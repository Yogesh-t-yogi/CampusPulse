package com.campuspulse.service.syllabus;

import com.campuspulse.entity.syllabus.Syllabus;
import com.campuspulse.entity.user.AppUser;

import java.util.Objects;

/**
 * Who may read / change a syllabus (Phase 6.3).
 * Read: owner · platform admin · college admin of its college · for DEPARTMENT-visible
 * syllabi also HOD/faculty of its department and students of its program.
 * Change (rename, edit curriculum, re-process, delete): owner · platform admin ·
 * college admin of its college · HOD of its department.
 */
public final class SyllabusAccess {

    public static final String PRIVATE = "PRIVATE";
    public static final String DEPARTMENT = "DEPARTMENT";

    private SyllabusAccess() {
    }

    public static boolean canRead(AppUser user, Syllabus syllabus) {
        if (canManage(user, syllabus)) {
            return true;
        }
        if (!sameCollege(user, syllabus) || !DEPARTMENT.equals(syllabus.getVisibility())) {
            return false;
        }
        return switch (user.getRole()) {
            case "HOD", "FACULTY" -> syllabus.getDepartmentId() != null
                    && syllabus.getDepartmentId().equals(user.getDepartmentId());
            case "STUDENT" -> syllabus.getProgramId() != null
                    && syllabus.getProgramId().equals(user.getProgramId());
            default -> false;
        };
    }

    public static boolean canManage(AppUser user, Syllabus syllabus) {
        if (isOwner(user, syllabus) || "SUPER_ADMIN".equals(user.getRole())) {
            return true;
        }
        if (!sameCollege(user, syllabus)) {
            return false;
        }
        return "ADMIN".equals(user.getRole())
                || ("HOD".equals(user.getRole())
                    && syllabus.getDepartmentId() != null
                    && syllabus.getDepartmentId().equals(user.getDepartmentId()));
    }

    /** Staff uploads are shared with the department; student uploads stay private. */
    public static String defaultVisibility(AppUser uploader) {
        return "STUDENT".equals(uploader.getRole()) ? PRIVATE : DEPARTMENT;
    }

    private static boolean isOwner(AppUser user, Syllabus syllabus) {
        return syllabus.getOwner() != null && user.getId().equals(syllabus.getOwner().getId());
    }

    private static boolean sameCollege(AppUser user, Syllabus syllabus) {
        return syllabus.getInstitutionId() != null
                && Objects.equals(syllabus.getInstitutionId(), user.getInstitutionId());
    }
}
