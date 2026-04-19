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

class HomePageTest extends BaseSeleniumTest {

    @Autowired UserRepository userRepository;
    @Autowired ProductRepository productRepository;
    @Autowired PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        productRepository.deleteAll();
        userRepository.deleteAll();

        userRepository.save(User.builder()
                .username("hometest").email("home@test.com")
                .passwordHash(passwordEncoder.encode("Password123!"))
                .role(User.Role.SHOPPER).build());

        for (int i = 1; i <= 3; i++) {
            productRepository.save(Product.builder()
                    .name("Home Product " + i).price(BigDecimal.valueOf(10 * i))
                    .stockQuantity(10).category("Electronics").description("desc").build());
        }
    }

    @Test
    void shopperSeesCatalogGrid() {
        loginViaApi("home@test.com", "Password123!");
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("product-grid")));
        wait.until(ExpectedConditions.presenceOfAllElementsLocatedBy(By.className("product-card")));
        assertThat(driver.findElements(By.className("product-card"))).isNotEmpty();
    }

    @Test
    void eachProductCardShowsNameAndPrice() {
        loginViaApi("home@test.com", "Password123!");
        wait.until(ExpectedConditions.presenceOfAllElementsLocatedBy(By.className("product-card-title")));
        assertThat(driver.findElements(By.className("product-card-title"))).isNotEmpty();
        assertThat(driver.findElements(By.className("product-card-price"))).isNotEmpty();
    }

    @Test
    void unauthenticatedUserRedirectedToLogin() {
        navigateTo("/home");
        // localStorage will be empty → JS redirects to /login
        wait.until(ExpectedConditions.urlContains("/login"));
        assertThat(driver.getCurrentUrl()).contains("/login");
    }
}
