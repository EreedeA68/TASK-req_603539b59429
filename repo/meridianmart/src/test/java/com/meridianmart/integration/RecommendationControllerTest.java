package com.meridianmart.integration;

import com.meridianmart.model.FeatureFlag;
import com.meridianmart.model.Product;
import com.meridianmart.repository.FeatureFlagRepository;
import com.meridianmart.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RecommendationControllerTest extends BaseIntegrationTest {

    @Autowired ProductRepository productRepository;
    @Autowired FeatureFlagRepository featureFlagRepository;

    @BeforeEach
    void setUpProducts() {
        productRepository.deleteAll();
        for (int i = 1; i <= 5; i++) {
            productRepository.save(Product.builder()
                    .name("Product " + i).price(BigDecimal.valueOf(10 * i))
                    .stockQuantity(20).category(i % 2 == 0 ? "Electronics" : "Food")
                    .description("desc").newArrival(i % 3 == 0).build());
        }
    }

    @Test
    void getRecommendationsReturns200WithUpTo10Products() throws Exception {
        mockMvc.perform(withShopperAuth(get("/api/recommendations")
                        .contentType(MediaType.APPLICATION_JSON)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(lessThanOrEqualTo(10)));
    }

    @Test
    void coldStartUserGetsPopularOrNewItems() throws Exception {
        // New user with no interactions — cold start
        mockMvc.perform(withShopperAuth(get("/api/recommendations")
                        .contentType(MediaType.APPLICATION_JSON)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isNotEmpty());
    }

    @Test
    void recommendationsDisabledByFlagReturnsEmptyList() throws Exception {
        featureFlagRepository.deleteAll();
        featureFlagRepository.save(FeatureFlag.builder()
                .flagName("RECOMMENDATIONS_ENABLED").enabled(false).build());

        mockMvc.perform(withShopperAuth(get("/api/recommendations")
                        .contentType(MediaType.APPLICATION_JSON)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(0));

        featureFlagRepository.deleteAll();
    }
}
