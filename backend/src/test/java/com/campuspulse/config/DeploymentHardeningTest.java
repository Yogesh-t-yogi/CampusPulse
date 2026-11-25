package com.campuspulse.config;

import com.campuspulse.controller.admin.AdminUserControllerAccess;
import com.campuspulse.entity.syllabus.Syllabus;
import com.campuspulse.entity.user.AppUser;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Logins end after a password change; deleting a user keeps shared syllabi. */
class DeploymentHardeningTest {

    private static Date at(LocalDateTime time) {
        return Date.from(time.atZone(ZoneId.systemDefault()).toInstant());
    }

    @Test
    void tokenFromBeforeThePasswordChangeIsRefused() {
        AppUser user = new AppUser();
        LocalDateTime changed = LocalDateTime.of(2026, 11, 25, 10, 0, 0);
        user.setPasswordChangedAt(changed);
        assertFalse(JwtAuthenticationFilter.tokenStillValid(user, at(changed.minusMinutes(5))));
        assertTrue(JwtAuthenticationFilter.tokenStillValid(user, at(changed)));
        assertTrue(JwtAuthenticationFilter.tokenStillValid(user, at(changed.plusSeconds(1))));
        assertFalse(JwtAuthenticationFilter.tokenStillValid(user, null));
    }

    @Test
    void accountsThatNeverChangedTheirPasswordKeepWorking() {
        assertTrue(JwtAuthenticationFilter.tokenStillValid(new AppUser(), at(LocalDateTime.now().minusDays(1))));
    }

    @Test
    void onlyPrivateSyllabiAreDeletedWithTheirOwner() {
        Syllabus shared = new Syllabus();
        shared.setVisibility("DEPARTMENT");
        Syllabus mine = new Syllabus();
        mine.setVisibility("PRIVATE");
        assertTrue(AdminUserControllerAccess.isShared(shared));
        assertFalse(AdminUserControllerAccess.isShared(mine));
    }
}
