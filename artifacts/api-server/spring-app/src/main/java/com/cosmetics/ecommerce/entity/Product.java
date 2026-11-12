package com.cosmetics.ecommerce.entity;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "products", indexes = {
        @Index(name = "idx_product_slug", columnList = "slug", unique = true),
        @Index(name = "idx_product_sku", columnList = "sku", unique = true),
        @Index(name = "idx_product_active", columnList = "active")
})
public class Product {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, length = 180) private String name;
    @Column(nullable = false, unique = true, length = 220) private String slug;
    @Column(nullable = false, unique = true, length = 80) private String sku;
    @Column(length = 3000) private String description;
    @Column(length = 500) private String shortDescription;
    @Column(nullable = false, precision = 12, scale = 2) private BigDecimal price;
    @Column(precision = 12, scale = 2) private BigDecimal discountPrice;
    @Column(nullable = false) private int stockQuantity;
    @Column(nullable = false) private int lowStockThreshold = 5;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private Category category;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subcategory_id")
    private Subcategory subcategory;

    private String imageUrl; // Primary thumbnail (backwards compatibility)

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("displayOrder ASC")
    private List<ProductImage> images = new ArrayList<>();

    @Column(nullable = false) private boolean active = true;
    @Column(nullable = false) private boolean featured;
    @Column(length = 1000) private String ingredients;
    @Column(length = 1000) private String benefits;
    @Column(length = 1000) private String howToUse;
    private String skinType;
    private String shade;
    private String size;
    private Instant expiryDate;

    @Convert(converter = StringMapJsonConverter.class)
    @Column(name = "attributes_json", columnDefinition = "TEXT")
    private Map<String, String> attributes = new LinkedHashMap<>();

    @CreationTimestamp @Column(nullable = false, updatable = false) private Instant createdAt;
    @UpdateTimestamp private Instant updatedAt;

    protected Product() {}

    public Product(String name, String slug, String sku, BigDecimal price, int stockQuantity, Category category) {
        this.name = name; this.slug = slug; this.sku = sku; this.price = price; this.stockQuantity = stockQuantity;
        this.category = category;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getSlug() { return slug; }
    public String getSku() { return sku; }
    public String getDescription() { return description; }
    public String getShortDescription() { return shortDescription; }
    public BigDecimal getPrice() { return price; }
    public BigDecimal getDiscountPrice() { return discountPrice; }
    public int getStockQuantity() { return stockQuantity; }
    public int getLowStockThreshold() { return lowStockThreshold; }
    public Category getCategory() { return category; }
    public Subcategory getSubcategory() { return subcategory; }
    public void setSubcategory(Subcategory value) { subcategory = value; }
    public String getImageUrl() { return imageUrl; }
    public boolean isActive() { return active; }
    public boolean isFeatured() { return featured; }
    public String getIngredients() { return ingredients; }
    public String getBenefits() { return benefits; }
    public String getHowToUse() { return howToUse; }
    public String getSkinType() { return skinType; }
    public String getShade() { return shade; }
    public String getSize() { return size; }
    public Instant getExpiryDate() { return expiryDate; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public BigDecimal sellingPrice() { return discountPrice != null && discountPrice.compareTo(price) < 0 ? discountPrice : price; }

    public List<ProductImage> getImages() { return images; }
    public void setImages(List<ProductImage> images) { this.images = images; }

    // Helper methods for gallery management
    public void addImage(String url, int order) {
        if (this.images == null) {
            this.images = new ArrayList<>();
        }
        this.images.add(new ProductImage(this, url, order));
    }

    public void clearImages() {
        if (this.images != null) {
            this.images.clear();
        }
    }

    public void setName(String value) { name = value; } public void setSlug(String value) { slug = value; }
    public void setSku(String value) { sku = value; } public void setDescription(String value) { description = value; }
    public void setShortDescription(String value) { shortDescription = value; } public void setPrice(BigDecimal value) { price = value; }
    public void setDiscountPrice(BigDecimal value) { discountPrice = value; } public void setStockQuantity(int value) { stockQuantity = value; }
    public void setLowStockThreshold(int value) { lowStockThreshold = value; }
    public void setCategory(Category value) { category = value; } public void setImageUrl(String value) { imageUrl = value; }
    public void setActive(boolean value) { active = value; } public void setFeatured(boolean value) { featured = value; }
    public void setIngredients(String value) { ingredients = value; } public void setBenefits(String value) { benefits = value; }
    public void setHowToUse(String value) { howToUse = value; } public void setSkinType(String value) { skinType = value; }
    public void setShade(String value) { shade = value; } public void setSize(String value) { size = value; }
    public void setExpiryDate(Instant value) { expiryDate = value; }

    public Map<String, String> getAttributes() {
        return attributes != null ? attributes : Collections.emptyMap();
    }

    public void setAttributes(Map<String, String> value) {
        this.attributes = value != null ? new LinkedHashMap<>(value) : new LinkedHashMap<>();
    }
}