package com.learncreator.auth.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final long WINDOW_MILLIS = 60_000;
    private static final int MAX_TRACKED_KEYS = 10_000;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Limit limit = limitFor(request);
        if (limit == null) {
            chain.doFilter(request, response);
            return;
        }

        long now = Instant.now().toEpochMilli();
        String key = request.getRemoteAddr() + ':' + limit.bucket;
        Window window = acquireWindow(key, now);
        if (window == null) {
            reject(response);
            return;
        }

        if (window.count > limit.requests) {
            reject(response);
            return;
        }
        chain.doFilter(request, response);
    }

    private synchronized Window acquireWindow(String key, long now) {
        if (windows.size() >= MAX_TRACKED_KEYS) {
            windows.entrySet().removeIf(entry -> now - entry.getValue().startedAt >= WINDOW_MILLIS);
        }
        if (!windows.containsKey(key) && windows.size() >= MAX_TRACKED_KEYS) {
            return null;
        }
        return windows.compute(key, (ignored, current) ->
                current == null || now - current.startedAt >= WINDOW_MILLIS
                        ? new Window(now, 1)
                        : new Window(current.startedAt, current.count + 1));
    }

    private Limit limitFor(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path.startsWith("/api/auth/")) return new Limit("auth", 30);
        if (path.matches("/api/ai-tutor/lessons/[^/]+/transcript/generate")
                || path.equals("/api/media/upload-url")) return new Limit("expensive-job", 10);
        if (path.startsWith("/api/ai-tutor/") && "POST".equals(request.getMethod())) {
            return new Limit("ai-question", 20);
        }
        return null;
    }

    private void reject(HttpServletResponse response) throws IOException {
        response.setStatus(429);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"status\":429,\"message\":\"Too many requests\"}");
    }

    private record Limit(String bucket, int requests) {}
    private record Window(long startedAt, int count) {}
}
