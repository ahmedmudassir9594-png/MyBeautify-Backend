package com.cosmetics.ecommerce.repository;

import com.cosmetics.ecommerce.entity.Order;
import com.cosmetics.ecommerce.enums.OrderStatus;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

public interface OrderRepository extends JpaRepository<Order, Long> {
    Page<Order> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
    Optional<Order> findByIdAndUserId(Long id, Long userId);
    Optional<Order> findByOrderNumberAndUserId(String orderNumber, Long userId);
    long countByUserId(Long userId);
    long countByOrderStatus(OrderStatus status);
    long countByCreatedAtGreaterThanEqual(Instant from);
    @org.springframework.data.jpa.repository.Query("select coalesce(sum(o.totalAmount), 0) from Order o where o.paymentStatus = com.cosmetics.ecommerce.enums.PaymentStatus.SUCCESS")
    BigDecimal totalRevenue();
    @org.springframework.data.jpa.repository.Query("select coalesce(sum(o.totalAmount), 0) from Order o where o.paymentStatus = com.cosmetics.ecommerce.enums.PaymentStatus.SUCCESS and o.createdAt >= :from")
    BigDecimal revenueSince(Instant from);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("SELECT o FROM Order o WHERE o.id = :id")
    Optional<Order> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") Long id);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("SELECT o FROM Order o WHERE o.orderNumber = :orderNumber")
    Optional<Order> findByOrderNumberForUpdate(@org.springframework.data.repository.query.Param("orderNumber") String orderNumber);

    @org.springframework.data.jpa.repository.Query("SELECT DISTINCT o FROM Order o LEFT JOIN FETCH o.items LEFT JOIN FETCH o.shippingAddress LEFT JOIN FETCH o.coupon WHERE o.user.id = :userId AND o.orderStatus = :orderStatus AND o.paymentStatus = :paymentStatus ORDER BY o.createdAt DESC")
    List<Order> findPendingOrdersForRecovery(@org.springframework.data.repository.query.Param("userId") Long userId,
                                            @org.springframework.data.repository.query.Param("orderStatus") OrderStatus orderStatus,
                                            @org.springframework.data.repository.query.Param("paymentStatus") com.cosmetics.ecommerce.enums.PaymentStatus paymentStatus);
}