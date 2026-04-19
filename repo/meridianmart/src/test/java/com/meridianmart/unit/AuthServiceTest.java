package com.meridianmart.unit;

import com.meridianmart.audit.AuditService;
import com.meridianmart.dto.LoginRequest;
import com.meridianmart.dto.LoginResponse;
import com.meridianmart.model.User;
import com.meridianmart.repository.UserRepository;
import com.meridianmart.security.JwtTokenProvider;
import com.meridianmart.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtTokenProvider tokenProvider;
    @Mock AuditService auditService;
    @InjectMocks AuthService authService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authService, "maxAttempts", 5);
        ReflectionTestUtils.setField(authService, "lockDurationMinutes", 15);
    }

    private User buildUser(User.Role role) {
        return User.builder()
                .id(1L)
                .email("test@example.com")
                .username("testuser")
                .passwordHash("$hashed$")
                .role(role)
                .locked(false)
                .failedAttempts(0)
                .build();
    }

    @Test
    void successfulLoginReturnsJwtWithCorrectRole() {
        User user = buildUser(User.Role.SHOPPER);
        LoginRequest req = new LoginRequest();
        req.setUsername("test@example.com");
        req.setPassword("secret");

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret", "$hashed$")).thenReturn(true);
        when(tokenProvider.generateToken(1L, "test@example.com", "SHOPPER")).thenReturn("jwt.token.here");
        when(userRepository.save(any())).thenReturn(user);

        LoginResponse response = authService.login(req, "127.0.0.1");

        assertThat(response.getToken()).isEqualTo("jwt.token.here");
        assertThat(response.getRole()).isEqualTo("SHOPPER");
        assertThat(response.getUserId()).isEqualTo(1L);
    }

    @Test
    void failedLoginIncrementsFailedAttempts() {
        User user = buildUser(User.Role.SHOPPER);
        LoginRequest req = new LoginRequest();
        req.setUsername("test@example.com");
        req.setPassword("wrong");

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "$hashed$")).thenReturn(false);
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThatThrownBy(() -> authService.login(req, "127.0.0.1"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode")
                .satisfies(code -> assertThat(code.value()).isEqualTo(401));

        verify(userRepository).save(argThat(u -> u.getFailedAttempts() == 1));
    }

    @Test
    void fifthFailedLoginLocksAccountAndWritesAuditLog() {
        User user = buildUser(User.Role.SHOPPER);
        user.setFailedAttempts(4);
        LoginRequest req = new LoginRequest();
        req.setUsername("test@example.com");
        req.setPassword("wrong");

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "$hashed$")).thenReturn(false);
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThatThrownBy(() -> authService.login(req, "127.0.0.1"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode")
                .satisfies(code -> assertThat(code.value()).isEqualTo(423));

        verify(userRepository).save(argThat(u -> u.isLocked() && u.getLockTime() != null));
        verify(auditService).log(eq(1L), eq("ACCOUNT_LOCKED"), anyString(), eq("127.0.0.1"));
    }

    @Test
    void lockedAccountRejectsLoginEvenWithCorrectPassword() {
        User user = buildUser(User.Role.SHOPPER);
        user.setLocked(true);
        user.setLockTime(LocalDateTime.now().minusMinutes(5));
        LoginRequest req = new LoginRequest();
        req.setUsername("test@example.com");
        req.setPassword("correct");

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(req, "127.0.0.1"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode")
                .satisfies(code -> assertThat(code.value()).isEqualTo(423));
    }

    @Test
    void accountAutoUnlocksAfterLockDuration() {
        User user = buildUser(User.Role.SHOPPER);
        user.setLocked(true);
        user.setLockTime(LocalDateTime.now().minusMinutes(20));
        LoginRequest req = new LoginRequest();
        req.setUsername("test@example.com");
        req.setPassword("correct");

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("correct", "$hashed$")).thenReturn(true);
        when(tokenProvider.generateToken(anyLong(), anyString(), anyString())).thenReturn("new.token");
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        LoginResponse response = authService.login(req, "127.0.0.1");
        assertThat(response.getToken()).isEqualTo("new.token");
    }

    @Test
    void logoutInvalidatesToken() {
        authService.logout("some.jwt.token");
        verify(tokenProvider).invalidateToken("some.jwt.token");
    }

    @Test
    void loginWithUnknownEmailThrows401() {
        LoginRequest req = new LoginRequest();
        req.setUsername("nobody@example.com");
        req.setPassword("pass");

        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(req, "127.0.0.1"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode")
                .satisfies(code -> assertThat(code.value()).isEqualTo(401));
    }
}
