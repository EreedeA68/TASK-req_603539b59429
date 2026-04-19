package com.meridianmart.repository;

import com.meridianmart.model.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    Page<Product> findAll(Pageable pageable);

    Page<Product> findByStoreId(String storeId, Pageable pageable);

    Optional<Product> findByIdAndStoreId(Long id, String storeId);

    List<Product> findByIdInAndStoreId(List<Long> ids, String storeId);

    @Query("SELECT p FROM Product p WHERE p.newArrival = true AND p.storeId = :storeId ORDER BY p.createdAt DESC")
    List<Product> findNewArrivalsByStoreId(@Param("storeId") String storeId, Pageable pageable);

    List<Product> findByCategory(String category);

    List<Product> findByNewArrivalTrue();

    @Query("SELECT p FROM Product p WHERE p.newArrival = true ORDER BY p.createdAt DESC")
    List<Product> findNewArrivals(Pageable pageable);

    @Query(value = """
            SELECT p.*, COUNT(be.id) as event_count
            FROM products p
            JOIN behavior_events be ON p.id = be.product_id
            WHERE be.created_at >= :since
            GROUP BY p.id
            ORDER BY event_count DESC
            """, nativeQuery = true)
    List<Product> findPopularProductsSince(@Param("since") LocalDateTime since, Pageable pageable);

    @Query(value = """
            SELECT p.*, COUNT(be.id) as event_count
            FROM products p
            JOIN behavior_events be ON p.id = be.product_id
            WHERE p.category = :category AND be.created_at >= :since
            GROUP BY p.id
            ORDER BY event_count DESC
            """, nativeQuery = true)
    List<Product> findPopularProductsByCategorySince(
            @Param("category") String category,
            @Param("since") LocalDateTime since,
            Pageable pageable);

    List<Product> findByIdIn(List<Long> ids);
}
