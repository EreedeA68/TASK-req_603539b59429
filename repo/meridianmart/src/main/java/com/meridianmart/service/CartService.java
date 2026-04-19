package com.meridianmart.service;

import com.meridianmart.dto.AddToCartRequest;
import com.meridianmart.dto.CartItemDto;
import com.meridianmart.dto.CartResponse;
import com.meridianmart.model.BehaviorEvent;
import com.meridianmart.model.CartItem;
import com.meridianmart.model.Product;
import com.meridianmart.model.User;
import com.meridianmart.repository.BehaviorEventRepository;
import com.meridianmart.repository.CartItemRepository;
import com.meridianmart.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CartService {

    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final BehaviorEventRepository behaviorEventRepository;

    @Transactional
    public CartItemDto addToCart(User user, AddToCartRequest request) {
        Product product = productRepository.findByIdAndStoreId(request.getProductId(), user.getStoreId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));

        if (product.getStockQuantity() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Product is out of stock");
        }

        Optional<CartItem> existingItem = cartItemRepository.findByUserIdAndProductId(user.getId(), product.getId());

        CartItem cartItem;
        if (existingItem.isPresent()) {
            cartItem = existingItem.get();
            cartItem.setQuantity(cartItem.getQuantity() + request.getQuantity());
        } else {
            cartItem = CartItem.builder()
                    .user(user)
                    .product(product)
                    .quantity(request.getQuantity())
                    .build();
        }
        cartItem = cartItemRepository.save(cartItem);
        behaviorEventRepository.save(BehaviorEvent.builder()
                .user(user).product(product).eventType(BehaviorEvent.EventType.ADD_TO_CART).build());
        log.info("Added product {} to cart for user {}", product.getId(), user.getId());
        return toDto(cartItem);
    }

    @Transactional(readOnly = true)
    public CartResponse getCart(User user) {
        List<CartItem> items = cartItemRepository.findByUser(user);
        List<CartItemDto> dtos = items.stream().map(this::toDto).collect(Collectors.toList());
        BigDecimal total = dtos.stream()
                .map(CartItemDto::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return CartResponse.builder()
                .items(dtos)
                .totalPrice(total)
                .itemCount(dtos.size())
                .build();
    }

    @Transactional
    public void removeFromCart(User user, Long itemId) {
        if (!cartItemRepository.existsByUserIdAndId(user.getId(), itemId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Cart item not found");
        }
        cartItemRepository.deleteById(itemId);
        log.info("Removed cart item {} for user {}", itemId, user.getId());
    }

    @Transactional
    public void clearCart(Long userId) {
        cartItemRepository.deleteByUserId(userId);
    }

    @Transactional(readOnly = true)
    public List<CartItem> getCartItems(Long userId) {
        return cartItemRepository.findByUserId(userId);
    }

    private CartItemDto toDto(CartItem item) {
        Product p = item.getProduct();
        BigDecimal subtotal = p.getPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
        return CartItemDto.builder()
                .id(item.getId())
                .productId(p.getId())
                .productName(p.getName())
                .imageUrl(p.getImageUrl())
                .quantity(item.getQuantity())
                .unitPrice(p.getPrice())
                .subtotal(subtotal)
                .stockWarning(p.getStockQuantity() < 2)
                .stockQuantity(p.getStockQuantity())
                .build();
    }
}
