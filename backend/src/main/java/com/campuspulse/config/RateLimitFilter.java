package com.campuspulse.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Simple per-IP, one-minute window limits (change 047): sign-in/register (guessing passwords)
 * and AI-heavy calls (Groq quota, CPU). In memory, so limits are per backend process —
 * enough for one college server; put a proxy limit in front if this ever runs on several nodes.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RateLimitFilter extends OncePerRequestFilter {

    static final long WINDOW_MS = 60_000;
    private static final int MAX_KEYS = 10_000;

    private final int authPerMinute;
    private final int aiPerMinute;
    private final LongSupplier clock;
    private final Map<String, long[]> windows = new ConcurrentHashMap<>();

    @org.springframework.beans.factory.annotation.Autowired
    public RateLimitFilter(@Value("${app.rate-limit.auth-per-minute:10}") int authPerMinute,
                           @Value("${app.rate-limit.ai-per-minute:30}") int aiPerMinute) {
        this(authPerMinute, aiPerMinute, System::currentTimeMillis);
    }

    RateLimitFilter(int authPerMinute, int aiPerMinute, LongSupplier clock) {
        this.authPerMinute = authPerMinute;
        this.aiPerMinute = aiPerMinute;
        this.clock = clock;
    }

    /** Which limit applies, or null for none. */
    static String bucket(String method, String path, String refresh) {
        if (!"POST".equals(method) && !"GET".equals(method)) return null;
        if ("POST".equals(method) && (path.equals("/api/auth/login") || path.equals("/api/auth/register"))) return "auth";
        if ("POST".equals(method) && (path.startsWith("/api/ai/") || path.equals("/api/syllabus/upload")
                || path.equals("/api/jobs/custom") || path.startsWith("/api/resume"))) return "ai";
        if ("GET".equals(method) && "true".equals(refresh)
                && (path.startsWith("/api/matching/") || path.startsWith("/api/recommendations/"))) return "ai";
        return null;
    }

    /** True if this request is within the limit (and counts it). */
    boolean allow(String key, int limit) {
        if (limit <= 0) return true;
        long now = clock.getAsLong();
        if (windows.size() > MAX_KEYS) windows.entrySet().removeIf(e -> now - e.getValue()[0] >= WINDOW_MS);
        long[] window = windows.compute(key, (k, w) -> {
            if (w == null || now - w[0] >= WINDOW_MS) return new long[] {now, 1};
            w[1]++;
            return w;
        });
        return window[1] <= limit;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String bucket = bucket(request.getMethod(), request.getRequestURI(), request.getParameter("refresh"));
        if (bucket != null) {
            int limit = "auth".equals(bucket) ? authPerMinute : aiPerMinute;
            if (!allow(bucket + "|" + request.getRemoteAddr(), limit)) {
                response.setStatus(429);
                response.setHeader("Retry-After", "60");
                response.setContentType("application/json");
                response.getWriter().write("{\"message\":\"Too many requests. Wait a minute and try again.\"}");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
