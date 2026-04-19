package com.meridianmart.unit;

import com.meridianmart.audit.AuditService;
import com.meridianmart.model.Order;
import com.meridianmart.model.PaymentTransaction;
import com.meridianmart.model.User;
import com.meridianmart.payment.DistributedLockService;
import com.meridianmart.payment.PaymentService;
import com.meridianmart.repository.OrderRepository;
import com.meridianmart.repository.PaymentTransactionRepository;
import com.meridianmart.security.AesEncryptionService;
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
class PaymentServiceTest {

    @Mock PaymentTransactionRepository paymentTransactionRepository;
    @Mock OrderRepository orderRepository;
    @Mock DistributedLockService lockService;
    @Mock AuditService auditService;
    @Mock AesEncryptionService aesEncryptionService;
    @InjectMocks PaymentService paymentService;

    private Order buildOrder() {
        User user = User.builder().id(1L).username("shopper").build();
        return Order.builder()
                .id(1L).user(user).receiptNumber("RCP-001")
                .totalAmount(BigDecimal.valueOf(100.00))
                .status(Order.OrderStatus.PENDING)
                .idempotencyKey("order-idem")
                .items(List.of())
                .build();
    }

    @Test
    void paymentRecordedCorrectlyFromPosConfirmation() {
        Order order = buildOrder();
        when(paymentTransactionRepository.findByIdempotencyKey("pay-001")).thenReturn(Optional.empty());
        when(lockService.acquireLock(anyString(), anyInt())).thenReturn("lock-id");
        when(aesEncryptionService.encrypt(anyString())).thenReturn("encrypted-token");
        when(paymentTransactionRepository.save(any())).thenAnswer(inv -> {
            PaymentTransaction tx = inv.getArgument(0);
            tx.setId(1L);
            return tx;
        });

        PaymentTransaction result = paymentService.recordPayment(order, BigDecimal.valueOf(100.00), "pay-001");

        assertThat(result.getStatus()).isEqualTo(PaymentTransaction.PaymentStatus.SUCCESS);
        assertThat(result.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(100.00));
        verify(lockService).releaseLock(anyString(), eq("lock-id"));
    }

    @Test
    void duplicateIdempotencyKeyReturnsExistingTransaction() {
        Order order = buildOrder();
        PaymentTransaction existing = PaymentTransaction.builder()
                .id(42L).order(order).amount(BigDecimal.valueOf(100))
                .status(PaymentTransaction.PaymentStatus.SUCCESS)
                .idempotencyKey("dup-key").build();
        when(paymentTransactionRepository.findByIdempotencyKey("dup-key")).thenReturn(Optional.of(existing));

        PaymentTransaction result = paymentService.recordPayment(order, BigDecimal.valueOf(100), "dup-key");

        assertThat(result.getId()).isEqualTo(42L);
        verify(lockService, never()).acquireLock(anyString(), anyInt());
    }

    @Test
    void refundCreatesTransactionAndUpdatesOrderToRefunded() {
        Order order = buildOrder();
        order.setStatus(Order.OrderStatus.COMPLETED);

        when(paymentTransactionRepository.findByIdempotencyKey("refund-001")).thenReturn(Optional.empty());
        when(lockService.acquireLock(anyString(), anyInt())).thenReturn("lock-id");
        when(paymentTransactionRepository.save(any())).thenAnswer(inv -> {
            PaymentTransaction tx = inv.getArgument(0);
            tx.setId(10L);
            return tx;
        });
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PaymentTransaction refund = paymentService.processRefund(order, "refund-001", 2L, "127.0.0.1");

        assertThat(refund.getStatus()).isEqualTo(PaymentTransaction.PaymentStatus.REFUNDED);
        assertThat(order.getStatus()).isEqualTo(Order.OrderStatus.REFUNDED);
        verify(auditService).log(eq(2L), eq("REFUND_PROCESSED"), anyString(), eq("127.0.0.1"));
    }

    @Test
    void concurrentPaymentLockFailReturnsExistingTransactionDeterministically() {
        Order order = buildOrder();
        PaymentTransaction existing = PaymentTransaction.builder()
                .id(99L).order(order).amount(BigDecimal.valueOf(100))
                .status(PaymentTransaction.PaymentStatus.SUCCESS)
                .idempotencyKey("concurrent-key").build();
        // First call (idempotency pre-check) sees nothing; second call (lock-fail fallback) sees completed tx
        when(paymentTransactionRepository.findByIdempotencyKey("concurrent-key"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(existing));
        when(lockService.acquireLock(anyString(), anyInt())).thenReturn(null);

        PaymentTransaction result = paymentService.recordPayment(order, BigDecimal.valueOf(100), "concurrent-key");

        assertThat(result.getId()).isEqualTo(99L);
        verify(paymentTransactionRepository, never()).save(any());
    }

    @Test
    void concurrentPaymentLockFailThrows503WhenNoExistingTransactionAfterRetries() {
        Order order = buildOrder();
        when(paymentTransactionRepository.findByIdempotencyKey("in-flight-key"))
                .thenReturn(Optional.empty());
        when(lockService.acquireLock(anyString(), anyInt())).thenReturn(null);

        assertThatThrownBy(() -> paymentService.recordPayment(order, BigDecimal.valueOf(100), "in-flight-key"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode.value")
                .isEqualTo(503);
    }

    @Test
    void refundThrowsWhenOrderNotCompleted() {
        Order order = buildOrder();
        order.setStatus(Order.OrderStatus.REFUNDED);

        when(paymentTransactionRepository.findByIdempotencyKey("refund-x")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.processRefund(order, "refund-x", 1L, "127.0.0.1"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode.value")
                .isEqualTo(400);
    }

    @Test
    void idempotencyKeyForDifferentOrderReturns409() {
        Order order = buildOrder();
        Order otherOrder = Order.builder().id(99L).user(order.getUser())
                .receiptNumber("RCP-OTHER").totalAmount(BigDecimal.valueOf(50))
                .status(Order.OrderStatus.PENDING).idempotencyKey("other-key").items(List.of()).build();
        PaymentTransaction existingForOtherOrder = PaymentTransaction.builder()
                .id(7L).order(otherOrder).amount(BigDecimal.valueOf(50))
                .status(PaymentTransaction.PaymentStatus.SUCCESS)
                .idempotencyKey("cross-order-key").build();
        when(paymentTransactionRepository.findByIdempotencyKey("cross-order-key"))
                .thenReturn(Optional.of(existingForOtherOrder));

        assertThatThrownBy(() -> paymentService.recordPayment(order, BigDecimal.valueOf(100), "cross-order-key"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(
                        ((org.springframework.web.server.ResponseStatusException) ex).getStatusCode().value()
                ).isEqualTo(409));
    }

    @Test
    void preAuthorizeCreatesPreAuthorizedTransaction() {
        Order order = buildOrder();
        when(paymentTransactionRepository.findByIdempotencyKey("preauth-001")).thenReturn(Optional.empty());
        when(lockService.acquireLock(anyString(), anyInt())).thenReturn("lock-id");
        when(aesEncryptionService.encrypt(anyString())).thenReturn("encrypted-token");
        when(paymentTransactionRepository.save(any())).thenAnswer(inv -> {
            PaymentTransaction tx = inv.getArgument(0);
            tx.setId(5L);
            return tx;
        });

        PaymentTransaction result = paymentService.preAuthorizePayment(order, BigDecimal.valueOf(100), "preauth-001");

        assertThat(result.getStatus()).isEqualTo(PaymentTransaction.PaymentStatus.PRE_AUTHORIZED);
        verify(lockService).releaseLock(anyString(), eq("lock-id"));
    }
}
