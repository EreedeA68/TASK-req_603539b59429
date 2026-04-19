package com.meridianmart.security;

import com.meridianmart.model.NonceEntry;
import com.meridianmart.repository.NonceRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;

@Slf4j
@Component
@RequiredArgsConstructor
public class RequestSigningFilter extends OncePerRequestFilter {

    private final NonceRepository nonceRepository;

    @Value("${app.request-signing.max-age-seconds:300}")
    private long maxAgeSeconds;

    @Value("${app.request-signing.secret:${app.jwt.secret}}")
    private String signingSecret;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Only sign /api/ endpoints; all page routes and actuator paths are excluded.
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String timestampHeader = request.getHeader("X-Timestamp");
        String nonceHeader = request.getHeader("X-Nonce");

        if (!StringUtils.hasText(timestampHeader) || !StringUtils.hasText(nonceHeader)) {
            writeError(response, HttpStatus.BAD_REQUEST, "Missing X-Timestamp or X-Nonce header");
            return;
        }

        long requestTimestamp;
        try {
            requestTimestamp = Long.parseLong(timestampHeader);
        } catch (NumberFormatException e) {
            writeError(response, HttpStatus.BAD_REQUEST, "Invalid X-Timestamp format");
            return;
        }

        long now = Instant.now().getEpochSecond();
        if (Math.abs(now - requestTimestamp) > maxAgeSeconds) {
            writeError(response, HttpStatus.BAD_REQUEST, "Request timestamp is too old or in future. Replay attack rejected.");
            return;
        }

        String signatureHeader = request.getHeader("X-Signature");
        if (!StringUtils.hasText(signatureHeader)) {
            writeError(response, HttpStatus.BAD_REQUEST, "Missing X-Signature header");
            return;
        }

        // Read body once; wrap request so downstream can re-read it.
        byte[] bodyBytes = request.getInputStream().readAllBytes();
        HttpServletRequest reReadable = new HttpServletRequestWrapper(request) {
            @Override
            public ServletInputStream getInputStream() {
                ByteArrayInputStream bais = new ByteArrayInputStream(bodyBytes);
                return new ServletInputStream() {
                    public int read() { return bais.read(); }
                    public boolean isFinished() { return bais.available() == 0; }
                    public boolean isReady() { return true; }
                    public void setReadListener(ReadListener l) {}
                };
            }
            @Override
            public BufferedReader getReader() {
                return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
            }
        };

        // All authenticated requests are signed with the server-held signing secret.
        // Unauthenticated requests (e.g. login bootstrap) use an empty-string key;
        // timestamp + nonce replay guards still apply in both cases.
        String authHeader = request.getHeader("Authorization");
        String effectiveKey = (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer "))
                ? signingSecret
                : "";

        String queryString = request.getQueryString();
        String fullPath = request.getRequestURI()
                + (queryString != null && !queryString.isEmpty() ? "?" + queryString : "");
        String bodyHash = computeSha256Hex(bodyBytes);
        String canonical = request.getMethod() + "\n" + fullPath
                + "\n" + timestampHeader + "\n" + nonceHeader + "\n" + bodyHash;
        String expectedSignature = computeHmac(canonical, effectiveKey);
        if (!expectedSignature.equals(signatureHeader)) {
            writeError(response, HttpStatus.UNAUTHORIZED, "Invalid request signature");
            return;
        }

        LocalDateTime windowStart = LocalDateTime.ofInstant(
                Instant.now().minusSeconds(maxAgeSeconds), ZoneOffset.UTC);
        if (nonceRepository.existsByNonceValueAndCreatedAtAfter(nonceHeader, windowStart)) {
            writeError(response, HttpStatus.BAD_REQUEST, "Nonce already used. Replay attack rejected.");
            return;
        }

        NonceEntry entry = new NonceEntry();
        entry.setNonceValue(nonceHeader);
        nonceRepository.save(entry);

        filterChain.doFilter(reReadable, response);
    }

    private String computeSha256Hex(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(data);
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 computation failed", e);
        }
    }

    private String computeHmac(String canonical, String key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            byte[] keyBytes = key.isEmpty() ? new byte[]{0} : key.getBytes(StandardCharsets.UTF_8);
            mac.init(new SecretKeySpec(keyBytes, "HmacSHA256"));
            return Base64.getEncoder().encodeToString(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException("HMAC computation failed", e);
        }
    }

    private void writeError(HttpServletResponse response, HttpStatus status, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(String.format(
                "{\"success\":false,\"errorMessage\":\"%s\"}", message));
    }
}
