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

class StaffDashboardTest extends BaseSeleniumTest {

    @Autowired UserRepository userRepository;
    @Autowired OrderRepository orderRepository;
    @Autowired PaymentTransactionRepository paymentTransactionRepository;
    @Autowired PasswordEncoder passwordEncoder;

    private String receiptNumber;

    @BeforeEach
    void setUp() {
        paymentTransactionRepository.deleteAll();
        orderRepository.deleteAll();
        userRepository.deleteAll();

        User shopper = userRepository.save(User.builder()
                .username("shopper4staff").email("shopper4staff@test.com")
                .passwordHash(passwordEncoder.encode("Pass123!"))
                .role(User.Role.SHOPPER).build());

        userRepository.save(User.builder()
                .username("staffuser").email("staff@test.com")
                .passwordHash(passwordEncoder.encode("Staff123!"))
                .role(User.Role.STAFF).build());

        receiptNumber = "RCP-STAFF-UI-001";
        Order order = orderRepository.save(Order.builder()
                .user(shopper).receiptNumber(receiptNumber)
                .totalAmount(BigDecimal.valueOf(100)).status(Order.OrderStatus.COMPLETED)
                .transactionTimestamp(LocalDateTime.now())
                .idempotencyKey(UUID.randomUUID().toString())
                .items(List.of()).build());

        paymentTransactionRepository.save(PaymentTransaction.builder()
                .order(order).amount(BigDecimal.valueOf(100))
                .status(PaymentTransaction.PaymentStatus.SUCCESS)
                .idempotencyKey(UUID.randomUUID().toString()).build());
    }

    @Test
    void pageAccessibleToStaff() {
        loginViaApi("staff@test.com", "Staff123!");
        navigateTo("/staff/dashboard");
        assertThat(driver.getCurrentUrl()).contains("/staff/dashboard");
        assertThat(isElementPresent(By.id("staff-dashboard"))).isTrue();
    }

    @Test
    void pageNotAccessibleToShopper() {
        loginViaApi("shopper4staff@test.com", "Pass123!");
        navigateTo("/staff/dashboard");
        // JS will redirect away (role check in JS)
        try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
        assertThat(driver.getCurrentUrl()).doesNotContain("/staff/dashboard");
    }

    @Test
    void receiptLookupFormRenders() {
        loginViaApi("staff@test.com", "Staff123!");
        navigateTo("/staff/dashboard");
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("receipt-input")));
        assertThat(isElementPresent(By.id("receipt-input"))).isTrue();
        assertThat(isElementPresent(By.id("receipt-search-btn"))).isTrue();
    }

    @Test
    void validReceiptShowsTransactionDetail() {
        loginViaApi("staff@test.com", "Staff123!");
        navigateTo("/staff/dashboard");
        wait.until(ExpectedConditions.elementToBeClickable(By.id("receipt-input")));
        driver.findElement(By.id("receipt-input")).sendKeys(receiptNumber);
        driver.findElement(By.id("receipt-search-btn")).click();
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("transaction-panel")));
        assertThat(driver.findElement(By.id("transaction-panel")).isDisplayed()).isTrue();
        assertThat(driver.findElement(By.id("tx-receipt")).getText()).isEqualTo(receiptNumber);
    }

    @Test
    void refundAndPickupButtonsPresent() {
        loginViaApi("staff@test.com", "Staff123!");
        navigateTo("/staff/dashboard");
        wait.until(ExpectedConditions.elementToBeClickable(By.id("receipt-input")));
        driver.findElement(By.id("receipt-input")).sendKeys(receiptNumber);
        driver.findElement(By.id("receipt-search-btn")).click();
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("transaction-panel")));

        assertThat(isElementPresent(By.id("refund-btn"))).isTrue();
        assertThat(isElementPresent(By.id("pickup-btn"))).isTrue();
    }
}
