package com.meridianmart.controller;

import com.meridianmart.dto.AddToCartRequest;
import com.meridianmart.dto.ApiResponse;
import com.meridianmart.dto.CartItemDto;
import com.meridianmart.dto.CartResponse;
import com.meridianmart.model.User;
import com.meridianmart.service.CartService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @PostMapping
    public ResponseEntity<ApiResponse<CartItemDto>> addToCart(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody AddToCartRequest request) {
        CartItemDto item = cartService.addToCart(user, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(item));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<CartResponse>> getCart(@AuthenticationPrincipal User user) {
        CartResponse cart = cartService.getCart(user);
        return ResponseEntity.ok(ApiResponse.success(cart));
    }

    @DeleteMapping("/{itemId}")
    public ResponseEntity<ApiResponse<Void>> removeFromCart(
            @AuthenticationPrincipal User user,
            @PathVariable Long itemId) {
        cartService.removeFromCart(user, itemId);
        return ResponseEntity.ok(ApiResponse.success("Item removed from cart", null));
    }
}
