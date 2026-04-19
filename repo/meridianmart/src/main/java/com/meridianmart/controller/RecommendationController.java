package com.meridianmart.controller;

import com.meridianmart.dto.ApiResponse;
import com.meridianmart.dto.ProductDto;
import com.meridianmart.model.User;
import com.meridianmart.recommendation.RecommendationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/recommendations")
@RequiredArgsConstructor
public class RecommendationController {

    private final RecommendationService recommendationService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<ProductDto>>> getRecommendations(@AuthenticationPrincipal User user) {
        List<ProductDto> recommendations = recommendationService.getRecommendations(user);
        return ResponseEntity.ok(ApiResponse.success(recommendations));
    }
}
