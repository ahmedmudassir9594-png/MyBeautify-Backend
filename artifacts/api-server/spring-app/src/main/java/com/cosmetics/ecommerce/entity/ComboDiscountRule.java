package com.cosmetics.ecommerce.entity;

import com.cosmetics.ecommerce.enums.DiscountType;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

@Entity
@Table(name = "combo_discount_rules", indexes = {
        @Index(name = "idx_combo_rules_active", columnList = "active"),
        @Index(name = "idx_combo_rules_dates", columnList = "startDate, endDate")
})
public class ComboDiscountRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(nullable = false)
    private int minProducts = 2;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private DiscountType discountType = DiscountType.PERCENTAGE;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal discountValue = BigDecimal.valueOf(10.00);

    @Column(precision = 12, scale = 2)
    private BigDecimal minSubtotal;

    @Column(precision = 12, scale = 2)
    private BigDecimal maxDiscountAmount;

    private Instant startDate;

    private Instant endDate;

    @Column(nullable = false)
    private boolean active = true;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    protected ComboDiscountRule() {}

    public ComboDiscountRule(String name, int minProducts, DiscountType discountType, BigDecimal discountValue) {
        this.name = name;
        this.minProducts = minProducts;
        this.discountType = discountType;
        this.discountValue = discountValue;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public int getMinProducts() {
        return minProducts;
    }

    public void setMinProducts(int minProducts) {
        this.minProducts = minProducts;
    }

    public DiscountType getDiscountType() {
        return discountType;
    }

    public void setDiscountType(DiscountType discountType) {
        this.discountType = discountType;
    }

    public BigDecimal getDiscountValue() {
        return discountValue;
    }

    public void setDiscountValue(BigDecimal discountValue) {
        this.discountValue = discountValue;
    }

    public BigDecimal getMinSubtotal() {
        return minSubtotal;
    }

    public void setMinSubtotal(BigDecimal minSubtotal) {
        this.minSubtotal = minSubtotal;
    }

    public BigDecimal getMaxDiscountAmount() {
        return maxDiscountAmount;
    }

    public void setMaxDiscountAmount(BigDecimal maxDiscountAmount) {
        this.maxDiscountAmount = maxDiscountAmount;
    }

    public Instant getStartDate() {
        return startDate;
    }

    public void setStartDate(Instant startDate) {
        this.startDate = startDate;
    }

    public Instant getEndDate() {
        return endDate;
    }

    public void setEndDate(Instant endDate) {
        this.endDate = endDate;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public boolean isValidNow(Instant now) {
        if (!active) {
            return false;
        }
        if (startDate != null && now.isBefore(startDate)) {
            return false;
        }
        if (endDate != null && now.isAfter(endDate)) {
            return false;
        }
        return true;
    }

    public boolean appliesTo(int itemCount, BigDecimal subtotal, Instant now) {
        if (!isValidNow(now)) {
            return false;
        }
        if (itemCount < minProducts) {
            return false;
        }
        if (minSubtotal != null && subtotal != null && subtotal.compareTo(minSubtotal) < 0) {
            return false;
        }
        return true;
    }

    public BigDecimal calculateDiscount(BigDecimal subtotal) {
        if (subtotal == null || subtotal.compareTo(BigDecimal.ZERO) <= 0 || discountValue == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal discount = BigDecimal.ZERO;
        if (discountType == DiscountType.PERCENTAGE) {
            discount = subtotal.multiply(discountValue)
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        } else if (discountType == DiscountType.FIXED) {
            discount = discountValue.min(subtotal);
        }
        if (maxDiscountAmount != null && maxDiscountAmount.compareTo(BigDecimal.ZERO) > 0) {
            discount = discount.min(maxDiscountAmount);
        }
        return discount.max(BigDecimal.ZERO);
    }
}

