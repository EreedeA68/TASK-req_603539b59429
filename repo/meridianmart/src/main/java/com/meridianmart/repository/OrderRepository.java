package com.meridianmart.repository;

import com.meridianmart.model.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findByUserIdOrderByTransactionTimestampDesc(Long userId);

    List<Order> findByUserIdAndStoreIdOrderByTransactionTimestampDesc(Long userId, String storeId);

    Optional<Order> findByReceiptNumber(String receiptNumber);

    Optional<Order> findByReceiptNumberAndStoreId(String receiptNumber, String storeId);

    Optional<Order> findByIdAndStoreId(Long id, String storeId);

    Optional<Order> findByIdempotencyKey(String idempotencyKey);

    @Query("SELECT o FROM Order o WHERE o.user.id = :userId AND o.status IN ('COMPLETED', 'REFUNDED')")
    List<Order> findCompletedByUserId(@Param("userId") Long userId);

    @Query("SELECT DISTINCT o.user.id FROM Order o WHERE o.status = 'PENDING' OR o.status = 'READY_FOR_PICKUP'")
    List<Long> findUserIdsWithOpenDisputes();

    boolean existsByIdempotencyKey(String idempotencyKey);
}
