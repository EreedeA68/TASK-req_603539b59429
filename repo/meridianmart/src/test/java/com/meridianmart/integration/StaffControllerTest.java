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

class StaffControllerTest extends BaseIntegrationTest {

    @Autowired OrderRepository orderRepository;
    @Autowired ProductRepository productRepository;
    @Autowired PaymentTransactionRepository paymentTransactionRepository;

    private String receiptNumber;
    private Long orderId;

    @BeforeEach
    void setUpOrder() {
        paymentTransactionRepository.deleteAll();
        orderRepository.deleteAll();
        productRepository.deleteAll();

        Product product = Product.builder().name("Staff Test Product")
                .price(BigDecimal.valueOf(100)).stockQuantity(10)
                .category("Electronics").description("desc").build();
        product = productRepository.save(product);

        User shopper = userRepository.findById(shopperId).orElseThrow();
        receiptNumber = "RCP-STAFF-" + System.currentTimeMillis();
        Order order = Order.builder()
                .user(shopper).receiptNumber(receiptNumber)
                .totalAmount(BigDecimal.valueOf(100)).status(Order.OrderStatus.COMPLETED)
                .transactionTimestamp(LocalDateTime.now())
                .idempotencyKey(UUID.randomUUID().toString())
                .items(List.of())
                .build();
        order = orderRepository.save(order);
        orderId = order.getId();

        PaymentTransaction tx = PaymentTransaction.builder()
                .order(order).amount(BigDecimal.valueOf(100))
                .status(PaymentTransaction.PaymentStatus.SUCCESS)
                .idempotencyKey(UUID.randomUUID().toString()).build();
        paymentTransactionRepository.save(tx);
    }

    @Test
    void getTransactionAsStaffReturns200WithFullPayload() throws Exception {
        mockMvc.perform(withStaffAuth(get("/api/transactions/" + receiptNumber)
                        .contentType(MediaType.APPLICATION_JSON)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.receiptNumber").value(receiptNumber))
                .andExpect(jsonPath("$.data.totalAmount").isNumber())
                .andExpect(jsonPath("$.data.status").isString());
    }

    @Test
    void getTransactionAsShopperReturns403() throws Exception {
        mockMvc.perform(withShopperAuth(get("/api/transactions/" + receiptNumber)
                        .contentType(MediaType.APPLICATION_JSON)))
                .andExpect(status().isForbidden());
    }

    @Test
    void refundAsStaffReturns200WithRefundIdAndStatus() throws Exception {
        String idempKey = "refund-test-" + UUID.randomUUID();
        mockMvc.perform(withStaffAuth(post("/api/refunds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of(
                                "receiptNumber", receiptNumber,
                                "idempotencyKey", idempKey
                        )))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.refundId").isNumber())
                .andExpect(jsonPath("$.data.updatedOrderStatus").value("REFUNDED"));
    }

    @Test
    void markReadyForPickupAsStaffReturns200WithCorrectStatus() throws Exception {
        mockMvc.perform(withStaffAuth(put("/api/orders/" + orderId + "/ready-for-pickup")
                        .contentType(MediaType.APPLICATION_JSON)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("READY_FOR_PICKUP"));
    }

    @Test
    void markReadyForPickupAsShopperReturns403() throws Exception {
        mockMvc.perform(withShopperAuth(put("/api/orders/" + orderId + "/ready-for-pickup")
                        .contentType(MediaType.APPLICATION_JSON)))
                .andExpect(status().isForbidden());
    }

    // ---- Cross-store isolation tests ----

    @Test
    void getTransactionFromDifferentStoreReturns404() throws Exception {
        // STORE_001 receipt looked up by STORE_002 staff → not found
        mockMvc.perform(withStaffAuth2(get("/api/transactions/" + receiptNumber)
                        .contentType(MediaType.APPLICATION_JSON)))
                .andExpect(status().isNotFound());
    }

    @Test
    void refundFromDifferentStoreReturns404() throws Exception {
        String idempKey = "refund-cross-store-" + UUID.randomUUID();
        mockMvc.perform(withStaffAuth2(post("/api/refunds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of(
                                "receiptNumber", receiptNumber,
                                "idempotencyKey", idempKey
                        )))))
                .andExpect(status().isNotFound());
    }

    @Test
    void markReadyForPickupFromDifferentStoreReturns404() throws Exception {
        mockMvc.perform(withStaffAuth2(put("/api/orders/" + orderId + "/ready-for-pickup")
                        .contentType(MediaType.APPLICATION_JSON)))
                .andExpect(status().isNotFound());
    }

    @Test
    void posConfirmByStaffReturns200WithCompletedStatus() throws Exception {
        User shopper = userRepository.findById(shopperId).orElseThrow();
        Order pendingOrder = orderRepository.save(Order.builder()
                .user(shopper).receiptNumber("RCP-POS-" + System.currentTimeMillis())
                .totalAmount(BigDecimal.valueOf(50)).status(Order.OrderStatus.PENDING_AT_REGISTER)
                .transactionTimestamp(LocalDateTime.now())
                .idempotencyKey(UUID.randomUUID().toString())
                .items(List.of()).build());

        mockMvc.perform(withStaffAuth(post("/api/orders/" + pendingOrder.getId() + "/pos-confirm")
                        .contentType(MediaType.APPLICATION_JSON)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));
    }

    @Test
    void posConfirmByShopperReturns403() throws Exception {
        User shopper = userRepository.findById(shopperId).orElseThrow();
        Order pendingOrder = orderRepository.save(Order.builder()
                .user(shopper).receiptNumber("RCP-POS-SHOP-" + System.currentTimeMillis())
                .totalAmount(BigDecimal.valueOf(50)).status(Order.OrderStatus.PENDING_AT_REGISTER)
                .transactionTimestamp(LocalDateTime.now())
                .idempotencyKey(UUID.randomUUID().toString())
                .items(List.of()).build());

        mockMvc.perform(withShopperAuth(post("/api/orders/" + pendingOrder.getId() + "/pos-confirm")
                        .contentType(MediaType.APPLICATION_JSON)))
                .andExpect(status().isForbidden());
    }

    @Test
    void markReadyForPickupOnPendingOrderReturns400() throws Exception {
        User shopper = userRepository.findById(shopperId).orElseThrow();
        Order pendingOrder = orderRepository.save(Order.builder()
                .user(shopper).receiptNumber("RCP-PICKUP-GUARD-" + System.currentTimeMillis())
                .totalAmount(BigDecimal.valueOf(50)).status(Order.OrderStatus.PENDING_AT_REGISTER)
                .transactionTimestamp(LocalDateTime.now())
                .idempotencyKey(UUID.randomUUID().toString())
                .items(List.of()).build());

        mockMvc.perform(withStaffAuth(put("/api/orders/" + pendingOrder.getId() + "/ready-for-pickup")
                        .contentType(MediaType.APPLICATION_JSON)))
                .andExpect(status().isBadRequest());
    }
}
