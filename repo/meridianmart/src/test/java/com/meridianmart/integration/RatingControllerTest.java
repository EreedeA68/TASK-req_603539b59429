package com.meridianmart.integration;

import com.meridianmart.model.*;
import com.meridianmart.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RatingControllerTest extends BaseIntegrationTest {

    @Autowired ProductRepository productRepository;
    @Autowired OrderRepository orderRepository;
    @Autowired OrderItemRepository orderItemRepository;
    @Autowired RatingRepository ratingRepository;
    @Autowired PaymentTransactionRepository paymentTransactionRepository;

    private Long productId;

    @BeforeEach
    void setUp() {
        ratingRepository.deleteAll();
        paymentTransactionRepository.deleteAll();
        orderRepository.deleteAll();
        productRepository.deleteAll();

        Product p = Product.builder().name("Rate Me").price(BigDecimal.valueOf(20))
                .stockQuantity(10).category("Food").description("desc").build();
        p = productRepository.save(p);
        productId = p.getId();

        // Create a completed order for the shopper so rating is allowed
        User shopper = userRepository.findById(shopperId).orElseThrow();
        Order order = Order.builder()
                .user(shopper).receiptNumber("RCP-RATE-001")
                .totalAmount(BigDecimal.valueOf(20)).status(Order.OrderStatus.COMPLETED)
                .transactionTimestamp(LocalDateTime.now()).idempotencyKey(UUID.randomUUID().toString())
                .items(List.of())
                .build();
        order = orderRepository.save(order);

        OrderItem item = OrderItem.builder().order(order).product(p).quantity(1)
                .unitPrice(p.getPrice()).build();
        orderItemRepository.save(item);

        PaymentTransaction tx = PaymentTransaction.builder().order(order)
                .amount(BigDecimal.valueOf(20)).idempotencyKey(UUID.randomUUID().toString())
                .status(PaymentTransaction.PaymentStatus.SUCCESS).build();
        paymentTransactionRepository.save(tx);
    }

    @Test
    void rateProductScore1to5Returns201() throws Exception {
        mockMvc.perform(withShopperAuth(post("/api/ratings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("productId", productId, "score", 5)))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.ratingId").isNumber())
                .andExpect(jsonPath("$.data.productId").value(productId))
                .andExpect(jsonPath("$.data.score").value(5));
    }

    @Test
    void rateProductWithScore0Returns400() throws Exception {
        mockMvc.perform(withShopperAuth(post("/api/ratings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("productId", productId, "score", 0)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorMessage").isString());
    }

    @Test
    void rateProductWithScore6Returns400() throws Exception {
        mockMvc.perform(withShopperAuth(post("/api/ratings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("productId", productId, "score", 6)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorMessage").isString());
    }

    @Test
    void rateProductNotPurchasedReturns403() throws Exception {
        Product notPurchased = Product.builder().name("Not Bought").price(BigDecimal.valueOf(50))
                .stockQuantity(10).category("Electronics").description("desc").build();
        notPurchased = productRepository.save(notPurchased);
        Long notPurchasedId = notPurchased.getId();

        mockMvc.perform(withShopperAuth(post("/api/ratings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("productId", notPurchasedId, "score", 3)))))
                .andExpect(status().isForbidden());
    }
}
