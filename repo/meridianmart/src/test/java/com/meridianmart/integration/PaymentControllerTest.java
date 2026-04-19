package com.meridianmart.integration;

import com.meridianmart.model.Order;
import com.meridianmart.model.PaymentTransaction;
import com.meridianmart.model.Product;
import com.meridianmart.model.User;
import com.meridianmart.repository.OrderRepository;
import com.meridianmart.repository.PaymentTransactionRepository;
import com.meridianmart.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PaymentControllerTest extends BaseIntegrationTest {

    @Autowired OrderRepository orderRepository;
    @Autowired ProductRepository productRepository;
    @Autowired PaymentTransactionRepository paymentTransactionRepository;

    private Long orderId;

    @BeforeEach
    void setUpOrder() {
        paymentTransactionRepository.deleteAll();
        orderRepository.deleteAll();
        productRepository.deleteAll();

        User staff = userRepository.findByUsername("staff_test").orElseThrow();

        Product product = productRepository.save(Product.builder()
                .name("Test Product").price(BigDecimal.valueOf(50.00))
                .stockQuantity(10).category("TEST").storeId("STORE_001").build());

        User shopper = userRepository.findByUsername("shopper_test").orElseThrow();
        Order order = orderRepository.save(Order.builder()
                .user(shopper)
                .receiptNumber("RCP-TEST-" + UUID.randomUUID().toString().substring(0, 8))
                .totalAmount(BigDecimal.valueOf(50.00))
                .status(Order.OrderStatus.PENDING_AT_REGISTER)
                .transactionTimestamp(LocalDateTime.now())
                .idempotencyKey("order-" + UUID.randomUUID())
                .storeId("STORE_001")
                .build());
        orderId = order.getId();
    }

    @Test
    void depositByStaffReturns201WithTransactionDetails() throws Exception {
        mockMvc.perform(withStaffAuth(post("/api/payments/deposit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of(
                                "orderId", orderId,
                                "idempotencyKey", "deposit-" + UUID.randomUUID())))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.transactionId").isNumber())
                .andExpect(jsonPath("$.data.status").value("DEPOSIT"))
                .andExpect(jsonPath("$.data.amount").value(50.0));
    }

    @Test
    void depositByShopperReturns403() throws Exception {
        mockMvc.perform(withShopperAuth(post("/api/payments/deposit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of(
                                "orderId", orderId,
                                "idempotencyKey", "deposit-" + UUID.randomUUID())))))
                .andExpect(status().isForbidden());
    }

    @Test
    void depositWithoutAuthReturns401() throws Exception {
        mockMvc.perform(withNoAuthSigning(post("/api/payments/deposit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of(
                                "orderId", orderId,
                                "idempotencyKey", "deposit-" + UUID.randomUUID())))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void depositForNonExistentOrderReturns404() throws Exception {
        mockMvc.perform(withStaffAuth(post("/api/payments/deposit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of(
                                "orderId", 999999L,
                                "idempotencyKey", "deposit-" + UUID.randomUUID())))))
                .andExpect(status().isNotFound());
    }

    @Test
    void captureByStaffReturns200WithTransactionDetails() throws Exception {
        mockMvc.perform(withStaffAuth(post("/api/payments/capture")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of(
                                "orderId", orderId,
                                "idempotencyKey", "capture-" + UUID.randomUUID())))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.transactionId").isNumber())
                .andExpect(jsonPath("$.data.status").value("SUCCESS"));
    }

    @Test
    void captureByShopperReturns403() throws Exception {
        mockMvc.perform(withShopperAuth(post("/api/payments/capture")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of(
                                "orderId", orderId,
                                "idempotencyKey", "capture-" + UUID.randomUUID())))))
                .andExpect(status().isForbidden());
    }

    @Test
    void depositIdempotencyReturnsSameTransactionOnDuplicate() throws Exception {
        String idemKey = "deposit-idem-" + UUID.randomUUID();
        String body = toJson(Map.of("orderId", orderId, "idempotencyKey", idemKey));

        // First request
        String firstResponse = mockMvc.perform(withStaffAuth(post("/api/payments/deposit")
                        .contentType(MediaType.APPLICATION_JSON).content(body)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        // Second request with same key returns same transaction
        int firstTxnId = ((Number) com.jayway.jsonpath.JsonPath.read(firstResponse, "$.data.transactionId")).intValue();
        mockMvc.perform(withStaffAuth(post("/api/payments/deposit")
                        .contentType(MediaType.APPLICATION_JSON).content(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.transactionId").value(firstTxnId));
    }

    @Test
    void crossStoreStaffCannotAccessOtherStoreOrder() throws Exception {
        mockMvc.perform(withStaffAuth2(post("/api/payments/deposit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of(
                                "orderId", orderId,
                                "idempotencyKey", "deposit-" + UUID.randomUUID())))))
                .andExpect(status().isNotFound());
    }

    @Test
    void preAuthorizeByStaffReturns201WithPreAuthorizedStatus() throws Exception {
        mockMvc.perform(withStaffAuth(post("/api/payments/pre-authorize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of(
                                "orderId", orderId,
                                "idempotencyKey", "preauth-" + UUID.randomUUID())))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PRE_AUTHORIZED"));
    }

    @Test
    void depositIdempotencyKeyReusedForDifferentOrderReturns409() throws Exception {
        // Create a second order
        User shopper = userRepository.findByUsername("shopper_test").orElseThrow();
        Order order2 = orderRepository.save(Order.builder()
                .user(shopper)
                .receiptNumber("RCP-CROSS-" + UUID.randomUUID().toString().substring(0, 8))
                .totalAmount(BigDecimal.valueOf(75.00))
                .status(Order.OrderStatus.PENDING_AT_REGISTER)
                .transactionTimestamp(java.time.LocalDateTime.now())
                .idempotencyKey("order2-" + UUID.randomUUID())
                .storeId("STORE_001")
                .build());

        String sharedIdemKey = "shared-key-" + UUID.randomUUID();

        // First: use key for original order — succeeds
        mockMvc.perform(withStaffAuth(post("/api/payments/deposit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("orderId", orderId, "idempotencyKey", sharedIdemKey)))))
                .andExpect(status().isCreated());

        // Second: attempt to reuse same key for a different order — must be rejected as conflict
        mockMvc.perform(withStaffAuth(post("/api/payments/deposit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("orderId", order2.getId(), "idempotencyKey", sharedIdemKey)))))
                .andExpect(status().isConflict());
    }
}
