package com.meridianmart.frontend;

import com.meridianmart.model.User;
import com.meridianmart.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.*;

class LoginPageTest extends BaseSeleniumTest {

    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUpUser() {
        cleanDatabase();
        userRepository.save(User.builder()
                .username("testlogin").email("login@test.com")
                .passwordHash(passwordEncoder.encode("Password123!"))
                .role(User.Role.SHOPPER).build());
    }

    @Test
    void pageRendersUsernamePasswordAndSubmitButton() {
        navigateTo("/login");
        assertThat(isElementPresent(By.id("username"))).isTrue();
        assertThat(isElementPresent(By.id("password"))).isTrue();
        assertThat(isElementPresent(By.id("login-btn"))).isTrue();
    }

    @Test
    void validCredentialsRedirectToHomePage() {
        loginViaApi("testlogin", "Password123!");
        assertThat(driver.getCurrentUrl()).contains("/home");
    }

    @Test
    void invalidCredentialsShowsErrorMessage() {
        navigateTo("/login");
        wait.until(ExpectedConditions.elementToBeClickable(By.id("username")));
        driver.findElement(By.id("username")).sendKeys("testlogin");
        driver.findElement(By.id("password")).sendKeys("WrongPass");
        driver.findElement(By.id("login-btn")).click();

        WebElement error = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("login-error")));
        assertThat(error.isDisplayed()).isTrue();
    }

    @Test
    void after5FailuresAccountLockedMessageVisible() {
        navigateTo("/login");
        wait.until(ExpectedConditions.elementToBeClickable(By.id("username")));

        for (int i = 0; i < 5; i++) {
            driver.findElement(By.id("username")).clear();
            driver.findElement(By.id("password")).clear();
            driver.findElement(By.id("username")).sendKeys("testlogin");
            driver.findElement(By.id("password")).sendKeys("WrongPass" + i);
            driver.findElement(By.id("login-btn")).click();
            try { Thread.sleep(300); } catch (InterruptedException ignored) {}
        }

        // 6th attempt — should show locked
        driver.findElement(By.id("username")).clear();
        driver.findElement(By.id("password")).clear();
        driver.findElement(By.id("username")).sendKeys("testlogin");
        driver.findElement(By.id("password")).sendKeys("WrongPass5");
        driver.findElement(By.id("login-btn")).click();

        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("locked-message")));
        WebElement lockedMsg = driver.findElement(By.id("locked-message"));
        assertThat(lockedMsg.isDisplayed()).isTrue();
    }
}
