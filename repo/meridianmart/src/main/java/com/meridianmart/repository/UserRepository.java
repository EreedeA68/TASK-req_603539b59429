package com.meridianmart.repository;

import com.meridianmart.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    Optional<User> findByUsername(String username);

    boolean existsByEmail(String email);

    boolean existsByUsername(String username);

    @Query("SELECT u FROM User u WHERE u.locked = true AND u.lockTime IS NOT NULL AND u.lockTime < :expiry")
    List<User> findLockedUsersWithExpiredLockTime(@Param("expiry") LocalDateTime expiry);

    @Query("SELECT u FROM User u WHERE u.role = 'SHOPPER' AND u.anonymized = false AND (u.lastActive IS NULL OR u.lastActive < :cutoff)")
    List<User> findInactiveShoppers(@Param("cutoff") LocalDateTime cutoff);

    @Modifying
    @Query("UPDATE User u SET u.lastActive = :now WHERE u.id = :userId")
    void updateLastActive(@Param("userId") Long userId, @Param("now") LocalDateTime now);
}
