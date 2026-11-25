package com.campuspulse.service.org;

import com.campuspulse.entity.job.JobPosting;
import com.campuspulse.entity.syllabus.Syllabus;
import com.campuspulse.entity.user.AppUser;
import com.campuspulse.repository.job.JobPostingRepository;
import com.campuspulse.repository.org.InstitutionRepository;
import com.campuspulse.repository.syllabus.SyllabusRepository;
import com.campuspulse.service.CurrentUserService;
import com.campuspulse.service.job.JobPostingService;
import com.campuspulse.service.syllabus.SyllabusAccess;
import com.campuspulse.service.syllabus.SyllabusService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

import java.util.Objects;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** TC-06: a user of college A cannot read or change college B's syllabus or job. */
class TenantIsolationTest {

    private static final long COLLEGE_A = 1L;
    private static final long COLLEGE_B = 2L;

    private final InstitutionRepository institutions = mock(InstitutionRepository.class);
    private final TenantService tenant = new TenantService(institutions);
    private final CurrentUserService currentUser = mock(CurrentUserService.class);

    @AfterEach
    void clearRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    private static AppUser user(long id, String role, long college, Long department) {
        AppUser user = new AppUser();
        user.setId(id);
        user.setRole(role);
        user.setInstitutionId(college);
        user.setDepartmentId(department);
        return user;
    }

    /** Department-shared syllabus uploaded by a faculty member of college B. */
    private static Syllabus collegeBSyllabus() {
        Syllabus syllabus = new Syllabus();
        syllabus.setOwner(user(90, "FACULTY", COLLEGE_B, 3L));
        syllabus.setInstitutionId(COLLEGE_B);
        syllabus.setDepartmentId(3L);
        syllabus.setProgramId(7L);
        syllabus.setVisibility(SyllabusAccess.DEPARTMENT);
        return syllabus;
    }

    private SyllabusService syllabusService(SyllabusRepository repository) {
        return new SyllabusService(repository, null, null, currentUser, tenant, null, null);
    }

    @Test
    void tc06_collegeAUsersCannotReadCollegeBSyllabus() {
        SyllabusRepository repository = mock(SyllabusRepository.class);
        Syllabus foreign = collegeBSyllabus();
        when(repository.findById(42L)).thenReturn(Optional.of(foreign));
        SyllabusService service = syllabusService(repository);

        // Same department id and program id as the syllabus, but another college: still no access.
        for (AppUser reader : new AppUser[] {
                user(1, "FACULTY", COLLEGE_A, 3L),
                user(2, "HOD", COLLEGE_A, 3L),
                user(3, "ADMIN", COLLEGE_A, null)}) {
            when(currentUser.getCurrentUser()).thenReturn(reader);
            assertTrue(service.getSyllabusById(42L, reader.getId()).isEmpty(), reader.getRole() + " read it");
            ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> service.requireManageable(42L));
            assertEquals(HttpStatus.NOT_FOUND, e.getStatusCode(), "answers 404, does not reveal it exists");
        }

        AppUser student = user(4, "STUDENT", COLLEGE_A, 3L);
        student.setProgramId(7L);
        when(currentUser.getCurrentUser()).thenReturn(student);
        assertTrue(service.getSyllabusById(42L, student.getId()).isEmpty());

        // Control: college B's own colleague can read it.
        AppUser colleague = user(5, "FACULTY", COLLEGE_B, 3L);
        when(currentUser.getCurrentUser()).thenReturn(colleague);
        assertSame(foreign, service.getSyllabusById(42L, colleague.getId()).orElseThrow());
    }

    @Test
    void tc06_jobLookupIsScopedToTheUsersCollegeEvenWithAForgedHeader() {
        JobPostingRepository jobs = mock(JobPostingRepository.class);
        JobPosting collegeBJob = new JobPosting();
        collegeBJob.setInstitutionId(COLLEGE_B);
        // The repository only returns college B's catalog job to a request acting in college B.
        when(jobs.findAccessibleJob(eq(77L), anyLong(), any())).thenAnswer(inv ->
                Objects.equals(inv.getArgument(2), COLLEGE_B) ? Optional.of(collegeBJob) : Optional.empty());
        when(institutions.existsById(COLLEGE_B)).thenReturn(true);
        JobPostingService service = new JobPostingService(jobs, null, null, tenant, currentUser);

        AppUser admin = user(3, "ADMIN", COLLEGE_A, null);
        when(currentUser.getCurrentUser()).thenReturn(admin);
        // A college admin trying to switch college with the super-admin header is ignored.
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(TenantService.HEADER, String.valueOf(COLLEGE_B));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> service.requireAccessibleJob(77L, admin.getId()));
        assertEquals(HttpStatus.NOT_FOUND, e.getStatusCode());
        verify(jobs).findAccessibleJob(77L, admin.getId(), COLLEGE_A);

        assertEquals(COLLEGE_A, tenant.actingInstitutionId(admin));
        // Only a super admin may act in another college.
        assertEquals(COLLEGE_B, tenant.actingInstitutionId(user(9, "SUPER_ADMIN", COLLEGE_A, null)));
    }

    @Test
    void tc06_collegeAAdminCannotChangeCollegeBData() {
        AppUser adminA = user(3, "ADMIN", COLLEGE_A, null);
        assertThrows(ResponseStatusException.class, () -> tenant.requireCollegeAdmin(adminA, COLLEGE_B));
        com.campuspulse.entity.org.Department deptB = new com.campuspulse.entity.org.Department();
        org.springframework.test.util.ReflectionTestUtils.setField(deptB, "id", 30L);
        deptB.setInstitutionId(COLLEGE_B);
        assertThrows(ResponseStatusException.class, () -> tenant.requireDepartmentManager(adminA, deptB));
        assertTrue(!SyllabusAccess.canManage(adminA, collegeBSyllabus()));
    }
}
