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
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        int limit = limitFor(request);
        if (limit == 0) {
            chain.doFilter(request, response);
            return;
        }

        long now = Instant.now().toEpochMilli();
        String key = request.getRemoteAddr() + ':' + request.getMethod() + ':' + request.getRequestURI();
        Window window = windows.compute(key, (ignored, current) ->
                current == null || now - current.startedAt >= WINDOW_MILLIS
                        ? new Window(now, 1)
                        : new Window(current.startedAt, current.count + 1));

        if (window.count > limit) {
            response.setStatus(429);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"status\":429,\"message\":\"Too many requests\"}");
            return;
        }
        if (windows.size() > 10_000) {
            windows.entrySet().removeIf(entry -> now - entry.getValue().startedAt >= WINDOW_MILLIS);
        }
        chain.doFilter(request, response);
    }

    private int limitFor(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path.startsWith("/api/auth/")) return 30;
        if (path.contains("/transcript/generate") || path.equals("/api/media/upload-url")) return 10;
        if (path.startsWith("/api/ai-tutor/") && "POST".equals(request.getMethod())) return 20;
        return 0;
    }

    private record Window(long startedAt, int count) {}
}
