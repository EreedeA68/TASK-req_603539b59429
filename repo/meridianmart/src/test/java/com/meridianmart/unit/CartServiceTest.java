package com.meridianmart.unit;

import com.meridianmart.dto.AddToCartRequest;
import com.meridianmart.dto.CartItemDto;
import com.meridianmart.dto.CartResponse;
import com.meridianmart.model.CartItem;
import com.meridianmart.model.Product;
import com.meridianmart.model.User;
import com.meridianmart.repository.CartItemRepository;
import com.meridianmart.repository.ProductRepository;
import com.meridianmart.service.CartService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    @Mock CartItemRepository cartItemRepository;
    @Mock ProductRepository productRepository;
    @InjectMocks CartService cartService;

    private User buildUser() {
        return User.builder().id(1L).username("shopper").role(User.Role.SHOPPER).storeId("STORE_001").build();
    }

    private Product buildProduct(Long id, int stock, double price) {
        return Product.builder().id(id).name("Product " + id).price(BigDecimal.valueOf(price))
                .stockQuantity(stock).category("Electronics").build();
    }

    @Test
    void addToCartPersistsCorrectUserIdProductIdQuantity() {
        User user = buildUser();
        Product product = buildProduct(1L, 10, 50.00);
        AddToCartRequest req = new AddToCartRequest();
        req.setProductId(1L);
        req.setQuantity(2);

        when(productRepository.findByIdAndStoreId(eq(1L), anyString())).thenReturn(Optional.of(product));
        when(cartItemRepository.findByUserIdAndProductId(1L, 1L)).thenReturn(Optional.empty());
        when(cartItemRepository.save(any())).thenAnswer(inv -> {
            CartItem ci = inv.getArgument(0);
            ci.setId(100L);
            return ci;
        });

        CartItemDto result = cartService.addToCart(user, req);

        assertThat(result.getProductId()).isEqualTo(1L);
        assertThat(result.getQuantity()).isEqualTo(2);
        assertThat(result.getUnitPrice()).isEqualTo(BigDecimal.valueOf(50.00));
    }

    @Test
    void addingSameProductIncreasesQuantity() {
        User user = buildUser();
        Product product = buildProduct(1L, 10, 50.00);
        AddToCartRequest req = new AddToCartRequest();
        req.setProductId(1L);
        req.setQuantity(3);

        CartItem existing = CartItem.builder().id(1L).user(user).product(product).quantity(2).build();

        when(productRepository.findByIdAndStoreId(eq(1L), anyString())).thenReturn(Optional.of(product));
        when(cartItemRepository.findByUserIdAndProductId(1L, 1L)).thenReturn(Optional.of(existing));
        when(cartItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CartItemDto result = cartService.addToCart(user, req);
        assertThat(result.getQuantity()).isEqualTo(5);
    }

    @Test
    void removeFromCartDeletesItem() {
        User user = buildUser();
        when(cartItemRepository.existsByUserIdAndId(1L, 99L)).thenReturn(true);

        cartService.removeFromCart(user, 99L);
        verify(cartItemRepository).deleteById(99L);
    }

    @Test
    void removeFromCartThrowsWhenItemNotFound() {
        User user = buildUser();
        when(cartItemRepository.existsByUserIdAndId(1L, 999L)).thenReturn(false);

        assertThatThrownBy(() -> cartService.removeFromCart(user, 999L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode")
                .satisfies(code -> assertThat(code.value()).isEqualTo(404));
    }

    @Test
    void cartTotalCalculatesCorrectlyAcrossMultipleItems() {
        User user = buildUser();
        Product p1 = buildProduct(1L, 5, 10.00);
        Product p2 = buildProduct(2L, 5, 25.00);

        CartItem ci1 = CartItem.builder().id(1L).user(user).product(p1).quantity(3).build();
        CartItem ci2 = CartItem.builder().id(2L).user(user).product(p2).quantity(2).build();

        when(cartItemRepository.findByUser(user)).thenReturn(List.of(ci1, ci2));

        CartResponse response = cartService.getCart(user);
        // 3×10 + 2×25 = 30 + 50 = 80
        assertThat(response.getTotalPrice()).isEqualByComparingTo(BigDecimal.valueOf(80.00));
        assertThat(response.getItems()).hasSize(2);
    }

    @Test
    void addToCartThrowsWhenProductOutOfStock() {
        User user = buildUser();
        Product product = buildProduct(1L, 0, 50.00);
        AddToCartRequest req = new AddToCartRequest();
        req.setProductId(1L);

        when(productRepository.findByIdAndStoreId(eq(1L), anyString())).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> cartService.addToCart(user, req))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode")
                .satisfies(code -> assertThat(code.value()).isEqualTo(400));
    }
}
