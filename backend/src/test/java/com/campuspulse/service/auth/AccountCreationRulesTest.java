package com.campuspulse.service.auth;

import com.campuspulse.service.mail.AccountEmailService;
import com.campuspulse.service.org.StudentImportService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/** Default first password, the 100-account import cap, and email only when SMTP is set up. */
class AccountCreationRulesTest {

    @Test
    void defaultPasswordIsUsedWhenSet() {
        NewAccountPasswords passwords = new NewAccountPasswords("Welcome@2026");
        assertTrue(passwords.usesDefault());
        assertEquals("Welcome@2026", passwords.next());
    }

    @Test
    void shortOrEmptyDefaultFallsBackToRandomPasswords() {
        for (String value : new String[] {"", "short"}) {
            NewAccountPasswords passwords = new NewAccountPasswords(value);
            assertFalse(passwords.usesDefault());
            assertNotEquals(passwords.next(), passwords.next());
        }
    }

    @Test
    void importIsCappedAtOneHundredPeople() {
        assertEquals(100, StudentImportService.MAX_ROWS);
    }

    @Test
    @SuppressWarnings("unchecked")
    void noMailServerMeansNoEmail() {
        ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        AccountEmailService mail = new AccountEmailService(provider, "", "http://localhost:5173", "");
        assertFalse(mail.enabled());
        assertFalse(mail.sendWelcome("asha@example.edu", "Asha Rao", "Student", null, "Welcome@2026"));
        verifyNoInteractions(provider);
        assertTrue(mail.sendTestNow("admin@example.edu").startsWith("Email is not set up"));
    }
}
