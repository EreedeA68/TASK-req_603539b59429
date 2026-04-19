package com.meridianmart.controller;

import com.meridianmart.dto.ApiResponse;
import com.meridianmart.dto.NotificationDto;
import com.meridianmart.model.User;
import com.meridianmart.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<NotificationDto>>> getNotifications(@AuthenticationPrincipal User user) {
        List<NotificationDto> notifications = notificationService.getNotifications(user.getId());
        return ResponseEntity.ok(ApiResponse.success(notifications));
    }

    @PutMapping("/{id}/read")
    public ResponseEntity<ApiResponse<NotificationDto>> markAsRead(
            @AuthenticationPrincipal User user,
            @PathVariable Long id) {
        NotificationDto notification = notificationService.markAsRead(user.getId(), id);
        return ResponseEntity.ok(ApiResponse.success(notification));
    }
}
