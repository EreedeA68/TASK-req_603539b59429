package com.meridianmart.controller;

import com.meridianmart.dto.AddFavoriteRequest;
import com.meridianmart.dto.ApiResponse;
import com.meridianmart.dto.FavoriteDto;
import com.meridianmart.model.User;
import com.meridianmart.service.FavoriteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/favorites")
@RequiredArgsConstructor
public class FavoriteController {

    private final FavoriteService favoriteService;

    @PostMapping
    public ResponseEntity<ApiResponse<FavoriteDto>> addFavorite(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody AddFavoriteRequest body) {
        FavoriteDto favorite = favoriteService.addFavorite(user, body.getProductId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(favorite));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<FavoriteDto>>> getFavorites(@AuthenticationPrincipal User user) {
        List<FavoriteDto> favorites = favoriteService.getFavorites(user.getId());
        return ResponseEntity.ok(ApiResponse.success(favorites));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> removeFavorite(
            @AuthenticationPrincipal User user,
            @PathVariable Long id) {
        favoriteService.removeFavorite(user, id);
        return ResponseEntity.ok(ApiResponse.success("Favorite removed", null));
    }
}
