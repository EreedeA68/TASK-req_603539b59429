package com.meridianmart.frontend;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class BaseSeleniumTest {

    @LocalServerPort
    protected int port;

    protected WebDriver driver;
    protected WebDriverWait wait;

    @BeforeAll
    static void setUpDriver() {
        // Use the pre-installed chromedriver in Docker; fall back to WebDriverManager locally
        if (new java.io.File("/usr/local/bin/chromedriver").exists()) {
            System.setProperty("webdriver.chrome.driver", "/usr/local/bin/chromedriver");
        } else {
            WebDriverManager.chromedriver().setup();
        }
    }

    @BeforeEach
    void initDriver() {
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless", "--no-sandbox", "--disable-dev-shm-usage",
                "--disable-gpu", "--window-size=1280,800");
        driver = new ChromeDriver(options);
        wait = new WebDriverWait(driver, Duration.ofSeconds(15));
    }

    @AfterEach
    void tearDown() {
        if (driver != null) driver.quit();
    }

    protected String baseUrl() {
        return "http://localhost:" + port;
    }

    protected void navigateTo(String path) {
        driver.get(baseUrl() + path);
    }

    protected void setLocalStorage(String key, String value) {
        ((JavascriptExecutor) driver).executeScript(
                "window.localStorage.setItem(arguments[0], arguments[1]);", key, value);
    }

    protected void loginViaApi(String username, String password) {
        navigateTo("/login");
        wait.until(ExpectedConditions.elementToBeClickable(By.id("username")));
        driver.findElement(By.id("username")).sendKeys(username);
        driver.findElement(By.id("password")).sendKeys(password);
        driver.findElement(By.id("login-btn")).click();
        wait.until(ExpectedConditions.urlContains("/home"));
    }

    protected WebElement waitForElement(By by) {
        return wait.until(ExpectedConditions.presenceOfElementLocated(by));
    }

    protected WebElement waitForVisible(By by) {
        return wait.until(ExpectedConditions.visibilityOfElementLocated(by));
    }

    protected boolean isElementPresent(By by) {
        try {
            driver.findElement(by);
            return true;
        } catch (org.openqa.selenium.NoSuchElementException e) {
            return false;
        }
    }
}
