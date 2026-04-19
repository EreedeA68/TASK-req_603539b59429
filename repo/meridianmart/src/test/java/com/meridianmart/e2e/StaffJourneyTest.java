package com.meridianmart.e2e;

import com.meridianmart.frontend.BaseSeleniumTest;
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

class StaffJourneyTest extends BaseSeleniumTest {

    @Autowired UserRepository userRepository;
    @Autowired OrderRepository orderRepository;
    @Autowired PaymentTransactionRepository paymentTransactionRepository;
    @Autowired PasswordEncoder passwordEncoder;

    private String receiptNumber;

    @BeforeEach
    void setUp() {
        cleanDatabase();

        User shopper = userRepository.save(User.builder()
                .username("staffjourney_shopper").email("staffshopper@test.com")
                .passwordHash(passwordEncoder.encode("Pass123!"))
                .role(User.Role.SHOPPER).build());

        userRepository.save(User.builder()
                .username("staffjourney_staff").email("staffstaff@test.com")
                .passwordHash(passwordEncoder.encode("Staff123!"))
                .role(User.Role.STAFF).build());

        receiptNumber = "RCP-STAFFJOURNEY-001";
        Order order = orderRepository.save(Order.builder()
                .user(shopper).receiptNumber(receiptNumber)
                .totalAmount(BigDecimal.valueOf(200)).status(Order.OrderStatus.COMPLETED)
                .transactionTimestamp(LocalDateTime.now())
                .idempotencyKey(UUID.randomUUID().toString())
                .items(List.of()).build());

        paymentTransactionRepository.save(PaymentTransaction.builder()
                .order(order).amount(BigDecimal.valueOf(200))
                .status(PaymentTransaction.PaymentStatus.SUCCESS)
                .idempotencyKey(UUID.randomUUID().toString()).build());
    }

    @Test
    void staffRefundJourney() {
        // Step 1: Login as staff
        loginViaApi("staffstaff@test.com", "Staff123!");

        // Step 2: Open staff dashboard
        navigateTo("/staff/dashboard");
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("staff-dashboard")));

        // Step 3: Enter receipt number and search
        wait.until(ExpectedConditions.elementToBeClickable(By.id("receipt-input")));
        driver.findElement(By.id("receipt-input")).sendKeys(receiptNumber);
        driver.findElement(By.id("receipt-search-btn")).click();

        // Step 4: Verify transaction details load
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("transaction-panel")));
        assertThat(driver.findElement(By.id("tx-receipt")).getText()).isEqualTo(receiptNumber);

        // Step 5: Click refund
        driver.findElement(By.id("refund-btn")).click();

        // Step 6: Verify order status shows REFUNDED
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("staff-alerts")));
        try { Thread.sleep(2000); } catch (InterruptedException ignored) {}

        // Re-search to check status
        driver.findElement(By.id("receipt-search-btn")).click();
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("transaction-panel")));
        String status = driver.findElement(By.id("tx-status")).getText();
        assertThat(status).isEqualTo("REFUNDED");
    }
}
