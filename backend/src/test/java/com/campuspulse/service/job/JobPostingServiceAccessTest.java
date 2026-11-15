package com.campuspulse.service.job;

import com.campuspulse.entity.job.JobPosting;
import com.campuspulse.entity.user.AppUser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Who may change a job (JobPostingService.canModify). */
class JobPostingServiceAccessTest {

    private static AppUser user(long id, String role, Long college) {
        AppUser user = new AppUser();
        user.setId(id);
        user.setRole(role);
        user.setInstitutionId(college);
        return user;
    }

    private static JobPosting job(AppUser owner, Long college) {
        JobPosting job = new JobPosting();
        job.setOwner(owner);
        job.setInstitutionId(college);
        return job;
    }

    @Test
    void ownerCanChangeOwnPrivateJob() {
        AppUser owner = user(5, "FACULTY", 1L);
        assertTrue(JobPostingService.canModify(job(owner, null), owner));
    }

    @Test
    void othersCannotChangeSomeoneElsesPrivateJob() {
        AppUser owner = user(7, "FACULTY", 1L);
        assertFalse(JobPostingService.canModify(job(owner, null), user(5, "ADMIN", 1L)));
    }

    @Test
    void globalCatalogOnlyForPlatformAdmin() {
        JobPosting global = job(null, null);
        assertTrue(JobPostingService.canModify(global, user(1, "SUPER_ADMIN", 1L)));
        assertFalse(JobPostingService.canModify(global, user(2, "ADMIN", 1L)));
        assertFalse(JobPostingService.canModify(global, user(3, "FACULTY", 1L)));
    }

    @Test
    void collegeCatalogForThatCollegesAdmin() {
        JobPosting collegeJob = job(null, 1L);
        assertTrue(JobPostingService.canModify(collegeJob, user(2, "ADMIN", 1L)));
        assertFalse(JobPostingService.canModify(collegeJob, user(3, "ADMIN", 2L)));
        assertFalse(JobPostingService.canModify(collegeJob, user(4, "HOD", 1L)));
        assertTrue(JobPostingService.canModify(collegeJob, user(1, "SUPER_ADMIN", 9L)));
    }
}
