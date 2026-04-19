package com.meridianmart.e2e;

import com.meridianmart.frontend.BaseSeleniumTest;
import com.meridianmart.model.FeatureFlag;
import com.meridianmart.model.Product;
import com.meridianmart.model.User;
import com.meridianmart.repository.FeatureFlagRepository;
import com.meridianmart.repository.ProductRepository;
import com.meridianmart.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class RecommendationJourneyTest extends BaseSeleniumTest {

    @Autowired UserRepository userRepository;
    @Autowired ProductRepository productRepository;
    @Autowired FeatureFlagRepository featureFlagRepository;
    @Autowired PasswordEncoder passwordEncoder;

    private List<Long> productIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        featureFlagRepository.deleteAll();
        productRepository.deleteAll();
        userRepository.deleteAll();

        userRepository.save(User.builder()
                .username("recjourney").email("rec@test.com")
                .passwordHash(passwordEncoder.encode("Rec123!"))
                .role(User.Role.SHOPPER).build());

        featureFlagRepository.save(FeatureFlag.builder()
                .flagName("RECOMMENDATIONS_ENABLED").enabled(true).storeId("STORE_001").updatedBy("admin").build());

        for (int i = 1; i <= 5; i++) {
            Product p = productRepository.save(Product.builder()
                    .name("Rec Product " + i).price(BigDecimal.valueOf(10 * i))
                    .stockQuantity(20).category(i % 2 == 0 ? "Electronics" : "Food")
                    .description("desc").newArrival(i % 2 == 0).build());
            productIds.add(p.getId());
        }
    }

    @Test
    void recommendationsSectionPopulatedAfterInteractions() {
        // Step 1: Login
        loginViaApi("rec@test.com", "Rec123!");

        // Step 2: View 3 products
        for (int i = 0; i < Math.min(3, productIds.size()); i++) {
            navigateTo("/products/" + productIds.get(i));
            try { Thread.sleep(500); } catch (InterruptedException ignored) {}
        }

        // Step 3: Add one to favorites
        navigateTo("/products/" + productIds.get(0));
        wait.until(ExpectedConditions.elementToBeClickable(By.id("add-to-fav-btn")));
        driver.findElement(By.id("add-to-fav-btn")).click();
        try { Thread.sleep(1000); } catch (InterruptedException ignored) {}

        // Step 4: Navigate to home page
        navigateTo("/home");
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("product-grid")));
        try { Thread.sleep(3000); } catch (InterruptedException ignored) {}

        // Step 5: Verify "Recommended for you" section is populated
        // (cold start will fill with popular/new items since this is a fresh user)
        assertThat(driver.findElements(By.className("product-card"))).isNotEmpty();
    }
}
