package com.meridianmart.integration;

import com.meridianmart.model.Notification;
import com.meridianmart.model.User;
import com.meridianmart.repository.NotificationRepository;
import com.meridianmart.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class NotificationControllerTest extends BaseIntegrationTest {

    @Autowired NotificationRepository notificationRepository;
    @Autowired NotificationService notificationService;

    @BeforeEach
    void clearNotifications() {
        notificationRepository.deleteAll();
    }

    @Test
    void getNotificationsReturns200WithArray() throws Exception {
        User shopper = userRepository.findById(shopperId).orElseThrow();
        notificationRepository.save(Notification.builder().user(shopper).message("Hello").build());

        mockMvc.perform(withShopperAuth(get("/api/notifications")
                        .contentType(MediaType.APPLICATION_JSON)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].message").value("Hello"));
    }

    @Test
    void after5NotificationsServiceEnforcesWith429() {
        for (int i = 0; i < 5; i++) {
            notificationService.createNotification(shopperId, "Notif " + i);
        }

        assertThatThrownBy(() -> notificationService.createNotification(shopperId, "Over cap"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(429));
    }

    @Test
    void markReadAsNonOwnerReturns404() throws Exception {
        // Create a notification belonging to shopper
        User shopper = userRepository.findById(shopperId).orElseThrow();
        Notification n = notificationRepository.save(
                Notification.builder().user(shopper).message("For shopper only").build());

        // Shopper2 attempts to mark shopper's notification as read — must be 404
        mockMvc.perform(withShopperAuth2(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .put("/api/notifications/" + n.getId() + "/read")
                                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)))
                .andExpect(status().isNotFound());
    }

    @Test
    void markReadUnauthenticatedReturns401() throws Exception {
        User shopper = userRepository.findById(shopperId).orElseThrow();
        Notification n = notificationRepository.save(
                Notification.builder().user(shopper).message("Unauth test").build());

        mockMvc.perform(withNoAuthSigning(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .put("/api/notifications/" + n.getId() + "/read")
                                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)))
                .andExpect(status().isUnauthorized());
    }
}
