package com.campuspulse.config;

import com.campuspulse.support.AppDefaults;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RateLimitFilterTest {

    @Test
    void blocksAfterLimitAndResetsAfterAMinute() {
        AtomicLong now = new AtomicLong(1_000);
        RateLimitFilter filter = new RateLimitFilter(3, 5, now::get);
        for (int i = 0; i < 3; i++) assertTrue(filter.allow("auth|1.2.3.4", 3));
        assertFalse(filter.allow("auth|1.2.3.4", 3));
        assertTrue(filter.allow("auth|5.6.7.8", 3), "other IPs are separate");
        now.addAndGet(RateLimitFilter.WINDOW_MS);
        assertTrue(filter.allow("auth|1.2.3.4", 3));
    }

    @Test
    void zeroMeansOff() {
        RateLimitFilter filter = new RateLimitFilter(0, 0, () -> 0);
        for (int i = 0; i < 100; i++) assertTrue(filter.allow("auth|x", 0));
    }

    @Test
    void bucketsCoverSignInAndAiCallsOnly() {
        assertEquals("auth", RateLimitFilter.bucket("POST", "/api/auth/login", null));
        assertEquals("ai", RateLimitFilter.bucket("POST", "/api/ai/jobs/3/extract-skills", null));
        assertEquals("ai", RateLimitFilter.bucket("POST", "/api/syllabus/upload", null));
        assertEquals("ai", RateLimitFilter.bucket("GET", "/api/matching/semantic/report/jobs/1/syllabus/2", "true"));
        assertNull(RateLimitFilter.bucket("GET", "/api/matching/semantic/report/jobs/1/syllabus/2", null));
        assertNull(RateLimitFilter.bucket("GET", "/api/jobs", null));
        assertNull(RateLimitFilter.bucket("OPTIONS", "/api/auth/login", null));
        // Password guessing and course-outcome AI extraction are limited too.
        assertEquals("auth", RateLimitFilter.bucket("POST", "/api/auth/change-password", null));
        assertEquals("ai", RateLimitFilter.bucket("POST", "/api/outcomes/syllabus/4/extract", null));
    }

    private static MockHttpServletResponse login(RateLimitFilter filter, String ip) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setRemoteAddr(ip);
        request.setContentType("application/json");
        request.setContent("{\"email\":\"a@b.c\",\"password\":\"wrong\"}".getBytes());
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    /** TC-01: the 11th sign-in within a minute from one client gets 429 with Retry-After: 60. */
    @Test
    void tc01_eleventhSignInWithinAMinuteIsRejectedWith429() throws Exception {
        int limit = AppDefaults.getInt("app.rate-limit.auth-per-minute");
        assertEquals(10, limit, "configured sign-in limit (application.properties)");
        AtomicLong now = new AtomicLong(5_000);
        RateLimitFilter filter = new RateLimitFilter(limit,
                AppDefaults.getInt("app.rate-limit.ai-per-minute"), now::get);

        for (int attempt = 1; attempt <= limit; attempt++) {
            MockHttpServletResponse ok = login(filter, "10.0.0.7");
            assertEquals(200, ok.getStatus(), "attempt " + attempt + " reaches the login controller");
            now.addAndGet(1_000); // all 11 attempts stay inside one minute
        }
        MockHttpServletResponse blocked = login(filter, "10.0.0.7");
        assertEquals(429, blocked.getStatus());
        assertEquals("60", blocked.getHeader("Retry-After"));
        assertTrue(blocked.getContentAsString().contains("Too many requests"), blocked.getContentAsString());

        assertEquals(200, login(filter, "10.0.0.8").getStatus(), "another client is not affected");
        now.addAndGet(RateLimitFilter.WINDOW_MS);
        assertEquals(200, login(filter, "10.0.0.7").getStatus(), "allowed again after the minute");
    }

    @Test
    void blockedRequestNeverReachesTheController() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(1, 1, () -> 0);
        login(filter, "1.1.1.1");
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setRemoteAddr("1.1.1.1");
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, new MockHttpServletResponse(), chain);
        assertNull(chain.getRequest(), "chain not called once the limit is hit");
    }
}
