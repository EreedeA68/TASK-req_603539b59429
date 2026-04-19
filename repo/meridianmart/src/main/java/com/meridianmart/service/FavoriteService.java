package com.meridianmart.service;

import com.meridianmart.dto.FavoriteDto;
import com.meridianmart.model.BehaviorEvent;
import com.meridianmart.model.Favorite;
import com.meridianmart.model.Product;
import com.meridianmart.model.User;
import com.meridianmart.repository.BehaviorEventRepository;
import com.meridianmart.repository.FavoriteRepository;
import com.meridianmart.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FavoriteService {

    private final FavoriteRepository favoriteRepository;
    private final ProductRepository productRepository;
    private final BehaviorEventRepository behaviorEventRepository;

    @Transactional
    public FavoriteDto addFavorite(User user, Long productId) {
        Product product = productRepository.findByIdAndStoreId(productId, user.getStoreId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));

        if (favoriteRepository.existsByUserIdAndProductId(user.getId(), productId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Product already in favorites");
        }

        Favorite favorite = Favorite.builder()
                .user(user)
                .product(product)
                .build();
        favorite = favoriteRepository.save(favorite);
        behaviorEventRepository.save(BehaviorEvent.builder()
                .user(user).product(product).eventType(BehaviorEvent.EventType.FAVORITE).build());
        log.info("User {} added product {} to favorites", user.getId(), productId);
        return toDto(favorite);
    }

    @Transactional(readOnly = true)
    public List<FavoriteDto> getFavorites(Long userId) {
        return favoriteRepository.findByUserId(userId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public void removeFavorite(User user, Long favoriteId) {
        if (!favoriteRepository.existsByUserIdAndId(user.getId(), favoriteId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Favorite not found");
        }
        favoriteRepository.deleteById(favoriteId);
        log.info("User {} removed favorite {}", user.getId(), favoriteId);
    }

    private FavoriteDto toDto(Favorite f) {
        Product p = f.getProduct();
        return FavoriteDto.builder()
                .id(f.getId())
                .productId(p.getId())
                .productName(p.getName())
                .imageUrl(p.getImageUrl())
                .price(p.getPrice())
                .createdAt(f.getCreatedAt() != null ? f.getCreatedAt().toString() : null)
                .build();
    }
}
