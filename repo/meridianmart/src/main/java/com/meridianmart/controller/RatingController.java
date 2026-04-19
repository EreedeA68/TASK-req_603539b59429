package com.meridianmart.controller;

import com.meridianmart.dto.ApiResponse;
import com.meridianmart.dto.RatingDto;
import com.meridianmart.dto.RatingRequest;
import com.meridianmart.model.User;
import com.meridianmart.service.RatingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ratings")
@RequiredArgsConstructor
public class RatingController {

    private final RatingService ratingService;

    @PostMapping
    public ResponseEntity<ApiResponse<RatingDto>> rateProduct(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody RatingRequest request) {
        RatingDto rating = ratingService.rateProduct(user, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(rating));
    }
}
