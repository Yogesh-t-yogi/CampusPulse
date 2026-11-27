package com.campuspulse.service.mail;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Roles appear in plain words in the welcome email. */
class WelcomeEmailTest {

    @Test
    void rolesAreShownInPlainWords() {
        assertEquals("College admin", AccountEmailService.roleLabel("ADMIN"));
        assertEquals("Head of department", AccountEmailService.roleLabel("HOD"));
        assertEquals("Student", AccountEmailService.roleLabel("STUDENT"));
        assertEquals("CampusPulse user", AccountEmailService.roleLabel(null));
    }
}
