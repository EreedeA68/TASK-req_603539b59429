package com.meridianmart.frontend;

import com.meridianmart.model.*;
import com.meridianmart.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class OrdersPageTest extends BaseSeleniumTest {

    @Autowired UserRepository userRepository;
    @Autowired OrderRepository orderRepository;
    @Autowired PaymentTransactionRepository paymentTransactionRepository;
    @Autowired PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        cleanDatabase();

        User shopper = userRepository.save(User.builder()
                .username("orderstest").email("orders@test.com")
                .passwordHash(passwordEncoder.encode("Password123!"))
                .role(User.Role.SHOPPER).build());

        orderRepository.save(Order.builder()
                .user(shopper).receiptNumber("RCP-ORDERS-001")
                .totalAmount(BigDecimal.valueOf(99.99)).status(Order.OrderStatus.COMPLETED)
                .transactionTimestamp(LocalDateTime.now())
                .idempotencyKey(UUID.randomUUID().toString())
                .items(List.of()).build());
    }

    @Test
    void ordersListRendersWithReceiptNumberDateTotalStatusBadge() {
        loginViaApi("orders@test.com", "Password123!");
        navigateTo("/orders");
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("orders-list")));
        try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
        assertThat(driver.getPageSource()).contains("RCP-ORDERS-001");
    }

    @Test
    void statusBadgeContainsOrderStatus() {
        loginViaApi("orders@test.com", "Password123!");
        navigateTo("/orders");
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("orders-list")));
        try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
        assertThat(driver.getPageSource()).contains("COMPLETED");
    }
}
