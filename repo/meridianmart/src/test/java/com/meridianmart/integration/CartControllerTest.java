package com.meridianmart.integration;

import com.meridianmart.model.Product;
import com.meridianmart.repository.CartItemRepository;
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

class CartControllerTest extends BaseIntegrationTest {

    @Autowired ProductRepository productRepository;
    @Autowired CartItemRepository cartItemRepository;

    private Long productId;

    @BeforeEach
    void setUpProducts() {
        cartItemRepository.deleteAll();
        productRepository.deleteAll();

        Product p = Product.builder()
                .name("Cart Test Product").description("For cart tests")
                .price(BigDecimal.valueOf(25.00)).stockQuantity(20)
                .category("Electronics").build();
        p = productRepository.save(p);
        productId = p.getId();
    }

    @Test
    void addToCartAsShopperReturns201WithCartItem() throws Exception {
        String response = mockMvc.perform(withShopperAuth(post("/api/cart")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("productId", productId, "quantity", 1)))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.productId").value(productId))
                .andExpect(jsonPath("$.data.quantity").value(1))
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void addToCartUnauthenticatedReturns401() throws Exception {
        mockMvc.perform(post("/api/cart")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("productId", productId, "quantity", 1))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getCartReturns200WithItemsAndTotalPrice() throws Exception {
        // First add an item
        mockMvc.perform(withShopperAuth(post("/api/cart")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("productId", productId, "quantity", 2)))));

        mockMvc.perform(withShopperAuth(get("/api/cart")
                        .contentType(MediaType.APPLICATION_JSON)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.totalPrice").isNumber());
    }

    @Test
    void deleteCartItemRemovesItFromCart() throws Exception {
        // Add item
        String addResponse = mockMvc.perform(withShopperAuth(post("/api/cart")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("productId", productId, "quantity", 1)))))
                .andReturn().getResponse().getContentAsString();

        Long itemId = objectMapper.readTree(addResponse).path("data").path("id").asLong();

        // Delete item
        mockMvc.perform(withShopperAuth(delete("/api/cart/" + itemId)
                        .contentType(MediaType.APPLICATION_JSON)))
                .andExpect(status().isOk());

        // Verify cart is empty
        mockMvc.perform(withShopperAuth(get("/api/cart")
                        .contentType(MediaType.APPLICATION_JSON)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isEmpty());
    }

    @Test
    void shopperCannotAddProductFromDifferentStoreToCart() throws Exception {
        // STORE_001 product added by STORE_002 shopper → 404 (product not visible to that store)
        mockMvc.perform(withShopperAuth2(post("/api/cart")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("productId", productId, "quantity", 1)))))
                .andExpect(status().isNotFound());
    }
}
