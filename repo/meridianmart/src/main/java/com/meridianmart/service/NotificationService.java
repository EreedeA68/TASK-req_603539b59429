package com.meridianmart.service;

import com.meridianmart.dto.NotificationDto;
import com.meridianmart.model.Notification;
import com.meridianmart.model.User;
import com.meridianmart.repository.NotificationRepository;
import com.meridianmart.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    @Value("${app.notifications.max-per-day:5}")
    private int maxPerDay;

    @Transactional
    public NotificationDto createNotification(Long userId, String message) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        LocalDateTime startOfDay = LocalDateTime.now().toLocalDate().atStartOfDay();
        long todayCount = notificationRepository.countTodayByUserId(userId, startOfDay);

        if (todayCount >= maxPerDay) {
            log.warn("Notification cap reached for user {}: {} today", userId, todayCount);
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Notification limit of " + maxPerDay + " per day reached for user");
        }

        Notification notification = Notification.builder()
                .user(user)
                .message(message)
                .read(false)
                .build();
        notification = notificationRepository.save(notification);
        return toDto(notification);
    }

    @Transactional(readOnly = true)
    public List<NotificationDto> getNotifications(Long userId) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public NotificationDto markAsRead(Long userId, Long notificationId) {
        if (!notificationRepository.existsByUserIdAndId(userId, notificationId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found");
        }
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));
        notification.setRead(true);
        notification = notificationRepository.save(notification);
        return toDto(notification);
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(Long userId) {
        return notificationRepository.countByUserIdAndReadFalse(userId);
    }

    private NotificationDto toDto(Notification n) {
        return NotificationDto.builder()
                .id(n.getId())
                .message(n.getMessage())
                .read(n.isRead())
                .createdAt(n.getCreatedAt() != null ? n.getCreatedAt().toString() : null)
                .build();
    }
}
