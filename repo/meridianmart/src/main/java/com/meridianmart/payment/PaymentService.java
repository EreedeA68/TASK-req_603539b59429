package com.meridianmart.payment;

import com.meridianmart.audit.AuditService;
import com.meridianmart.model.Order;
import com.meridianmart.model.PaymentTransaction;
import com.meridianmart.security.AesEncryptionService;
import com.meridianmart.repository.OrderRepository;
import com.meridianmart.repository.PaymentTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentTransactionRepository paymentTransactionRepository;
    private final OrderRepository orderRepository;
    private final DistributedLockService lockService;
    private final AuditService auditService;
    private final AesEncryptionService aesEncryptionService;

    @Transactional
    public PaymentTransaction recordPayment(Order order, BigDecimal amount, String idempotencyKey) {
        java.util.Optional<PaymentTransaction> existing =
                paymentTransactionRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            if (!existing.get().getOrder().getId().equals(order.getId())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Idempotency key belongs to a different order");
            }
            return existing.get();
        }

        String lockName = "payment:" + order.getId();
        String lockId = lockService.acquireLock(lockName, 30);

        if (lockId == null) {
            log.warn("Could not acquire payment lock for order {}; retrying idempotency lookup", order.getId());
            for (int attempt = 0; attempt < 3; attempt++) {
                if (attempt > 0) {
                    try { Thread.sleep(100); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
                }
                java.util.Optional<PaymentTransaction> fallback =
                        paymentTransactionRepository.findByIdempotencyKey(idempotencyKey);
                if (fallback.isPresent()) {
                    if (!fallback.get().getOrder().getId().equals(order.getId())) {
                        throw new ResponseStatusException(HttpStatus.CONFLICT, "Idempotency key belongs to a different order");
                    }
                    return fallback.get();
                }
            }
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Payment operation in progress for this order; please retry");
        }

        try {
            String rawToken = "order:" + order.getId() + "|ts:" + System.currentTimeMillis();
            String encryptedToken = aesEncryptionService.encrypt(rawToken);
            PaymentTransaction transaction = PaymentTransaction.builder()
                    .order(order)
                    .amount(amount)
                    .method("WECHAT_PAY")
                    .status(PaymentTransaction.PaymentStatus.SUCCESS)
                    .idempotencyKey(idempotencyKey)
                    .encryptedPaymentToken(encryptedToken)
                    .build();
            transaction = paymentTransactionRepository.save(transaction);
            log.info("Payment recorded for order {} amount {}", order.getId(), amount);
            return transaction;
        } finally {
            lockService.releaseLock(lockName, lockId);
        }
    }

    @Transactional
    public PaymentTransaction processRefund(Order order, String idempotencyKey, Long staffId, String ipAddress) {
        java.util.Optional<PaymentTransaction> existing =
                paymentTransactionRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            if (!existing.get().getOrder().getId().equals(order.getId())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Idempotency key belongs to a different order");
            }
            return existing.get();
        }

        if (order.getStatus() != Order.OrderStatus.COMPLETED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    order.getStatus() == Order.OrderStatus.REFUNDED
                            ? "Order already refunded"
                            : "Only completed orders can be refunded");
        }

        String lockName = "refund:" + order.getId();
        String lockId = lockService.acquireLock(lockName, 30);
        if (lockId == null) {
            log.warn("Could not acquire refund lock for order {}; retrying idempotency lookup", order.getId());
            for (int attempt = 0; attempt < 3; attempt++) {
                if (attempt > 0) {
                    try { Thread.sleep(100); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
                }
                java.util.Optional<PaymentTransaction> fallback =
                        paymentTransactionRepository.findByIdempotencyKey(idempotencyKey);
                if (fallback.isPresent()) {
                    if (!fallback.get().getOrder().getId().equals(order.getId())) {
                        throw new ResponseStatusException(HttpStatus.CONFLICT, "Idempotency key belongs to a different order");
                    }
                    return fallback.get();
                }
            }
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Refund operation in progress for this order; please retry");
        }

        try {
            PaymentTransaction refund = PaymentTransaction.builder()
                    .order(order)
                    .amount(order.getTotalAmount())
                    .method("WECHAT_PAY")
                    .status(PaymentTransaction.PaymentStatus.REFUNDED)
                    .idempotencyKey(idempotencyKey)
                    .build();
            refund = paymentTransactionRepository.save(refund);

            order.setStatus(Order.OrderStatus.REFUNDED);
            orderRepository.save(order);

            auditService.log(staffId, "REFUND_PROCESSED",
                    "Refund for order " + order.getReceiptNumber() + " amount: " + order.getTotalAmount(),
                    ipAddress);

            log.info("Refund processed for order {} by staff {}", order.getId(), staffId);
            return refund;
        } finally {
            lockService.releaseLock(lockName, lockId);
        }
    }

    @Transactional
    public PaymentTransaction initiateDeposit(Order order, BigDecimal amount, String idempotencyKey) {
        java.util.Optional<PaymentTransaction> existing =
                paymentTransactionRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            if (!existing.get().getOrder().getId().equals(order.getId())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Idempotency key belongs to a different order");
            }
            return existing.get();
        }

        String lockName = "deposit:" + order.getId();
        String lockId = lockService.acquireLock(lockName, 30);
        if (lockId == null) {
            log.warn("Could not acquire deposit lock for order {}; retrying idempotency lookup", order.getId());
            for (int attempt = 0; attempt < 3; attempt++) {
                if (attempt > 0) {
                    try { Thread.sleep(100); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
                }
                java.util.Optional<PaymentTransaction> fallback =
                        paymentTransactionRepository.findByIdempotencyKey(idempotencyKey);
                if (fallback.isPresent()) {
                    if (!fallback.get().getOrder().getId().equals(order.getId())) {
                        throw new ResponseStatusException(HttpStatus.CONFLICT, "Idempotency key belongs to a different order");
                    }
                    return fallback.get();
                }
            }
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Deposit operation in progress for this order; please retry");
        }

        try {
            PaymentTransaction deposit = PaymentTransaction.builder()
                    .order(order)
                    .amount(amount)
                    .method("WECHAT_PAY")
                    .status(PaymentTransaction.PaymentStatus.DEPOSIT)
                    .idempotencyKey(idempotencyKey)
                    .build();
            deposit = paymentTransactionRepository.save(deposit);
            log.info("Deposit initiated for order {} amount {}", order.getId(), amount);
            return deposit;
        } finally {
            lockService.releaseLock(lockName, lockId);
        }
    }

    @Transactional
    public PaymentTransaction capturePayment(Order order, String idempotencyKey) {
        java.util.Optional<PaymentTransaction> existing =
                paymentTransactionRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            if (!existing.get().getOrder().getId().equals(order.getId())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Idempotency key belongs to a different order");
            }
            return existing.get();
        }

        String lockName = "capture:" + order.getId();
        String lockId = lockService.acquireLock(lockName, 30);
        if (lockId == null) {
            log.warn("Could not acquire capture lock for order {}; retrying idempotency lookup", order.getId());
            for (int attempt = 0; attempt < 3; attempt++) {
                if (attempt > 0) {
                    try { Thread.sleep(100); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
                }
                java.util.Optional<PaymentTransaction> fallback =
                        paymentTransactionRepository.findByIdempotencyKey(idempotencyKey);
                if (fallback.isPresent()) {
                    if (!fallback.get().getOrder().getId().equals(order.getId())) {
                        throw new ResponseStatusException(HttpStatus.CONFLICT, "Idempotency key belongs to a different order");
                    }
                    return fallback.get();
                }
            }
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Capture operation in progress for this order; please retry");
        }

        try {
            String rawToken = "order:" + order.getId() + "|ts:" + System.currentTimeMillis();
            String encryptedToken = aesEncryptionService.encrypt(rawToken);
            PaymentTransaction capture = PaymentTransaction.builder()
                    .order(order)
                    .amount(order.getTotalAmount())
                    .method("WECHAT_PAY")
                    .status(PaymentTransaction.PaymentStatus.SUCCESS)
                    .idempotencyKey(idempotencyKey)
                    .encryptedPaymentToken(encryptedToken)
                    .build();
            capture = paymentTransactionRepository.save(capture);
            log.info("Payment captured for order {} amount {}", order.getId(), order.getTotalAmount());
            return capture;
        } finally {
            lockService.releaseLock(lockName, lockId);
        }
    }

    @Transactional
    public PaymentTransaction compensateFailedPayment(Order order, String idempotencyKey) {
        java.util.Optional<PaymentTransaction> existing =
                paymentTransactionRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            if (!existing.get().getOrder().getId().equals(order.getId())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Idempotency key belongs to a different order");
            }
            return existing.get();
        }

        String lockName = "compensate:" + order.getId();
        String lockId = lockService.acquireLock(lockName, 30);
        if (lockId == null) {
            log.warn("Could not acquire compensate lock for order {}; retrying idempotency lookup", order.getId());
            for (int attempt = 0; attempt < 3; attempt++) {
                if (attempt > 0) {
                    try { Thread.sleep(100); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
                }
                java.util.Optional<PaymentTransaction> fallback =
                        paymentTransactionRepository.findByIdempotencyKey(idempotencyKey);
                if (fallback.isPresent()) {
                    if (!fallback.get().getOrder().getId().equals(order.getId())) {
                        throw new ResponseStatusException(HttpStatus.CONFLICT, "Idempotency key belongs to a different order");
                    }
                    return fallback.get();
                }
            }
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Compensation operation in progress for this order; please retry");
        }

        try {
            PaymentTransaction compensation = PaymentTransaction.builder()
                    .order(order)
                    .amount(order.getTotalAmount())
                    .method("WECHAT_PAY")
                    .status(PaymentTransaction.PaymentStatus.FAILED)
                    .idempotencyKey(idempotencyKey)
                    .build();
            compensation = paymentTransactionRepository.save(compensation);
            order.setStatus(Order.OrderStatus.REFUNDED);
            orderRepository.save(order);
            log.warn("Payment compensation recorded for order {}", order.getId());
            return compensation;
        } finally {
            lockService.releaseLock(lockName, lockId);
        }
    }

    @Transactional
    public PaymentTransaction preAuthorizePayment(Order order, BigDecimal amount, String idempotencyKey) {
        java.util.Optional<PaymentTransaction> existing =
                paymentTransactionRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            if (!existing.get().getOrder().getId().equals(order.getId())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Idempotency key belongs to a different order");
            }
            return existing.get();
        }

        String lockName = "preauth:" + order.getId();
        String lockId = lockService.acquireLock(lockName, 30);
        if (lockId == null) {
            log.warn("Could not acquire pre-auth lock for order {}; retrying idempotency lookup", order.getId());
            for (int attempt = 0; attempt < 3; attempt++) {
                if (attempt > 0) {
                    try { Thread.sleep(100); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
                }
                java.util.Optional<PaymentTransaction> fallback =
                        paymentTransactionRepository.findByIdempotencyKey(idempotencyKey);
                if (fallback.isPresent()) {
                    if (!fallback.get().getOrder().getId().equals(order.getId())) {
                        throw new ResponseStatusException(HttpStatus.CONFLICT, "Idempotency key belongs to a different order");
                    }
                    return fallback.get();
                }
            }
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Pre-authorization operation in progress for this order; please retry");
        }

        try {
            String rawToken = "order:" + order.getId() + "|preauth|ts:" + System.currentTimeMillis();
            String encryptedToken = aesEncryptionService.encrypt(rawToken);
            PaymentTransaction preAuth = PaymentTransaction.builder()
                    .order(order)
                    .amount(amount)
                    .method("WECHAT_PAY")
                    .status(PaymentTransaction.PaymentStatus.PRE_AUTHORIZED)
                    .idempotencyKey(idempotencyKey)
                    .encryptedPaymentToken(encryptedToken)
                    .build();
            preAuth = paymentTransactionRepository.save(preAuth);
            log.info("Pre-authorization created for order {} amount {}", order.getId(), amount);
            return preAuth;
        } finally {
            lockService.releaseLock(lockName, lockId);
        }
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getDailyReconciliation() {
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime endOfDay = startOfDay.plusDays(1);

        List<PaymentTransaction> transactions = paymentTransactionRepository
                .findByCreatedAtBetween(startOfDay, endOfDay);

        long successCount = transactions.stream()
                .filter(t -> t.getStatus() == PaymentTransaction.PaymentStatus.SUCCESS).count();
        long refundCount = transactions.stream()
                .filter(t -> t.getStatus() == PaymentTransaction.PaymentStatus.REFUNDED).count();
        long failedCount = transactions.stream()
                .filter(t -> t.getStatus() == PaymentTransaction.PaymentStatus.FAILED).count();

        BigDecimal totalRevenue = transactions.stream()
                .filter(t -> t.getStatus() == PaymentTransaction.PaymentStatus.SUCCESS)
                .map(PaymentTransaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalRefunded = transactions.stream()
                .filter(t -> t.getStatus() == PaymentTransaction.PaymentStatus.REFUNDED)
                .map(PaymentTransaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return Map.of(
                "date", LocalDate.now().toString(),
                "successfulTransactions", successCount,
                "refundedTransactions", refundCount,
                "failedTransactions", failedCount,
                "totalRevenue", totalRevenue,
                "totalRefunded", totalRefunded,
                "netRevenue", totalRevenue.subtract(totalRefunded)
        );
    }
}
