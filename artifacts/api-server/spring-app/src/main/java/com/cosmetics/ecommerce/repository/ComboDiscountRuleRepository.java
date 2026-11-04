package com.cosmetics.ecommerce.repository;

import com.cosmetics.ecommerce.entity.ComboDiscountRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface ComboDiscountRuleRepository extends JpaRepository<ComboDiscountRule, Long> {

    @Query("SELECT r FROM ComboDiscountRule r WHERE r.active = true AND (r.startDate IS NULL OR r.startDate <= :now) AND (r.endDate IS NULL OR r.endDate >= :now) ORDER BY r.minProducts DESC, r.discountValue DESC")
    List<ComboDiscountRule> findActiveAndValidRules(@Param("now") Instant now);

    List<ComboDiscountRule> findByActiveTrue();
}

