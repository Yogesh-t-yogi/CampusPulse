package com.campuspulse.service.job;

import com.campuspulse.entity.job.JobPosting;
import com.campuspulse.entity.user.AppUser;
import com.campuspulse.repository.job.JobPostingRepository;
import com.campuspulse.repository.job.JobRequiredSkillRepository;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JobPostingServiceAccessTest {

    private final JobPostingRepository jobs = mock(JobPostingRepository.class);
    private final JobPostingService service = new JobPostingService(
            jobs, mock(JobRequiredSkillRepository.class), new SkillAliasService());

    private AppUser user(long id, String role) {
        AppUser user = new AppUser();
        user.setId(id);
        user.setRole(role);
        return user;
    }

    private void givenJob(AppUser owner) {
        JobPosting job = new JobPosting();
        job.setOwner(owner);
        when(jobs.findAccessibleJob(anyLong(), anyLong())).thenReturn(Optional.of(job));
    }

    @Test
    void regularUserCannotChangeCatalogJob() {
        givenJob(null);
        assertThrows(ResponseStatusException.class,
                () -> service.requireModifiableJob(1L, user(5, "USER")));
    }

    @Test
    void adminCanChangeCatalogJob() {
        givenJob(null);
        assertDoesNotThrow(() -> service.requireModifiableJob(1L, user(5, "ADMIN")));
    }

    @Test
    void ownerCanChangeOwnJob() {
        AppUser owner = user(5, "USER");
        givenJob(owner);
        assertDoesNotThrow(() -> service.requireModifiableJob(1L, owner));
    }

    @Test
    void otherUserCannotChangeSomeoneElsesJob() {
        givenJob(user(7, "USER"));
        assertThrows(ResponseStatusException.class,
                () -> service.requireModifiableJob(1L, user(5, "USER")));
    }
}
