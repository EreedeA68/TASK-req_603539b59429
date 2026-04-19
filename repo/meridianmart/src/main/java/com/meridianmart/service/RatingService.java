package com.meridianmart.service;

import com.meridianmart.dto.RatingDto;
import com.meridianmart.dto.RatingRequest;
import com.meridianmart.model.BehaviorEvent;
import com.meridianmart.model.Order;
import com.meridianmart.model.Product;
import com.meridianmart.model.Rating;
import com.meridianmart.model.User;
import com.meridianmart.repository.BehaviorEventRepository;
import com.meridianmart.repository.OrderRepository;
import com.meridianmart.repository.ProductRepository;
import com.meridianmart.repository.RatingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RatingService {

    private final RatingRepository ratingRepository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final BehaviorEventRepository behaviorEventRepository;

    @Transactional
    public RatingDto rateProduct(User user, RatingRequest request) {
        Product product = productRepository.findByIdAndStoreId(request.getProductId(), user.getStoreId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));

        List<Order> completedOrders = orderRepository.findCompletedByUserId(user.getId());
        boolean hasPurchased = completedOrders.stream()
                .anyMatch(order -> order.getItems().stream()
                        .anyMatch(item -> item.getProduct().getId().equals(product.getId())));

        if (!hasPurchased) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "You can only rate products you have purchased");
        }

        Rating rating;
        if (ratingRepository.existsByUserIdAndProductId(user.getId(), product.getId())) {
            rating = ratingRepository.findByUserIdAndProductId(user.getId(), product.getId()).get();
            rating.setScore(request.getScore());
        } else {
            rating = Rating.builder()
                    .user(user)
                    .product(product)
                    .score(request.getScore())
                    .build();
        }
        rating = ratingRepository.save(rating);
        behaviorEventRepository.save(BehaviorEvent.builder()
                .user(user).product(product).eventType(BehaviorEvent.EventType.RATING).build());
        log.info("User {} rated product {} with score {}", user.getId(), product.getId(), request.getScore());

        return RatingDto.builder()
                .ratingId(rating.getId())
                .productId(product.getId())
                .score(rating.getScore())
                .createdAt(rating.getCreatedAt() != null ? rating.getCreatedAt().toString() : null)
                .build();
    }
}
