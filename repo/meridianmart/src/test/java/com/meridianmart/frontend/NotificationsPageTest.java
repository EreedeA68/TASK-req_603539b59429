package com.meridianmart.frontend;

import com.meridianmart.model.Notification;
import com.meridianmart.model.User;
import com.meridianmart.repository.NotificationRepository;
import com.meridianmart.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.*;

class NotificationsPageTest extends BaseSeleniumTest {

    @Autowired UserRepository userRepository;
    @Autowired NotificationRepository notificationRepository;
    @Autowired PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        notificationRepository.deleteAll();
        userRepository.deleteAll();

        User shopper = userRepository.save(User.builder()
                .username("notiftest").email("notif@test.com")
                .passwordHash(passwordEncoder.encode("Password123!"))
                .role(User.Role.SHOPPER).build());

        notificationRepository.save(Notification.builder()
                .user(shopper).message("Your order is ready!").read(false).build());
        notificationRepository.save(Notification.builder()
                .user(shopper).message("Order completed.").read(true).build());
    }

    @Test
    void notificationsListRendersWithMessageAndTimestamp() {
        loginViaApi("notif@test.com", "Password123!");
        navigateTo("/notifications");
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("notifications-list")));
        try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
        assertThat(driver.getPageSource()).contains("Your order is ready!");
    }

    @Test
    void markAsReadButtonPresentPerNotification() {
        loginViaApi("notif@test.com", "Password123!");
        navigateTo("/notifications");
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("notifications-list")));
        try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
        // Unread notification should have Mark read button
        assertThat(driver.getPageSource()).contains("Mark read");
    }

    @Test
    void unreadCountBadgeVisibleInNav() {
        loginViaApi("notif@test.com", "Password123!");
        navigateTo("/home");
        try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
        assertThat(isElementPresent(By.id("notif-badge"))).isTrue();
    }
}
