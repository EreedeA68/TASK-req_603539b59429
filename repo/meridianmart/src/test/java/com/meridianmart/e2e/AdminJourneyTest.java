package com.meridianmart.e2e;

import com.meridianmart.frontend.BaseSeleniumTest;
import com.meridianmart.model.FeatureFlag;
import com.meridianmart.model.User;
import com.meridianmart.repository.FeatureFlagRepository;
import com.meridianmart.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.*;

class AdminJourneyTest extends BaseSeleniumTest {

    @Autowired UserRepository userRepository;
    @Autowired FeatureFlagRepository featureFlagRepository;
    @Autowired PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        cleanDatabase();

        userRepository.save(User.builder()
                .username("adminjourney").email("adminjourney@test.com")
                .passwordHash(passwordEncoder.encode("Admin123!"))
                .role(User.Role.ADMIN).build());

        featureFlagRepository.save(FeatureFlag.builder()
                .flagName("JOURNEY_TEST_FLAG").enabled(true).storeId("STORE_001").updatedBy("admin").build());
    }

    @Test
    void adminFeatureFlagToggleJourney() {
        // Step 1: Login as admin
        loginViaApi("adminjourney@test.com", "Admin123!");

        // Step 2: Open admin dashboard
        navigateTo("/admin/dashboard");
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("admin-dashboard")));
        try { Thread.sleep(3000); } catch (InterruptedException ignored) {}

        // Step 3: Verify flags list renders
        assertThat(driver.getPageSource()).contains("JOURNEY_TEST_FLAG");

        // Step 4: Find the toggle and turn it off (checkbox is CSS-hidden; use JS click)
        WebElement toggle = wait.until(ExpectedConditions.presenceOfElementLocated(
                By.cssSelector("#flags-list input[type='checkbox']")));
        boolean initiallyChecked = toggle.isSelected();
        ((JavascriptExecutor) driver).executeScript("arguments[0].click();", toggle);

        try { Thread.sleep(2000); } catch (InterruptedException ignored) {}

        // Step 5: Verify state changed - reload flags
        navigateTo("/admin/dashboard");
        try { Thread.sleep(3000); } catch (InterruptedException ignored) {}

        WebElement updatedToggle = wait.until(ExpectedConditions.presenceOfElementLocated(
                By.cssSelector("#flags-list input[type='checkbox']")));
        assertThat(updatedToggle.isSelected()).isNotEqualTo(initiallyChecked);

        // Step 6: Toggle back on (CSS-hidden; use JS click)
        ((JavascriptExecutor) driver).executeScript("arguments[0].click();", updatedToggle);
        try { Thread.sleep(2000); } catch (InterruptedException ignored) {}

        navigateTo("/admin/dashboard");
        try { Thread.sleep(3000); } catch (InterruptedException ignored) {}

        WebElement finalToggle = wait.until(ExpectedConditions.presenceOfElementLocated(
                By.cssSelector("#flags-list input[type='checkbox']")));
        assertThat(finalToggle.isSelected()).isEqualTo(initiallyChecked);
    }
}
