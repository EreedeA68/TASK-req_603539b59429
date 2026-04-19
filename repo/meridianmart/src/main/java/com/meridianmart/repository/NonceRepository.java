package com.meridianmart.repository;

import com.meridianmart.model.NonceEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Repository
public interface NonceRepository extends JpaRepository<NonceEntry, Long> {

    // Only consider a nonce "used" if it was stored within the active replay window.
    boolean existsByNonceValueAndCreatedAtAfter(String nonceValue, LocalDateTime windowStart);

    @Modifying
    @Transactional
    @Query("DELETE FROM NonceEntry n WHERE n.createdAt < :expiry")
    void deleteExpired(@Param("expiry") LocalDateTime expiry);
}
