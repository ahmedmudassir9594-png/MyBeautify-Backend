package com.cosmetics.ecommerce.repository;

import com.cosmetics.ecommerce.entity.Coupon;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface CouponRepository extends JpaRepository<Coupon, Long> {
    Optional<Coupon> findByCodeIgnoreCase(String code);

    @Query("SELECT c FROM Coupon c WHERE c.active = true AND c.startDate <= :now AND c.expiryDate >= :now AND (c.usageLimit IS NULL OR c.usedCount < c.usageLimit) ORDER BY c.startDate DESC")
    List<Coupon> findActiveAndValidCoupons(@Param("now") Instant now);
}
