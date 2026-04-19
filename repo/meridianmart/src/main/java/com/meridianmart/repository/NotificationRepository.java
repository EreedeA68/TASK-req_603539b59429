package com.meridianmart.repository;

import com.meridianmart.model.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByUserIdOrderByCreatedAtDesc(Long userId);

    @Query("""
            SELECT COUNT(n) FROM Notification n
            WHERE n.user.id = :userId AND n.createdAt >= :startOfDay
            """)
    long countTodayByUserId(@Param("userId") Long userId, @Param("startOfDay") LocalDateTime startOfDay);

    long countByUserIdAndReadFalse(Long userId);

    boolean existsByUserIdAndId(Long userId, Long id);
}
