package com.meridianmart.integration;

import com.meridianmart.model.Product;
import com.meridianmart.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class BehaviorControllerTest extends BaseIntegrationTest {

    @Autowired ProductRepository productRepository;

    private Long productId;

    @BeforeEach
    void setUpProducts() {
        productRepository.deleteAll();

        Product p = Product.builder()
                .name("Behavior Test Product").description("For behavior tests")
                .price(BigDecimal.valueOf(10.00)).stockQuantity(5)
                .category("Electronics").build();
        p = productRepository.save(p);
        productId = p.getId();
    }

    @Test
    void recordValidBehaviorEventAsShopperReturns200() throws Exception {
        mockMvc.perform(withShopperAuth(post("/api/behavior")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("productId", productId, "eventType", "VIEW")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Event recorded"));
    }

    @Test
    void recordBehaviorEventUnauthenticatedReturns401() throws Exception {
        mockMvc.perform(withNoAuthSigning(post("/api/behavior")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("productId", productId, "eventType", "VIEW")))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void recordBehaviorEventMissingProductIdReturns400() throws Exception {
        mockMvc.perform(withShopperAuth(post("/api/behavior")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("eventType", "VIEW")))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void recordBehaviorEventMissingEventTypeReturns400() throws Exception {
        mockMvc.perform(withShopperAuth(post("/api/behavior")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("productId", productId)))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void recordBehaviorAsAdminReturns200() throws Exception {
        mockMvc.perform(withAdminAuth(post("/api/behavior")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("productId", productId, "eventType", "PURCHASE")))))
                .andExpect(status().isOk());
    }
}
