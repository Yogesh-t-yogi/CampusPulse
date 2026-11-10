package com.campuspulse.config;

import org.junit.jupiter.api.Test;

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
    }
}
