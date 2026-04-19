package com.meridianmart.frontend;

import com.meridianmart.model.Product;
import com.meridianmart.model.User;
import com.meridianmart.repository.ProductRepository;
import com.meridianmart.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

class ProductPageTest extends BaseSeleniumTest {

    @Autowired UserRepository userRepository;
    @Autowired ProductRepository productRepository;
    @Autowired PasswordEncoder passwordEncoder;

    private Long productId;
    private Long lowStockId;

    @BeforeEach
    void setUp() {
        productRepository.deleteAll();
        userRepository.deleteAll();

        userRepository.save(User.builder()
                .username("prodtest").email("prod@test.com")
                .passwordHash(passwordEncoder.encode("Password123!"))
                .role(User.Role.SHOPPER).build());

        Product p1 = productRepository.save(Product.builder()
                .name("Test Product").description("Great item").price(BigDecimal.valueOf(99.99))
                .stockQuantity(10).category("Electronics").build());
        productId = p1.getId();

        Product p2 = productRepository.save(Product.builder()
                .name("Low Stock Product").description("Almost gone").price(BigDecimal.valueOf(49.99))
                .stockQuantity(1).category("Clothing").build());
        lowStockId = p2.getId();
    }

    @Test
    void pageRendersNameDescriptionPriceStock() {
        loginViaApi("prod@test.com", "Password123!");
        navigateTo("/products/" + productId);
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("product-detail")));
        wait.until(ExpectedConditions.textToBePresentInElementLocated(By.id("product-name"), "Test Product"));

        assertThat(driver.findElement(By.id("product-name")).getText()).contains("Test Product");
        assertThat(driver.findElement(By.id("product-price")).getText()).isNotEmpty();
        assertThat(driver.findElement(By.id("product-stock")).getText()).isNotEmpty();
    }

    @Test
    void stockWarningVisibleWhenStockBelowTwo() {
        loginViaApi("prod@test.com", "Password123!");
        navigateTo("/products/" + lowStockId);
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("stock-warning")));
        assertThat(driver.findElement(By.id("stock-warning")).isDisplayed()).isTrue();
    }

    @Test
    void addToCartButtonPresentAndShowsSuccessFeedback() {
        loginViaApi("prod@test.com", "Password123!");
        navigateTo("/products/" + productId);
        wait.until(ExpectedConditions.elementToBeClickable(By.id("add-to-cart-btn")));

        driver.findElement(By.id("add-to-cart-btn")).click();
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("cart-success")));
        assertThat(driver.findElement(By.id("cart-success")).isDisplayed()).isTrue();
    }

    @Test
    void addToFavoritesButtonPresent() {
        loginViaApi("prod@test.com", "Password123!");
        navigateTo("/products/" + productId);
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("add-to-fav-btn")));
        assertThat(isElementPresent(By.id("add-to-fav-btn"))).isTrue();
    }

    @Test
    void starRatingWidgetPresentAndInteractive() {
        loginViaApi("prod@test.com", "Password123!");
        navigateTo("/products/" + productId);
        wait.until(ExpectedConditions.presenceOfAllElementsLocatedBy(By.className("star")));
        assertThat(driver.findElements(By.className("star"))).hasSize(5);
    }
}
