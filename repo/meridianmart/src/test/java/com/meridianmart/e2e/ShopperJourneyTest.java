package com.meridianmart.e2e;

import com.meridianmart.frontend.BaseSeleniumTest;
import com.meridianmart.model.Product;
import com.meridianmart.model.User;
import com.meridianmart.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

class ShopperJourneyTest extends BaseSeleniumTest {

    @Autowired UserRepository userRepository;
    @Autowired ProductRepository productRepository;
    @Autowired CartItemRepository cartItemRepository;
    @Autowired OrderRepository orderRepository;
    @Autowired PaymentTransactionRepository paymentTransactionRepository;
    @Autowired PasswordEncoder passwordEncoder;

    private Long productId;

    @BeforeEach
    void setUp() {
        paymentTransactionRepository.deleteAll();
        orderRepository.deleteAll();
        cartItemRepository.deleteAll();
        productRepository.deleteAll();
        userRepository.deleteAll();

        userRepository.save(User.builder()
                .username("journeyshopper").email("journey@test.com")
                .passwordHash(passwordEncoder.encode("Journey123!"))
                .role(User.Role.SHOPPER).build());

        Product p = productRepository.save(Product.builder()
                .name("Journey Product").description("E2E test product")
                .price(BigDecimal.valueOf(30.00)).stockQuantity(50)
                .category("Electronics").build());
        productId = p.getId();
    }

    @Test
    void fullShopperJourney() {
        // Step 1: Login
        loginViaApi("journey@test.com", "Journey123!");
        assertThat(driver.getCurrentUrl()).contains("/home");

        // Step 2: View home page
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("product-grid")));

        // Step 3: Click a product
        navigateTo("/products/" + productId);
        wait.until(ExpectedConditions.textToBePresentInElementLocated(
                By.id("product-name"), "Journey Product"));

        // Step 4: Add to cart
        wait.until(ExpectedConditions.elementToBeClickable(By.id("add-to-cart-btn")));
        driver.findElement(By.id("add-to-cart-btn")).click();
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("cart-success")));

        // Step 5: Go to cart
        navigateTo("/cart");
        wait.until(ExpectedConditions.presenceOfElementLocated(By.className("cart-item")));

        // Step 6: Go to checkout
        wait.until(ExpectedConditions.elementToBeClickable(By.id("checkout-btn")));
        driver.findElement(By.id("checkout-btn")).click();
        wait.until(ExpectedConditions.urlContains("/checkout"));

        // Step 7: Confirm order
        wait.until(ExpectedConditions.elementToBeClickable(By.id("confirm-btn")));
        driver.findElement(By.id("confirm-btn")).click();

        // Step 8: Verify confirmation page
        wait.until(ExpectedConditions.urlContains("/confirmation"));
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("receipt-number")));

        String receipt = driver.findElement(By.id("receipt-number")).getText();
        assertThat(receipt).isNotBlank();

        String timestamp = driver.findElement(By.id("receipt-timestamp")).getText();
        assertThat(timestamp).matches(".*(?:AM|PM).*");
    }
}
