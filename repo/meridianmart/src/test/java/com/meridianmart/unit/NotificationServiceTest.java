package com.meridianmart.unit;

import com.meridianmart.dto.NotificationDto;
import com.meridianmart.model.Notification;
import com.meridianmart.model.User;
import com.meridianmart.repository.NotificationRepository;
import com.meridianmart.repository.UserRepository;
import com.meridianmart.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock NotificationRepository notificationRepository;
    @Mock UserRepository userRepository;
    @InjectMocks NotificationService notificationService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(notificationService, "maxPerDay", 5);
    }

    private User buildUser() {
        return User.builder().id(1L).username("shopper").role(User.Role.SHOPPER).build();
    }

    @Test
    void notificationCreatedAndStored() {
        User user = buildUser();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(notificationRepository.countTodayByUserId(eq(1L), any())).thenReturn(0L);
        when(notificationRepository.save(any())).thenAnswer(inv -> {
            Notification n = inv.getArgument(0);
            n.setId(1L);
            return n;
        });

        NotificationDto result = notificationService.createNotification(1L, "Test message");

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getMessage()).isEqualTo("Test message");
        assertThat(result.isRead()).isFalse();
    }

    @Test
    void sixthNotificationInDayIsRejected() {
        User user = buildUser();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(notificationRepository.countTodayByUserId(eq(1L), any())).thenReturn(5L);

        assertThatThrownBy(() -> notificationService.createNotification(1L, "6th message"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode")
                .satisfies(code -> assertThat(code.value()).isEqualTo(429));
    }

    @Test
    void notificationMarkedAsReadCorrectly() {
        User user = buildUser();
        Notification notification = Notification.builder()
                .id(1L).user(user).message("Test").read(false).build();

        when(notificationRepository.existsByUserIdAndId(1L, 1L)).thenReturn(true);
        when(notificationRepository.findById(1L)).thenReturn(Optional.of(notification));
        when(notificationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        NotificationDto result = notificationService.markAsRead(1L, 1L);
        assertThat(result.isRead()).isTrue();
    }

    @Test
    void fifthNotificationIsAllowed() {
        User user = buildUser();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(notificationRepository.countTodayByUserId(eq(1L), any())).thenReturn(4L);
        when(notificationRepository.save(any())).thenAnswer(inv -> {
            Notification n = inv.getArgument(0);
            n.setId(5L);
            return n;
        });

        NotificationDto result = notificationService.createNotification(1L, "5th notification");
        assertThat(result).isNotNull();
    }
}
