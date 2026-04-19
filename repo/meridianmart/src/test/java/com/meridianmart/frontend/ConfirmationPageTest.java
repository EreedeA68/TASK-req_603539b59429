package com.meridianmart.frontend;

import com.meridianmart.model.Order;
import com.meridianmart.model.User;
import com.meridianmart.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.*;

class ConfirmationPageTest extends BaseSeleniumTest {

    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        cleanDatabase();
        userRepository.save(User.builder()
                .username("confirmtest").email("confirm@test.com")
                .passwordHash(passwordEncoder.encode("Password123!"))
                .role(User.Role.SHOPPER).build());
    }

    @Test
    void receiptNumberDisplayed() {
        loginViaApi("confirm@test.com", "Password123!");

        // Inject a fake order into localStorage
        ((JavascriptExecutor) driver).executeScript(
                "localStorage.setItem('mm_last_order', JSON.stringify({receiptNumber:'RCP-TEST-001', transactionTimestamp:'3:45 PM'}))");

        navigateTo("/confirmation");
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("receipt-number")));
        String receipt = driver.findElement(By.id("receipt-number")).getText();
        assertThat(receipt).isEqualTo("RCP-TEST-001");
    }

    @Test
    void timestampIn12HourFormat() {
        loginViaApi("confirm@test.com", "Password123!");

        ((JavascriptExecutor) driver).executeScript(
                "localStorage.setItem('mm_last_order', JSON.stringify({receiptNumber:'RCP-TS-002', transactionTimestamp:'11:30 AM'}))");

        navigateTo("/confirmation");
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("receipt-timestamp")));
        String timestamp = driver.findElement(By.id("receipt-timestamp")).getText();
        assertThat(timestamp).matches(".*(?:AM|PM).*");
    }

    @Test
    void printableReceiptElementPresent() {
        loginViaApi("confirm@test.com", "Password123!");
        ((JavascriptExecutor) driver).executeScript(
                "localStorage.setItem('mm_last_order', JSON.stringify({receiptNumber:'RCP-PRINT-003', transactionTimestamp:'5:00 PM'}))");

        navigateTo("/confirmation");
        assertThat(isElementPresent(By.id("printable-receipt"))).isTrue();
    }
}
