package com.meridianmart.repository;

import com.meridianmart.model.PaymentTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {

    Optional<PaymentTransaction> findByIdempotencyKey(String idempotencyKey);

    boolean existsByIdempotencyKey(String idempotencyKey);

    List<PaymentTransaction> findByOrderId(Long orderId);

    List<PaymentTransaction> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);
}
