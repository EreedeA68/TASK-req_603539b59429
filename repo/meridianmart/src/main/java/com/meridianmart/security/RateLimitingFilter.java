package com.meridianmart.security;

import com.meridianmart.model.User;
import com.meridianmart.service.AppConfigService;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    @Value("${app.rate-limit.requests-per-minute:60}")
    private int defaultRequestsPerMinute;

    @Lazy
    @Autowired
    private AppConfigService appConfigService;

    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> lastAccessMs = new ConcurrentHashMap<>();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String key = resolveKey(request);
        lastAccessMs.put(key, System.currentTimeMillis());
        Bucket bucket = buckets.computeIfAbsent(key, this::createBucket);

        if (bucket.tryConsume(1)) {
            filterChain.doFilter(request, response);
        } else {
            log.warn("Rate limit exceeded for key: {}", key);
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setHeader("Retry-After", "60");
            response.getWriter().write(
                    "{\"success\":false,\"errorMessage\":\"Rate limit exceeded. Try again later.\",\"retryAfter\":60}"
            );
        }
    }

    @Scheduled(fixedRate = 120_000)
    public void evictStaleBuckets() {
        long cutoff = System.currentTimeMillis() - 120_000;
        lastAccessMs.entrySet().removeIf(entry -> {
            if (entry.getValue() < cutoff) {
                buckets.remove(entry.getKey());
                return true;
            }
            return false;
        });
    }

    private String resolveKey(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof User user) {
            return "user:" + user.getId();
        }
        return "ip:" + request.getRemoteAddr();
    }

    private Bucket createBucket(String key) {
        int rpm = appConfigService.getIntConfig("rate-limit.requests-per-minute", defaultRequestsPerMinute);
        Bandwidth limit = Bandwidth.classic(rpm, Refill.intervally(rpm, Duration.ofMinutes(1)));
        return Bucket.builder().addLimit(limit).build();
    }
}
