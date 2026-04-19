package com.meridianmart.controller;

import com.meridianmart.dto.ApiResponse;
import com.meridianmart.dto.PagedResponse;
import com.meridianmart.dto.ProductDto;
import com.meridianmart.model.User;
import com.meridianmart.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<ProductDto>>> getCatalog(
            @AuthenticationPrincipal User user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PagedResponse<ProductDto> catalog = productService.getCatalog(user.getStoreId(), page, size);
        return ResponseEntity.ok(ApiResponse.success(catalog));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductDto>> getProduct(
            @AuthenticationPrincipal User user,
            @PathVariable Long id) {
        ProductDto product = productService.getProductById(id, user.getStoreId());
        productService.recordViewEvent(user, id);
        return ResponseEntity.ok(ApiResponse.success(product));
    }
}
