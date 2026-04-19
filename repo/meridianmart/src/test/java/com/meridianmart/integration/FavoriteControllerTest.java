package com.meridianmart.integration;

import com.meridianmart.model.Product;
import com.meridianmart.repository.FavoriteRepository;
import com.meridianmart.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class FavoriteControllerTest extends BaseIntegrationTest {

    @Autowired ProductRepository productRepository;
    @Autowired FavoriteRepository favoriteRepository;

    private Long productId;

    @BeforeEach
    void setUpProducts() {
        favoriteRepository.deleteAll();
        productRepository.deleteAll();

        Product p = Product.builder()
                .name("Fav Test Product").description("For favorite tests")
                .price(BigDecimal.valueOf(15.00)).stockQuantity(10)
                .category("Books").build();
        p = productRepository.save(p);
        productId = p.getId();
    }

    @Test
    void addFavoriteAsShopperReturns201() throws Exception {
        mockMvc.perform(withShopperAuth(post("/api/favorites")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("productId", productId)))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.productId").value(productId));
    }

    @Test
    void addFavoriteUnauthenticatedReturns401() throws Exception {
        mockMvc.perform(withNoAuthSigning(post("/api/favorites")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("productId", productId)))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void addFavoriteMissingProductIdReturns400() throws Exception {
        mockMvc.perform(withShopperAuth(post("/api/favorites")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void addFavoriteForNonExistentProductReturns404() throws Exception {
        mockMvc.perform(withShopperAuth(post("/api/favorites")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("productId", 999999L)))))
                .andExpect(status().isNotFound());
    }

    @Test
    void getFavoritesReturnsShopperFavorites() throws Exception {
        mockMvc.perform(withShopperAuth(post("/api/favorites")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("productId", productId)))));

        mockMvc.perform(withShopperAuth(get("/api/favorites")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].productId").value(productId));
    }

    @Test
    void getFavoritesUnauthenticatedReturns401() throws Exception {
        mockMvc.perform(withNoAuthSigning(get("/api/favorites")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void removeFavoriteByOwnerSucceeds() throws Exception {
        String addBody = mockMvc.perform(withShopperAuth(post("/api/favorites")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("productId", productId)))))
                .andReturn().getResponse().getContentAsString();

        Long favId = objectMapper.readTree(addBody).path("data").path("id").asLong();

        mockMvc.perform(withShopperAuth(delete("/api/favorites/" + favId)))
                .andExpect(status().isOk());

        mockMvc.perform(withShopperAuth(get("/api/favorites")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    void removeNonExistentFavoriteReturns404() throws Exception {
        mockMvc.perform(withShopperAuth(delete("/api/favorites/999999")))
                .andExpect(status().isNotFound());
    }

    @Test
    void removeFavoriteUnauthenticatedReturns401() throws Exception {
        mockMvc.perform(withNoAuthSigning(delete("/api/favorites/1")))
                .andExpect(status().isUnauthorized());
    }
}
