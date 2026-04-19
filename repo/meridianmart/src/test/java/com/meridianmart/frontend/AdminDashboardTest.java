package com.meridianmart.frontend;

import com.meridianmart.model.FeatureFlag;
import com.meridianmart.model.User;
import com.meridianmart.repository.FeatureFlagRepository;
import com.meridianmart.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.*;

class AdminDashboardTest extends BaseSeleniumTest {

    @Autowired UserRepository userRepository;
    @Autowired FeatureFlagRepository featureFlagRepository;
    @Autowired PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        cleanDatabase();

        userRepository.save(User.builder()
                .username("adminui").email("admin@test.com")
                .passwordHash(passwordEncoder.encode("Admin123!"))
                .role(User.Role.ADMIN).build());
        userRepository.save(User.builder()
                .username("staffui").email("staff@test.com")
                .passwordHash(passwordEncoder.encode("Staff123!"))
                .role(User.Role.STAFF).build());
        userRepository.save(User.builder()
                .username("shopperui").email("shopper@test.com")
                .passwordHash(passwordEncoder.encode("User123!"))
                .role(User.Role.SHOPPER).build());

        featureFlagRepository.save(FeatureFlag.builder()
                .flagName("UI_TEST_FLAG").enabled(true).storeId("STORE_001").updatedBy("admin").build());
    }

    @Test
    void pageAccessibleToAdmin() {
        loginViaApi("admin@test.com", "Admin123!");
        navigateTo("/admin/dashboard");
        assertThat(driver.getCurrentUrl()).contains("/admin/dashboard");
        assertThat(isElementPresent(By.id("admin-dashboard"))).isTrue();
    }

    @Test
    void pageNotAccessibleToStaff() {
        loginViaApi("staff@test.com", "Staff123!");
        navigateTo("/admin/dashboard");
        try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
        assertThat(driver.getCurrentUrl()).doesNotContain("/admin/dashboard");
    }

    @Test
    void pageNotAccessibleToShopper() {
        loginViaApi("shopper@test.com", "User123!");
        navigateTo("/admin/dashboard");
        try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
        assertThat(driver.getCurrentUrl()).doesNotContain("/admin/dashboard");
    }

    @Test
    void featureFlagsListRendersWithToggleControls() {
        loginViaApi("admin@test.com", "Admin123!");
        navigateTo("/admin/dashboard");
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("flags-list")));
        try { Thread.sleep(3000); } catch (InterruptedException ignored) {}
        assertThat(driver.getPageSource()).contains("UI_TEST_FLAG");
        assertThat(driver.findElements(By.className("toggle-switch"))).isNotEmpty();
    }

    @Test
    void complianceReportsSectionRendersWithData() {
        loginViaApi("admin@test.com", "Admin123!");
        navigateTo("/admin/dashboard");
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("compliance-data")));
        try { Thread.sleep(3000); } catch (InterruptedException ignored) {}
        assertThat(driver.findElement(By.id("compliance-data")).getText()).isNotEmpty();
    }
}
