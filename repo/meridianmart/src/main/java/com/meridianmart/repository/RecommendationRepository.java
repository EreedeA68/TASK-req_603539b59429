package com.meridianmart.repository;

import com.meridianmart.model.Recommendation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface RecommendationRepository extends JpaRepository<Recommendation, Long> {

    List<Recommendation> findByUserIdOrderByScoreDesc(Long userId);

    @Query("""
            SELECT r FROM Recommendation r
            WHERE r.user.id = :userId AND r.cachedAt >= :since
            ORDER BY r.score DESC
            """)
    List<Recommendation> findFreshByUserId(@Param("userId") Long userId, @Param("since") LocalDateTime since);

    @Modifying
    @Query("DELETE FROM Recommendation r WHERE r.user.id = :userId")
    void deleteByUserId(@Param("userId") Long userId);

    @Modifying
    @Query("DELETE FROM Recommendation r WHERE r.cachedAt < :expiry")
    void deleteExpired(@Param("expiry") LocalDateTime expiry);

    boolean existsByUserIdAndCachedAtAfter(Long userId, LocalDateTime after);
}
