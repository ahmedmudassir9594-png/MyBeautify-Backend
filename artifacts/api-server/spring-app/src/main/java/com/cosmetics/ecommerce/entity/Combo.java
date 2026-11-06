package com.cosmetics.ecommerce.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "combos", indexes = {
        @Index(name = "idx_combo_slug", columnList = "slug", unique = true),
        @Index(name = "idx_combo_name", columnList = "name", unique = true),
        @Index(name = "idx_combo_active", columnList = "active"),
        @Index(name = "idx_combo_dates", columnList = "startDate, endDate")
})
public class Combo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 180)
    private String name;

    @Column(nullable = false, unique = true, length = 220)
    private String slug;

    @Column(length = 2000)
    private String description;

    @Column(name = "image_file_name", length = 255)
    private String imageFileName;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal originalPrice = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal sellingPrice = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    private Instant startDate;

    private Instant endDate;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false)
    private boolean featured = false;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    @OneToMany(mappedBy = "combo", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ComboItem> items = new ArrayList<>();

    protected Combo() {}

    public Combo(String name, String slug, String description, BigDecimal sellingPrice) {
        this.name = name;
        this.slug = slug;
        this.description = description;
        this.sellingPrice = sellingPrice;
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

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getImageFileName() {
        return imageFileName;
    }

    public void setImageFileName(String imageFileName) {
        this.imageFileName = imageFileName;
    }

    public BigDecimal getOriginalPrice() {
        return originalPrice;
    }

    public void setOriginalPrice(BigDecimal originalPrice) {
        this.originalPrice = originalPrice;
    }

    public BigDecimal getSellingPrice() {
        return sellingPrice;
    }

    public void setSellingPrice(BigDecimal sellingPrice) {
        this.sellingPrice = sellingPrice;
    }

    public BigDecimal getDiscountAmount() {
        return discountAmount;
    }

    public void setDiscountAmount(BigDecimal discountAmount) {
        this.discountAmount = discountAmount;
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

    public boolean isFeatured() {
        return featured;
    }

    public void setFeatured(boolean featured) {
        this.featured = featured;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public List<ComboItem> getItems() {
        return items;
    }

    public void addItem(ComboItem item) {
        items.add(item);
        item.setCombo(this);
    }

    public void removeItem(ComboItem item) {
        items.remove(item);
        item.setCombo(null);
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

    public BigDecimal getSavingsPercentage() {
        if (originalPrice == null || originalPrice.compareTo(BigDecimal.ZERO) <= 0 || discountAmount == null) {
            return BigDecimal.ZERO;
        }
        return discountAmount.multiply(BigDecimal.valueOf(100))
                .divide(originalPrice, 1, RoundingMode.HALF_UP);
    }

    public void recalculatePricing() {
        BigDecimal componentSum = BigDecimal.ZERO;
        for (ComboItem item : items) {
            if (item.getProduct() != null) {
                BigDecimal basePrice = item.getCustomPrice() != null
                        ? item.getCustomPrice()
                        : item.getProduct().sellingPrice();
                componentSum = componentSum.add(basePrice.multiply(BigDecimal.valueOf(item.getQuantity())));
            }
        }
        if (this.originalPrice == null || this.originalPrice.compareTo(BigDecimal.ZERO) <= 0) {
            this.originalPrice = componentSum;
        }
        if (this.sellingPrice != null && this.originalPrice != null) {
            this.discountAmount = this.originalPrice.subtract(this.sellingPrice).max(BigDecimal.ZERO);
        }
    }
}

