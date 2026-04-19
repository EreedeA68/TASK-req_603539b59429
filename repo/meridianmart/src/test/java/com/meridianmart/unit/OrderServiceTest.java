package com.meridianmart.unit;

import com.meridianmart.dto.OrderDto;
import com.meridianmart.model.*;
import com.meridianmart.payment.DistributedLockService;
import com.meridianmart.payment.PaymentService;
import com.meridianmart.repository.*;
import com.meridianmart.service.NotificationService;
import com.meridianmart.service.OrderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock OrderRepository orderRepository;
    @Mock CartItemRepository cartItemRepository;
    @Mock ProductRepository productRepository;
    @Mock PaymentService paymentService;
    @Mock NotificationService notificationService;
    @Mock PaymentTransactionRepository paymentTransactionRepository;
    @Mock BehaviorEventRepository behaviorEventRepository;
    @Mock DistributedLockService lockService;
    @InjectMocks OrderService orderService;

    private User buildUser() {
        return User.builder().id(1L).username("shopper").role(User.Role.SHOPPER).storeId("STORE_001").build();
    }

    private Product buildProduct(Long id) {
        return Product.builder().id(id).name("Product " + id)
                .price(BigDecimal.valueOf(50.00)).stockQuantity(10).category("Electronics").build();
    }

    @Test
    void checkoutCreatesOrderPendingAtRegister() {
        User user = buildUser();
        Product product = buildProduct(1L);
        CartItem cartItem = CartItem.builder().id(1L).user(user).product(product).quantity(2).build();

        when(cartItemRepository.findByUser(user)).thenReturn(List.of(cartItem));
        when(orderRepository.findByIdempotencyKey("idem-001")).thenReturn(Optional.empty());
        when(lockService.acquireLock(anyString(), anyInt())).thenReturn("lock-id");
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(orderRepository.save(any())).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            if (o.getId() == null) o.setId(1L);
            return o;
        });

        OrderDto result = orderService.checkout(user, "idem-001");

        assertThat(result).isNotNull();
        assertThat(result.getTotalAmount()).isEqualByComparingTo(BigDecimal.valueOf(100.00));
        assertThat(result.getStatus()).isEqualTo(Order.OrderStatus.PENDING_AT_REGISTER.name());
        verifyNoInteractions(paymentService);
    }

    @Test
    void posConfirmCapturesPaymentAndCompletesOrder() {
        User user = buildUser();
        Product product = buildProduct(1L);
        Order order = Order.builder()
                .id(1L).user(user).receiptNumber("RCP-001")
                .totalAmount(BigDecimal.valueOf(50)).status(Order.OrderStatus.PENDING_AT_REGISTER)
                .storeId("STORE_001").idempotencyKey("idem-001")
                .items(List.of(OrderItem.builder().product(product).quantity(1)
                        .unitPrice(BigDecimal.valueOf(50)).build()))
                .build();

        when(orderRepository.findByIdAndStoreId(1L, "STORE_001")).thenReturn(Optional.of(order));
        when(paymentService.initiateDeposit(any(), any(), any()))
                .thenReturn(PaymentTransaction.builder().id(1L).build());
        when(paymentService.capturePayment(any(), any()))
                .thenReturn(PaymentTransaction.builder().id(2L).build());
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        OrderDto result = orderService.posConfirmOrder(1L, 99L, "STORE_001");

        assertThat(result.getStatus()).isEqualTo(Order.OrderStatus.COMPLETED.name());
        verify(paymentService).initiateDeposit(any(), any(), any());
        verify(paymentService).capturePayment(any(), any());
        verify(behaviorEventRepository).save(any());
    }

    @Test
    void receiptNumberIsUniquePerOrder() {
        User user = buildUser();
        Product product = buildProduct(1L);
        CartItem cartItem = CartItem.builder().id(1L).user(user).product(product).quantity(1).build();

        when(cartItemRepository.findByUser(user)).thenReturn(List.of(cartItem));
        when(orderRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        when(lockService.acquireLock(anyString(), anyInt())).thenReturn("lock-id");
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(orderRepository.save(any())).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            if (o.getId() == null) o.setId((long)(Math.random() * 1000));
            return o;
        });

        OrderDto r1 = orderService.checkout(user, "key-1");
        CartItem ci2 = CartItem.builder().id(2L).user(user).product(buildProduct(2L)).quantity(1).build();
        when(cartItemRepository.findByUser(user)).thenReturn(List.of(ci2));
        OrderDto r2 = orderService.checkout(user, "key-2");

        assertThat(r1.getReceiptNumber()).isNotEqualTo(r2.getReceiptNumber());
    }

    @Test
    void timestampFormattedIn12HourTime() {
        User user = buildUser();
        Product product = buildProduct(1L);
        CartItem cartItem = CartItem.builder().id(1L).user(user).product(product).quantity(1).build();

        when(cartItemRepository.findByUser(user)).thenReturn(List.of(cartItem));
        when(orderRepository.findByIdempotencyKey("idem-ts")).thenReturn(Optional.empty());
        when(lockService.acquireLock(anyString(), anyInt())).thenReturn("lock-id");
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(orderRepository.save(any())).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            if (o.getId() == null) o.setId(1L);
            return o;
        });

        OrderDto result = orderService.checkout(user, "idem-ts");

        assertThat(result.getTransactionTimestamp()).matches(".*(?:AM|PM).*");
    }

    @Test
    void duplicateIdempotencyKeyReturnsExistingOrder() {
        User user = buildUser();
        Order existingOrder = Order.builder()
                .id(5L)
                .receiptNumber("RCP-EXISTING")
                .totalAmount(BigDecimal.valueOf(100))
                .status(Order.OrderStatus.PENDING_AT_REGISTER)
                .transactionTimestamp(LocalDateTime.now())
                .idempotencyKey("dup-key")
                .user(user)
                .items(List.of())
                .build();

        when(orderRepository.findByIdempotencyKey("dup-key")).thenReturn(Optional.of(existingOrder));

        OrderDto result = orderService.checkout(user, "dup-key");

        assertThat(result.getReceiptNumber()).isEqualTo("RCP-EXISTING");
        verify(cartItemRepository, never()).findByUser(any());
    }
}
