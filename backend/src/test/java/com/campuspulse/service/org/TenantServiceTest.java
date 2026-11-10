package com.campuspulse.service.org;

import com.campuspulse.entity.org.Department;
import com.campuspulse.entity.user.AppUser;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TenantServiceTest {

    private final TenantService tenant = new TenantService(null);

    private static AppUser user(String role, Long college, Long department) {
        AppUser user = new AppUser();
        user.setId(1L);
        user.setRole(role);
        user.setInstitutionId(college);
        user.setDepartmentId(department);
        return user;
    }

    private static Department department(long id, long college) {
        Department department = new Department();
        ReflectionTestUtils.setField(department, "id", id);
        department.setInstitutionId(college);
        return department;
    }

    @Test
    void roleGranting() {
        AppUser admin = user("ADMIN", 1L, null);
        assertDoesNotThrow(() -> tenant.requireCanAssignRole(admin, "ADMIN"));
        assertDoesNotThrow(() -> tenant.requireCanAssignRole(admin, "HOD"));
        assertThrows(ResponseStatusException.class, () -> tenant.requireCanAssignRole(admin, "SUPER_ADMIN"));

        AppUser hod = user("HOD", 1L, 3L);
        assertDoesNotThrow(() -> tenant.requireCanAssignRole(hod, "FACULTY"));
        assertDoesNotThrow(() -> tenant.requireCanAssignRole(hod, "STUDENT"));
        assertThrows(ResponseStatusException.class, () -> tenant.requireCanAssignRole(hod, "HOD"));

        assertThrows(ResponseStatusException.class,
                () -> tenant.requireCanAssignRole(user("FACULTY", 1L, 3L), "STUDENT"));
        assertThrows(ResponseStatusException.class, () -> tenant.requireCanAssignRole(admin, "OWNER"));
    }

    @Test
    void collegeAdminOnlyInOwnCollege() {
        assertDoesNotThrow(() -> tenant.requireCollegeAdmin(user("ADMIN", 1L, null), 1L));
        assertThrows(ResponseStatusException.class, () -> tenant.requireCollegeAdmin(user("ADMIN", 1L, null), 2L));
        assertThrows(ResponseStatusException.class, () -> tenant.requireCollegeAdmin(user("HOD", 1L, 3L), 1L));
        assertDoesNotThrow(() -> tenant.requireCollegeAdmin(user("SUPER_ADMIN", 1L, null), 2L));
    }

    @Test
    void departmentManagers() {
        Department cse = department(3, 1);
        assertDoesNotThrow(() -> tenant.requireDepartmentManager(user("HOD", 1L, 3L), cse));
        assertThrows(ResponseStatusException.class, () -> tenant.requireDepartmentManager(user("HOD", 1L, 4L), cse));
        assertDoesNotThrow(() -> tenant.requireDepartmentManager(user("ADMIN", 1L, null), cse));
        assertThrows(ResponseStatusException.class, () -> tenant.requireDepartmentManager(user("ADMIN", 2L, null), cse));
        assertThrows(ResponseStatusException.class, () -> tenant.requireDepartmentManager(user("FACULTY", 1L, 3L), cse));
    }
}
