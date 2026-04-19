package com.meridianmart.service;

import com.meridianmart.dto.OrderDto;
import com.meridianmart.dto.OrderItemDto;
import com.meridianmart.dto.RefundRequest;
import com.meridianmart.dto.RefundResponse;
import com.meridianmart.model.*;
import com.meridianmart.payment.DistributedLockService;
import com.meridianmart.payment.PaymentService;
import com.meridianmart.repository.BehaviorEventRepository;
import com.meridianmart.repository.CartItemRepository;
import com.meridianmart.repository.OrderRepository;
import com.meridianmart.repository.PaymentTransactionRepository;
import com.meridianmart.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final PaymentService paymentService;
    private final NotificationService notificationService;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final BehaviorEventRepository behaviorEventRepository;
    private final DistributedLockService lockService;

    private static final DateTimeFormatter RECEIPT_TS_FORMAT =
            DateTimeFormatter.ofPattern("h:mm a");

    @Transactional
    public OrderDto checkout(User user, String idempotencyKey) {
        Optional<Order> existingOrder = orderRepository.findByIdempotencyKey(idempotencyKey);
        if (existingOrder.isPresent()) {
            if (!existingOrder.get().getUser().getId().equals(user.getId())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Idempotency key conflict: key belongs to a different user");
            }
            log.info("Returning existing order for idempotency key: {}", idempotencyKey);
            return toDto(existingOrder.get());
        }

        // Acquire distributed lock to prevent concurrent duplicate creates for the same key
        String lockId = lockService.acquireLock("checkout:" + idempotencyKey, 30);
        if (lockId == null) {
            log.warn("Could not acquire checkout lock for key {}; retrying idempotency lookup", idempotencyKey);
            for (int attempt = 0; attempt < 3; attempt++) {
                if (attempt > 0) {
                    try { Thread.sleep(100); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
                }
                java.util.Optional<Order> fallback = orderRepository.findByIdempotencyKey(idempotencyKey);
                if (fallback.isPresent()) {
                    Order o = fallback.get();
                    if (!o.getUser().getId().equals(user.getId())) {
                        throw new ResponseStatusException(HttpStatus.CONFLICT,
                                "Idempotency key conflict: key belongs to a different user");
                    }
                    return toDto(o);
                }
            }
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Checkout already in progress; please retry");
        }

        try {
            // Double-check inside lock: another request may have committed while we were waiting
            Optional<Order> postLockCheck = orderRepository.findByIdempotencyKey(idempotencyKey);
            if (postLockCheck.isPresent()) {
                Order found = postLockCheck.get();
                if (!found.getUser().getId().equals(user.getId())) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "Idempotency key conflict: key belongs to a different user");
                }
                return toDto(found);
            }

        List<CartItem> cartItems = cartItemRepository.findByUser(user);
        if (cartItems.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cart is empty");
        }

        BigDecimal total = BigDecimal.ZERO;
        List<OrderItem> orderItems = new ArrayList<>();

        for (CartItem cartItem : cartItems) {
            Product product = cartItem.getProduct();
            if (product.getStockQuantity() < cartItem.getQuantity()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Insufficient stock for: " + product.getName());
            }
            product.setStockQuantity(product.getStockQuantity() - cartItem.getQuantity());
            productRepository.save(product);

            BigDecimal itemTotal = product.getPrice().multiply(BigDecimal.valueOf(cartItem.getQuantity()));
            total = total.add(itemTotal);

            OrderItem orderItem = OrderItem.builder()
                    .product(product)
                    .quantity(cartItem.getQuantity())
                    .unitPrice(product.getPrice())
                    .build();
            orderItems.add(orderItem);
        }

        String receiptNumber = generateReceiptNumber();
        Order order = Order.builder()
                .user(user)
                .receiptNumber(receiptNumber)
                .totalAmount(total)
                .status(Order.OrderStatus.PENDING_AT_REGISTER)
                .transactionTimestamp(LocalDateTime.now())
                .idempotencyKey(idempotencyKey)
                .storeId(user.getStoreId())
                .items(new ArrayList<>())
                .build();

        order = orderRepository.save(order);

        for (OrderItem item : orderItems) {
            item.setOrder(order);
        }
        order.getItems().addAll(orderItems);
        order = orderRepository.save(order);

        cartItemRepository.deleteByUserId(user.getId());

        log.info("Order {} created for user {} total {} — awaiting POS confirmation",
                receiptNumber, user.getId(), total);
        return toDto(order);
        } finally {
            lockService.releaseLock("checkout:" + idempotencyKey, lockId);
        }
    }

    @Transactional
    public OrderDto posConfirmOrder(Long orderId, Long staffId, String storeId) {
        Order order = orderRepository.findByIdAndStoreId(orderId, storeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));

        if (order.getStatus() != Order.OrderStatus.PENDING_AT_REGISTER) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Order is not pending at register (status: " + order.getStatus() + ")");
        }

        String depositIdempKey = "deposit-" + order.getIdempotencyKey();
        paymentService.initiateDeposit(order, order.getTotalAmount(), depositIdempKey);

        String captureIdempKey = "capture-" + order.getIdempotencyKey();
        try {
            paymentService.capturePayment(order, captureIdempKey);
        } catch (Exception e) {
            log.error("POS payment capture failed for order {}: {}", order.getReceiptNumber(), e.getMessage());
            String compensateIdempKey = "compensate-" + order.getIdempotencyKey();
            try {
                paymentService.compensateFailedPayment(order, compensateIdempKey);
            } catch (Exception ce) {
                log.error("Compensation failed for order {}: {}", order.getReceiptNumber(), ce.getMessage());
            }
            throw new ResponseStatusException(HttpStatus.PAYMENT_REQUIRED, "Payment capture failed at POS");
        }

        order.setStatus(Order.OrderStatus.COMPLETED);
        order = orderRepository.save(order);

        User user = order.getUser();
        for (OrderItem item : order.getItems()) {
            BehaviorEvent event = BehaviorEvent.builder()
                    .user(user)
                    .product(item.getProduct())
                    .eventType(BehaviorEvent.EventType.PURCHASE)
                    .build();
            behaviorEventRepository.save(event);
        }

        try {
            notificationService.createNotification(user.getId(),
                    "Your order " + order.getReceiptNumber() + " has been completed at the register.");
        } catch (Exception e) {
            log.warn("Could not create POS notification for order {}: {}", order.getReceiptNumber(), e.getMessage());
        }

        log.info("Order {} POS-confirmed by staff {} total {}",
                order.getReceiptNumber(), staffId, order.getTotalAmount());
        return toDto(order);
    }

    @Transactional(readOnly = true)
    public List<OrderDto> getOrderHistory(Long userId, String storeId) {
        return orderRepository.findByUserIdAndStoreIdOrderByTransactionTimestampDesc(userId, storeId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public OrderDto getOrderByReceiptNumber(String receiptNumber, String storeId) {
        Order order = orderRepository.findByReceiptNumberAndStoreId(receiptNumber, storeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Transaction not found with receipt: " + receiptNumber));
        return toDto(order);
    }

    @Transactional
    public RefundResponse processRefund(RefundRequest request, Long staffId, String storeId, String ipAddress) {
        Order order = orderRepository.findByReceiptNumberAndStoreId(request.getReceiptNumber(), storeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Order not found with receipt: " + request.getReceiptNumber()));

        PaymentTransaction refund = paymentService.processRefund(order, request.getIdempotencyKey(), staffId, ipAddress);

        for (OrderItem item : order.getItems()) {
            Product product = item.getProduct();
            product.setStockQuantity(product.getStockQuantity() + item.getQuantity());
            productRepository.save(product);
        }

        try {
            notificationService.createNotification(order.getUser().getId(),
                    "Your order " + order.getReceiptNumber() + " has been refunded.");
        } catch (Exception e) {
            log.warn("Could not create refund notification: {}", e.getMessage());
        }

        return RefundResponse.builder()
                .refundId(refund.getId())
                .receiptNumber(order.getReceiptNumber())
                .amount(order.getTotalAmount())
                .updatedOrderStatus(order.getStatus().name())
                .processedAt(refund.getCreatedAt().toString())
                .build();
    }

    @Transactional
    public OrderDto markReadyForPickup(Long orderId, Long staffId, String storeId) {
        Order order = orderRepository.findByIdAndStoreId(orderId, storeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));

        if (order.getStatus() != Order.OrderStatus.COMPLETED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Only completed orders can be marked ready for pickup (current status: " + order.getStatus() + ")");
        }

        order.setStatus(Order.OrderStatus.READY_FOR_PICKUP);
        order = orderRepository.save(order);

        try {
            notificationService.createNotification(order.getUser().getId(),
                    "Your order " + order.getReceiptNumber() + " is ready for pickup!");
        } catch (Exception e) {
            log.warn("Could not create pickup notification: {}", e.getMessage());
        }

        log.info("Order {} marked as ready for pickup by staff {}", orderId, staffId);
        return toDto(order);
    }

    private String generateReceiptNumber() {
        String uuid = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        return "RCP-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + "-" + uuid;
    }

    public OrderDto toDto(Order order) {
        List<OrderItemDto> items = order.getItems() != null ? order.getItems().stream()
                .map(this::toItemDto)
                .collect(Collectors.toList()) : List.of();

        String formattedTimestamp = order.getTransactionTimestamp() != null
                ? order.getTransactionTimestamp().format(RECEIPT_TS_FORMAT)
                : null;

        return OrderDto.builder()
                .id(order.getId())
                .receiptNumber(order.getReceiptNumber())
                .totalAmount(order.getTotalAmount())
                .status(order.getStatus().name())
                .transactionTimestamp(formattedTimestamp)
                .items(items)
                .userId(order.getUser() != null ? order.getUser().getId() : null)
                .build();
    }

    private OrderItemDto toItemDto(OrderItem item) {
        BigDecimal subtotal = item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
        return OrderItemDto.builder()
                .id(item.getId())
                .productId(item.getProduct().getId())
                .productName(item.getProduct().getName())
                .quantity(item.getQuantity())
                .unitPrice(item.getUnitPrice())
                .subtotal(subtotal)
                .build();
    }
}
