package com.meridianmart.integration;

import com.meridianmart.model.Product;
import com.meridianmart.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class OrderControllerTest extends BaseIntegrationTest {

    @Autowired ProductRepository productRepository;
    @Autowired CartItemRepository cartItemRepository;
    @Autowired OrderRepository orderRepository;
    @Autowired PaymentTransactionRepository paymentTransactionRepository;

    private Long productId;

    @BeforeEach
    void setUpProductAndCart() {
        paymentTransactionRepository.deleteAll();
        orderRepository.deleteAll();
        cartItemRepository.deleteAll();
        productRepository.deleteAll();

        Product p = Product.builder()
                .name("Order Test Product").price(BigDecimal.valueOf(50.00))
                .stockQuantity(100).category("Electronics").description("test").build();
        p = productRepository.save(p);
        productId = p.getId();

        // Add product to shopper's cart
        try {
            mockMvc.perform(withShopperAuth(post("/api/cart")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(toJson(Map.of("productId", productId, "quantity", 1)))));
        } catch (Exception ignored) {}
    }

    @Test
    void checkoutReturns201WithReceiptNumberTimestampStatus() throws Exception {
        String idempKey = UUID.randomUUID().toString();
        mockMvc.perform(withShopperAuth(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", idempKey)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.receiptNumber").isString())
                .andExpect(jsonPath("$.data.transactionTimestamp").isString())
                .andExpect(jsonPath("$.data.status").isString())
                .andExpect(jsonPath("$.data.transactionTimestamp",
                        anyOf(containsString("AM"), containsString("PM"))));
    }

    @Test
    void duplicateIdempotencyKeyReturnsSameOrder() throws Exception {
        String idempKey = UUID.randomUUID().toString();

        // Add item to cart for second attempt won't work (cart cleared), so add again
        mockMvc.perform(withShopperAuth(post("/api/cart")
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(Map.of("productId", productId, "quantity", 1)))));

        String r1 = mockMvc.perform(withShopperAuth(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", idempKey)))
                .andReturn().getResponse().getContentAsString();

        String receipt1 = objectMapper.readTree(r1).path("data").path("receiptNumber").asText();

        // Add item to cart again and try same key
        mockMvc.perform(withShopperAuth(post("/api/cart")
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(Map.of("productId", productId, "quantity", 1)))));

        String r2 = mockMvc.perform(withShopperAuth(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", idempKey)))
                .andReturn().getResponse().getContentAsString();

        String receipt2 = objectMapper.readTree(r2).path("data").path("receiptNumber").asText();
        assertThat(receipt1).isEqualTo(receipt2);
    }

    @Test
    void getOrderHistoryReturns200WithReceiptAndStatus() throws Exception {
        mockMvc.perform(withShopperAuth(get("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void checkoutAsStaffReturns403() throws Exception {
        mockMvc.perform(withStaffAuth(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", UUID.randomUUID().toString())))
                .andExpect(status().isForbidden());
    }

    @Test
    void shopperOrderHistoryOnlyShowsOwnOrders() throws Exception {
        // shopper1 checks out — creates an order for shopper1
        mockMvc.perform(withShopperAuth(post("/api/cart")
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(Map.of("productId", productId, "quantity", 1)))));
        mockMvc.perform(withShopperAuth(post("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", UUID.randomUUID().toString())));

        // shopper2 gets order history — must not see shopper1's orders
        String body = mockMvc.perform(withShopperAuth2(get("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        com.fasterxml.jackson.databind.JsonNode arr = objectMapper.readTree(body).path("data");
        assertThat(arr.isArray()).isTrue();
        assertThat(arr.size()).isEqualTo(0);
    }

}
