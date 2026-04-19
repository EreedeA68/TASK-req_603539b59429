package com.meridianmart.repository;

import com.meridianmart.model.BehaviorEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface BehaviorEventRepository extends JpaRepository<BehaviorEvent, Long> {

    List<BehaviorEvent> findByUserId(Long userId);

    @Query("SELECT COUNT(DISTINCT be.product.id) FROM BehaviorEvent be WHERE be.user.id = :userId")
    long countDistinctProductsByUserId(@Param("userId") Long userId);

    @Query("""
            SELECT be.user.id, be.product.id, COUNT(be.id) as cnt
            FROM BehaviorEvent be
            GROUP BY be.user.id, be.product.id
            """)
    List<Object[]> findAllUserProductInteractionCounts();

    @Query("""
            SELECT be.user.id, be.product.id, COUNT(be.id) as cnt
            FROM BehaviorEvent be
            WHERE be.product.storeId = :storeId
            GROUP BY be.user.id, be.product.id
            """)
    List<Object[]> findAllUserProductInteractionCountsByStoreId(@Param("storeId") String storeId);

    @Query("""
            SELECT be.product.id, COUNT(be.id) as cnt
            FROM BehaviorEvent be
            WHERE be.createdAt >= :since
            GROUP BY be.product.id
            ORDER BY cnt DESC
            """)
    List<Object[]> findProductPopularitySince(@Param("since") LocalDateTime since);

    @Query("""
            SELECT be.product.id, COUNT(be.id) as cnt
            FROM BehaviorEvent be
            WHERE be.createdAt >= :since AND be.product.storeId = :storeId
            GROUP BY be.product.id
            ORDER BY cnt DESC
            """)
    List<Object[]> findProductPopularitySinceByStoreId(
            @Param("since") LocalDateTime since,
            @Param("storeId") String storeId);

    @Query("SELECT COUNT(be) FROM BehaviorEvent be WHERE be.user.id = :userId")
    long countByUserId(@Param("userId") Long userId);

    @Query("""
            SELECT be.product.id
            FROM BehaviorEvent be
            WHERE be.user.id = :userId
            """)
    List<Long> findProductIdsByUserId(@Param("userId") Long userId);

    @Query("""
            SELECT DISTINCT be.product.category
            FROM BehaviorEvent be
            WHERE be.user.id = :userId
            """)
    List<String> findInteractedCategoriesByUserId(@Param("userId") Long userId);

    @Query("""
            SELECT be.product.id, COUNT(be.id) as cnt
            FROM BehaviorEvent be
            WHERE be.createdAt >= :since
              AND be.product.storeId = :storeId
              AND be.product.category IN :categories
            GROUP BY be.product.id
            ORDER BY cnt DESC
            """)
    List<Object[]> findProductPopularityByCategoriesSince(
            @Param("since") LocalDateTime since,
            @Param("storeId") String storeId,
            @Param("categories") List<String> categories);
}
