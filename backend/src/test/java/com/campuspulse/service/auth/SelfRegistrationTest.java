package com.campuspulse.service.auth;

import com.campuspulse.config.AdminBootstrap;
import com.campuspulse.entity.org.Institution;
import com.campuspulse.entity.user.AppUser;
import com.campuspulse.repository.org.InstitutionRepository;
import com.campuspulse.repository.user.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Sign-up is closed by default: HOD / college admin create accounts; the platform admin is created at startup. */
class SelfRegistrationTest {

    private final AppUserRepository users = mock(AppUserRepository.class);
    private final InstitutionRepository institutions = mock(InstitutionRepository.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final AdminBootstrap bootstrap = mock(AdminBootstrap.class);

    private AuthService service(boolean open) {
        when(encoder.encode(anyString())).thenReturn("hash");
        Institution college = mock(Institution.class);
        when(college.getId()).thenReturn(1L);
        when(institutions.findByCodeIgnoreCase(anyString())).thenReturn(Optional.of(college));
        when(users.save(any(AppUser.class))).thenAnswer(call -> call.getArgument(0));
        return new AuthService(users, encoder, mock(JwtService.class), bootstrap, institutions, open);
    }

    @Test
    void closedSignUpRejectsStudents() {
        AuthService auth = service(false);
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> auth.register("Asha", "asha@college.edu", "password1", "STUDENT", ""));
        assertEquals(AuthService.REGISTRATION_CLOSED, error.getMessage());
        verify(users, never()).save(any());
    }

    @Test
    void closedSignUpRefusesEvenAdminEmails() {
        when(bootstrap.isAdminEmail("owner@college.edu")).thenReturn(true);
        AuthService auth = service(false);
        assertThrows(IllegalArgumentException.class,
                () -> auth.register("Owner", "owner@college.edu", "password1", "FACULTY", ""));
    }

    @Test
    void openSignUpAcceptsStudents() {
        AppUser user = service(true).register("Asha", "asha@college.edu", "password1", "STUDENT", "");
        assertEquals("STUDENT", user.getRole());
    }
}
