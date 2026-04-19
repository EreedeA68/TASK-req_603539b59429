package com.meridianmart.controller;

import com.meridianmart.dto.ApiResponse;
import com.meridianmart.dto.OrderDto;
import com.meridianmart.dto.RefundRequest;
import com.meridianmart.dto.RefundResponse;
import com.meridianmart.model.User;
import com.meridianmart.service.OrderService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
public class StaffController {

    private final OrderService orderService;

    @GetMapping("/api/transactions/{receiptNumber}")
    public ResponseEntity<ApiResponse<OrderDto>> getTransaction(
            @AuthenticationPrincipal User user,
            @PathVariable String receiptNumber) {
        OrderDto order = orderService.getOrderByReceiptNumber(receiptNumber, user.getStoreId());
        return ResponseEntity.ok(ApiResponse.success(order));
    }

    @PostMapping("/api/orders/{id}/pos-confirm")
    public ResponseEntity<ApiResponse<OrderDto>> posConfirmOrder(
            @AuthenticationPrincipal User user,
            @PathVariable Long id) {
        OrderDto order = orderService.posConfirmOrder(id, user.getId(), user.getStoreId());
        return ResponseEntity.ok(ApiResponse.success(order));
    }

    @PostMapping("/api/refunds")
    public ResponseEntity<ApiResponse<RefundResponse>> processRefund(
            @Valid @RequestBody RefundRequest request,
            @AuthenticationPrincipal User user,
            HttpServletRequest httpRequest) {
        RefundResponse response = orderService.processRefund(request, user.getId(), user.getStoreId(), httpRequest.getRemoteAddr());
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
