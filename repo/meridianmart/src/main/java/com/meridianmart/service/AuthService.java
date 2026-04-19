package com.meridianmart.service;

import com.meridianmart.audit.AuditService;
import com.meridianmart.dto.LoginRequest;
import com.meridianmart.dto.LoginResponse;
import com.meridianmart.dto.UserProfileDto;
import com.meridianmart.model.User;
import com.meridianmart.repository.UserRepository;
import com.meridianmart.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final AuditService auditService;

    @Value("${app.brute-force.max-attempts:5}")
    private int maxAttempts;

    @Value("${app.brute-force.lock-duration-minutes:15}")
    private int lockDurationMinutes;

    @Value("${app.request-signing.secret:${app.jwt.secret}}")
    private String signingSecret;

    @Transactional
    public LoginResponse login(LoginRequest request, String ipAddress) {
        String identifier = request.getUsername();
        User user = (identifier.contains("@")
                ? userRepository.findByEmail(identifier)
                : userRepository.findByUsername(identifier))
                .orElseThrow(() -> {
                    auditService.log(null, "LOGIN_FAILURE", "Login failed for identifier (masked)", ipAddress);
                    return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
                });

        if (user.isLocked()) {
            LocalDateTime lockExpiry = user.getLockTime().plusMinutes(lockDurationMinutes);
            if (LocalDateTime.now().isBefore(lockExpiry)) {
                log.warn("Login attempt on locked account: userId={}", user.getId());
                throw new ResponseStatusException(HttpStatus.LOCKED, "Account is locked. Try again after 15 minutes.");
            } else {
                user.setLocked(false);
                user.setFailedAttempts(0);
                user.setLockTime(null);
                userRepository.save(user);
            }
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            int attempts = user.getFailedAttempts() + 1;
            user.setFailedAttempts(attempts);

            if (attempts >= maxAttempts) {
                user.setLocked(true);
                user.setLockTime(LocalDateTime.now());
                userRepository.save(user);
                auditService.log(user.getId(), "ACCOUNT_LOCKED",
                        "Account locked after " + attempts + " failed attempts", ipAddress);
                log.warn("Account locked for user: userId={}", user.getId());
                throw new ResponseStatusException(HttpStatus.LOCKED,
                        "Account locked after 5 failed attempts. Try again in 15 minutes.");
            }

            userRepository.save(user);
            auditService.log(user.getId(), "LOGIN_FAILURE",
                    "Failed attempt " + attempts, ipAddress);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }

        user.setFailedAttempts(0);
        user.setLastActive(LocalDateTime.now());
        userRepository.save(user);

        String token = tokenProvider.generateToken(user.getId(), user.getEmail(), user.getRole().name());
        log.info("Successful login for userId={}", user.getId());

        return LoginResponse.builder()
                .token(token)
                .role(user.getRole().name())
                .userId(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .signingKey(signingSecret)
                .build();
    }

    @Transactional
    public void logout(String token) {
        tokenProvider.invalidateToken(token);
    }

    @Transactional(readOnly = true)
    public UserProfileDto getCurrentUser(User user) {
        return UserProfileDto.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole().name())
                .createdAt(user.getCreatedAt() != null ? user.getCreatedAt().toString() : null)
                .lastActive(user.getLastActive() != null ? user.getLastActive().toString() : null)
                .build();
    }

    @Transactional
    public void updateLastActive(Long userId) {
        userRepository.updateLastActive(userId, LocalDateTime.now());
    }
}
