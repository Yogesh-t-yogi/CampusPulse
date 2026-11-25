package com.campuspulse.service.user;

import com.campuspulse.entity.org.Department;
import com.campuspulse.entity.org.Program;
import com.campuspulse.entity.user.AppUser;
import com.campuspulse.repository.org.DepartmentRepository;
import com.campuspulse.repository.org.ProgramRepository;
import com.campuspulse.repository.user.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Students edit their own USN, department, programme and batch year, only inside their college. */
class ProfileServiceTest {

    private final AppUserRepository users = mock(AppUserRepository.class);
    private final DepartmentRepository departments = mock(DepartmentRepository.class);
    private final ProgramRepository programs = mock(ProgramRepository.class);
    private final ProfileService service = new ProfileService(users, departments, programs);
    private AppUser student;

    @BeforeEach
    void setUp() {
        student = new AppUser();
        student.setId(7L);
        student.setRole("STUDENT");
        student.setInstitutionId(1L);
        student.setDepartmentId(10L);
        student.setProgramId(100L);
        when(users.findById(7L)).thenReturn(Optional.of(student));
        when(users.save(any(AppUser.class))).thenAnswer(call -> call.getArgument(0));
        when(departments.findByIdAndInstitutionId(anyLong(), eq(1L))).thenReturn(Optional.empty());
        when(departments.findByIdAndInstitutionId(eq(10L), eq(1L))).thenReturn(Optional.of(mock(Department.class)));
        when(departments.findByIdAndInstitutionId(eq(11L), eq(1L))).thenReturn(Optional.of(mock(Department.class)));
        Program mca = mock(Program.class);
        when(mca.getDepartmentId()).thenReturn(11L);
        when(programs.findById(200L)).thenReturn(Optional.of(mca));
    }

    @Test
    void studentUpdatesUsnDepartmentProgrammeAndYear() {
        AppUser saved = service.update(student, Map.of(
                "name", "Yogesh T", "rollNumber", " 1rf25mc116 ",
                "departmentId", "11", "programId", "200", "batchYear", "2025"));
        assertEquals("Yogesh T", saved.getName());
        assertEquals("1RF25MC116", saved.getRollNumber());
        assertEquals(11L, saved.getDepartmentId());
        assertEquals(200L, saved.getProgramId());
        assertEquals(2025, saved.getBatchYear());
    }

    @Test
    void changingDepartmentClearsTheOldProgramme() {
        AppUser saved = service.update(student, Map.of("departmentId", "11"));
        assertNull(saved.getProgramId());
    }

    @Test
    void departmentOfAnotherCollegeIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> service.update(student, Map.of("departmentId", "99")));
    }

    @Test
    void programmeOfAnotherDepartmentIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> service.update(student, Map.of("programId", "200")));
    }

    @Test
    void duplicateUsnInTheCollegeIsRefused() {
        when(users.existsByInstitutionIdAndRollNumberIgnoreCaseAndIdNot(eq(1L), anyString(), eq(7L))).thenReturn(true);
        assertThrows(IllegalArgumentException.class, () -> service.update(student, Map.of("rollNumber", "1RF25MC001")));
    }

    @Test
    void facultyCanOnlyChangeTheirName() {
        student.setRole("FACULTY");
        assertEquals("New Name", service.update(student, Map.of("name", "New Name")).getName());
        assertThrows(IllegalArgumentException.class, () -> service.update(student, Map.of("departmentId", "11")));
    }
}
