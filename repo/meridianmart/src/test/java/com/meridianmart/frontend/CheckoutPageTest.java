package com.meridianmart.frontend;

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

class CheckoutPageTest extends BaseSeleniumTest {

    @Autowired UserRepository userRepository;
    @Autowired ProductRepository productRepository;
    @Autowired CartItemRepository cartItemRepository;
    @Autowired OrderRepository orderRepository;
    @Autowired PaymentTransactionRepository paymentTransactionRepository;
    @Autowired PasswordEncoder passwordEncoder;

    private Long productId;

    @BeforeEach
    void setUp() {
        cleanDatabase();

        userRepository.save(User.builder()
                .username("checkouttest").email("checkout@test.com")
                .passwordHash(passwordEncoder.encode("Checkout123!"))
                .role(User.Role.SHOPPER).build());

        Product p = productRepository.save(Product.builder()
                .name("Checkout Product").description("desc")
                .price(BigDecimal.valueOf(45.00)).stockQuantity(20)
                .category("Home").build());
        productId = p.getId();
    }

    @Test
    void orderSummaryShowsAllCartItems() {
        loginViaApi("checkout@test.com", "Checkout123!");

        // Add item to cart
        navigateTo("/products/" + productId);
        wait.until(ExpectedConditions.elementToBeClickable(By.id("add-to-cart-btn")));
        driver.findElement(By.id("add-to-cart-btn")).click();
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("cart-success")));

        navigateTo("/checkout");
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("order-summary")));
        try { Thread.sleep(2000); } catch (InterruptedException ignored) {}

        assertThat(driver.findElement(By.id("order-summary")).getText())
                .contains("Checkout Product");
    }

    @Test
    void confirmButtonSubmitsAndRedirectsToConfirmation() {
        loginViaApi("checkout@test.com", "Checkout123!");

        // Add item to cart
        navigateTo("/products/" + productId);
        wait.until(ExpectedConditions.elementToBeClickable(By.id("add-to-cart-btn")));
        driver.findElement(By.id("add-to-cart-btn")).click();
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("cart-success")));

        navigateTo("/checkout");
        wait.until(ExpectedConditions.elementToBeClickable(By.id("confirm-btn")));
        try { Thread.sleep(2000); } catch (InterruptedException ignored) {}

        driver.findElement(By.id("confirm-btn")).click();
        wait.until(ExpectedConditions.urlContains("/confirmation"));
        assertThat(driver.getCurrentUrl()).contains("/confirmation");
    }
}
