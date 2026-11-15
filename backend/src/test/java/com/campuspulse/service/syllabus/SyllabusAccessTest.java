package com.campuspulse.service.syllabus;

import com.campuspulse.entity.syllabus.Syllabus;
import com.campuspulse.entity.user.AppUser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SyllabusAccessTest {

    private static AppUser user(long id, String role, Long college, Long department, Long program) {
        AppUser user = new AppUser();
        user.setId(id);
        user.setRole(role);
        user.setInstitutionId(college);
        user.setDepartmentId(department);
        user.setProgramId(program);
        return user;
    }

    // Uploaded by faculty #10 in college 1, department CSE (3), program B.E. CSE (7).
    private static Syllabus syllabus(String visibility) {
        Syllabus syllabus = new Syllabus();
        syllabus.setOwner(user(10, "FACULTY", 1L, 3L, null));
        syllabus.setInstitutionId(1L);
        syllabus.setDepartmentId(3L);
        syllabus.setProgramId(7L);
        syllabus.setVisibility(visibility);
        return syllabus;
    }

    @Test
    void departmentSyllabusIsReadByItsDepartmentAndProgram() {
        Syllabus shared = syllabus(SyllabusAccess.DEPARTMENT);
        assertTrue(SyllabusAccess.canRead(user(11, "FACULTY", 1L, 3L, null), shared));
        assertTrue(SyllabusAccess.canRead(user(20, "STUDENT", 1L, 3L, 7L), shared));
        assertFalse(SyllabusAccess.canRead(user(12, "FACULTY", 1L, 4L, null), shared), "other department");
        assertFalse(SyllabusAccess.canRead(user(21, "STUDENT", 1L, 3L, 8L), shared), "other program");
        assertFalse(SyllabusAccess.canRead(user(13, "FACULTY", 2L, 3L, null), shared), "other college");
    }

    @Test
    void privateSyllabusOnlyOwnerAndManagers() {
        Syllabus personal = syllabus(SyllabusAccess.PRIVATE);
        assertTrue(SyllabusAccess.canRead(user(10, "FACULTY", 1L, 3L, null), personal));
        assertFalse(SyllabusAccess.canRead(user(11, "FACULTY", 1L, 3L, null), personal));
        assertFalse(SyllabusAccess.canRead(user(20, "STUDENT", 1L, 3L, 7L), personal));
        assertTrue(SyllabusAccess.canRead(user(30, "HOD", 1L, 3L, null), personal));
        assertTrue(SyllabusAccess.canRead(user(40, "ADMIN", 1L, null, null), personal));
    }

    @Test
    void whoMayChange() {
        Syllabus shared = syllabus(SyllabusAccess.DEPARTMENT);
        assertTrue(SyllabusAccess.canManage(user(10, "FACULTY", 1L, 3L, null), shared), "owner");
        assertFalse(SyllabusAccess.canManage(user(11, "FACULTY", 1L, 3L, null), shared), "colleague reads only");
        assertTrue(SyllabusAccess.canManage(user(30, "HOD", 1L, 3L, null), shared));
        assertFalse(SyllabusAccess.canManage(user(31, "HOD", 1L, 4L, null), shared));
        assertTrue(SyllabusAccess.canManage(user(40, "ADMIN", 1L, null, null), shared));
        assertFalse(SyllabusAccess.canManage(user(41, "ADMIN", 2L, null, null), shared));
        assertTrue(SyllabusAccess.canManage(user(50, "SUPER_ADMIN", 9L, null, null), shared));
        assertFalse(SyllabusAccess.canManage(user(20, "STUDENT", 1L, 3L, 7L), shared));
    }

    @Test
    void defaultVisibility() {
        assertEquals(SyllabusAccess.PRIVATE, SyllabusAccess.defaultVisibility(user(1, "STUDENT", 1L, 3L, 7L)));
        assertEquals(SyllabusAccess.DEPARTMENT, SyllabusAccess.defaultVisibility(user(2, "FACULTY", 1L, 3L, null)));
    }
}
