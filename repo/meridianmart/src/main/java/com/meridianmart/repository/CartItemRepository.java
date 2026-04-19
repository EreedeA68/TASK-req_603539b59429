package com.meridianmart.repository;

import com.meridianmart.model.CartItem;
import com.meridianmart.model.User;
import com.meridianmart.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    List<CartItem> findByUser(User user);

    List<CartItem> findByUserId(Long userId);

    Optional<CartItem> findByUserAndProduct(User user, Product product);

    Optional<CartItem> findByUserIdAndProductId(Long userId, Long productId);

    @Modifying
    @Query("DELETE FROM CartItem c WHERE c.user.id = :userId")
    void deleteByUserId(@Param("userId") Long userId);

    boolean existsByUserIdAndId(Long userId, Long itemId);
}
