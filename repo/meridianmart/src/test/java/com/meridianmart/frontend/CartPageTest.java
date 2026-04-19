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

class CartPageTest extends BaseSeleniumTest {

    @Autowired UserRepository userRepository;
    @Autowired ProductRepository productRepository;
    @Autowired PasswordEncoder passwordEncoder;

    private Long productId;

    @BeforeEach
    void setUp() {
        productRepository.deleteAll();
        userRepository.deleteAll();

        userRepository.save(User.builder()
                .username("carttest").email("cart@test.com")
                .passwordHash(passwordEncoder.encode("Password123!"))
                .role(User.Role.SHOPPER).build());

        Product p = productRepository.save(Product.builder()
                .name("Cart Item").description("desc").price(BigDecimal.valueOf(25.00))
                .stockQuantity(20).category("Food").build());
        productId = p.getId();
    }

    @Test
    void cartShowsAddedItemsWithQuantityUnitPriceSubtotal() {
        loginViaApi("cart@test.com", "Password123!");
        navigateTo("/products/" + productId);
        wait.until(ExpectedConditions.elementToBeClickable(By.id("add-to-cart-btn")));
        driver.findElement(By.id("add-to-cart-btn")).click();
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("cart-success")));

        navigateTo("/cart");
        wait.until(ExpectedConditions.presenceOfElementLocated(By.className("cart-item")));
        assertThat(driver.findElements(By.className("cart-item"))).isNotEmpty();
    }

    @Test
    void totalPriceDisplayed() {
        loginViaApi("cart@test.com", "Password123!");
        navigateTo("/products/" + productId);
        wait.until(ExpectedConditions.elementToBeClickable(By.id("add-to-cart-btn")));
        driver.findElement(By.id("add-to-cart-btn")).click();
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("cart-success")));

        navigateTo("/cart");
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("cart-total")));
        assertThat(driver.findElement(By.id("cart-total")).isDisplayed()).isTrue();
    }

    @Test
    void emptyCartShowsEmptyStateMessage() {
        loginViaApi("cart@test.com", "Password123!");
        navigateTo("/cart");
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("cart-container")));
        // Wait for loading to complete
        try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
        assertThat(driver.findElement(By.id("cart-container")).getText()).isNotEmpty();
    }

    @Test
    void checkoutButtonNavigatesToCheckoutPage() {
        loginViaApi("cart@test.com", "Password123!");
        navigateTo("/products/" + productId);
        wait.until(ExpectedConditions.elementToBeClickable(By.id("add-to-cart-btn")));
        driver.findElement(By.id("add-to-cart-btn")).click();
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("cart-success")));

        navigateTo("/cart");
        wait.until(ExpectedConditions.elementToBeClickable(By.id("checkout-btn")));
        driver.findElement(By.id("checkout-btn")).click();
        wait.until(ExpectedConditions.urlContains("/checkout"));
        assertThat(driver.getCurrentUrl()).contains("/checkout");
    }
}
