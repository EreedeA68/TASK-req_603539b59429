package com.meridianmart.controller;

import com.meridianmart.dto.ApiResponse;
import com.meridianmart.dto.OrderDto;
import com.meridianmart.model.User;
import com.meridianmart.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PreAuthorize("hasRole('SHOPPER')")
    @PostMapping
    public ResponseEntity<ApiResponse<OrderDto>> checkout(
            @AuthenticationPrincipal User user,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        OrderDto order = orderService.checkout(user, idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(order));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<OrderDto>>> getOrderHistory(@AuthenticationPrincipal User user) {
        List<OrderDto> orders = orderService.getOrderHistory(user.getId(), user.getStoreId());
        return ResponseEntity.ok(ApiResponse.success(orders));
    }

    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    @PutMapping("/{id}/ready-for-pickup")
    public ResponseEntity<ApiResponse<OrderDto>> markReadyForPickup(
            @AuthenticationPrincipal User user,
            @PathVariable Long id) {
        OrderDto order = orderService.markReadyForPickup(id, user.getId(), user.getStoreId());
        return ResponseEntity.ok(ApiResponse.success(order));
    }
}
