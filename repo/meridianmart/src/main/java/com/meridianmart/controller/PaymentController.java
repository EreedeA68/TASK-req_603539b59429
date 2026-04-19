package com.meridianmart.controller;

import com.meridianmart.dto.ApiResponse;
import com.meridianmart.dto.CaptureRequest;
import com.meridianmart.dto.DepositRequest;
import com.meridianmart.dto.PreAuthorizeRequest;
import com.meridianmart.model.Order;
import com.meridianmart.model.PaymentTransaction;
import com.meridianmart.model.User;
import com.meridianmart.payment.PaymentService;
import com.meridianmart.repository.OrderRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final OrderRepository orderRepository;

    @PostMapping("/deposit")
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> initiateDeposit(
            @Valid @RequestBody DepositRequest request,
            @AuthenticationPrincipal User user) {
        Order order = orderRepository.findByIdAndStoreId(request.getOrderId(), user.getStoreId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));

        PaymentTransaction deposit = paymentService.initiateDeposit(order, order.getTotalAmount(),
                request.getIdempotencyKey());
        Map<String, Object> result = Map.of(
                "transactionId", deposit.getId(),
                "status", deposit.getStatus().name(),
                "amount", deposit.getAmount()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(result));
    }

    @PostMapping("/capture")
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> capturePayment(
            @Valid @RequestBody CaptureRequest request,
            @AuthenticationPrincipal User user) {
        Order order = orderRepository.findByIdAndStoreId(request.getOrderId(), user.getStoreId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));

        PaymentTransaction capture = paymentService.capturePayment(order, request.getIdempotencyKey());
        Map<String, Object> result = Map.of(
                "transactionId", capture.getId(),
                "status", capture.getStatus().name(),
                "amount", capture.getAmount()
        );
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping("/pre-authorize")
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> preAuthorizePayment(
            @Valid @RequestBody PreAuthorizeRequest request,
            @AuthenticationPrincipal User user) {
        Order order = orderRepository.findByIdAndStoreId(request.getOrderId(), user.getStoreId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));

        PaymentTransaction preAuth = paymentService.preAuthorizePayment(order, order.getTotalAmount(),
                request.getIdempotencyKey());
        Map<String, Object> result = Map.of(
                "transactionId", preAuth.getId(),
                "status", preAuth.getStatus().name(),
                "amount", preAuth.getAmount()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(result));
    }
}
