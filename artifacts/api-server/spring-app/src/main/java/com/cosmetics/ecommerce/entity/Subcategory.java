package com.cosmetics.ecommerce.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.text.Normalizer;
import java.time.Instant;
import java.util.Locale;

@Entity
@Table(
        name = "subcategories",
        indexes = {
                @Index(name = "idx_subcategory_category_id", columnList = "category_id"),
                @Index(name = "idx_subcategory_active", columnList = "active"),
                @Index(name = "idx_subcategory_name", columnList = "name")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_subcategory_category_name", columnNames = {"category_id", "name"})
        }
)
public class Subcategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 150)
    private String slug;

    @Column(length = 1000)
    private String description;

    @Column(length = 500)
    private String imageUrl;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(nullable = false)
    private boolean active = true;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    protected Subcategory() {}

    public Subcategory(String name, Category category) {
        this.name = name != null ? name.trim() : null;
        this.category = category;
        this.slug = generateSlug(name);
    }

    public Subcategory(String name, String description, Category category) {
        this.name = name != null ? name.trim() : null;
        this.description = description;
        this.category = category;
        this.slug = generateSlug(name);
    }

    @PrePersist
    @PreUpdate
    private void sanitize() {
        if (this.name != null) {
            this.name = this.name.trim();
        }
        if (this.slug == null || this.slug.isBlank()) {
            this.slug = generateSlug(this.name);
        }
    }

    private String generateSlug(String input) {
        if (input == null || input.isBlank()) {
            return "subcategory";
        }
        return Normalizer.normalize(input.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name != null ? name.trim() : null;
        this.slug = generateSlug(this.name);
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

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public Category getCategory() {
        return category;
    }

    public void setCategory(Category category) {
        this.category = category;
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
}

