package com.meridianmart.service;

import com.meridianmart.dto.BehaviorEventRequest;
import com.meridianmart.model.BehaviorEvent;
import com.meridianmart.model.Product;
import com.meridianmart.model.User;
import com.meridianmart.repository.BehaviorEventRepository;
import com.meridianmart.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@Service
@RequiredArgsConstructor
public class BehaviorEventService {

    private final BehaviorEventRepository behaviorEventRepository;
    private final ProductRepository productRepository;

    @Transactional
    public void recordEvent(User user, BehaviorEventRequest request) {
        Product product = productRepository.findByIdAndStoreId(request.getProductId(), user.getStoreId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));

        BehaviorEvent.EventType eventType;
        try {
            eventType = BehaviorEvent.EventType.valueOf(request.getEventType().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid event type: " + request.getEventType());
        }

        BehaviorEvent event = BehaviorEvent.builder()
                .user(user)
                .product(product)
                .eventType(eventType)
                .build();
        behaviorEventRepository.save(event);
        log.info("Recorded {} event for user {} on product {}", eventType, user.getId(), product.getId());
    }

    @Transactional
    public void recordEvent(User user, Long productId, BehaviorEvent.EventType eventType) {
        Product product = productRepository.findByIdAndStoreId(productId, user.getStoreId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));

        BehaviorEvent event = BehaviorEvent.builder()
                .user(user)
                .product(product)
                .eventType(eventType)
                .build();
        behaviorEventRepository.save(event);
    }
}
