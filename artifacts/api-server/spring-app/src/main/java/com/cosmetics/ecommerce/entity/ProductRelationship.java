package com.cosmetics.ecommerce.entity;

import com.cosmetics.ecommerce.enums.RelationshipType;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "product_relationships",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_product_relationship",
                columnNames = {"source_product_id", "related_product_id", "relationship_type"}
        ),
        indexes = {
                @Index(name = "idx_rel_source", columnList = "source_product_id"),
                @Index(name = "idx_rel_related", columnList = "related_product_id"),
                @Index(name = "idx_rel_type", columnList = "relationship_type")
        })
public class ProductRelationship {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_product_id", nullable = false)
    private Product sourceProduct;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "related_product_id", nullable = false)
    private Product relatedProduct;

    @Enumerated(EnumType.STRING)
    @Column(name = "relationship_type", nullable = false, length = 32)
    private RelationshipType relationshipType = RelationshipType.SIMILAR;

    @Column(name = "display_order", nullable = false)
    private int displayOrder = 0;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected ProductRelationship() {}

    public ProductRelationship(Product sourceProduct, Product relatedProduct, RelationshipType relationshipType, int displayOrder) {
        if (sourceProduct == null || relatedProduct == null) {
            throw new IllegalArgumentException("Source and related products cannot be null.");
        }
        if (sourceProduct.getId() != null && sourceProduct.getId().equals(relatedProduct.getId())) {
            throw new IllegalArgumentException("A product cannot be related to itself.");
        }
        this.sourceProduct = sourceProduct;
        this.relatedProduct = relatedProduct;
        this.relationshipType = relationshipType != null ? relationshipType : RelationshipType.SIMILAR;
        this.displayOrder = displayOrder;
    }

    @PrePersist
    @PreUpdate
    public void validateNotSelfReferencing() {
        if (sourceProduct != null && relatedProduct != null
                && sourceProduct.getId() != null && sourceProduct.getId().equals(relatedProduct.getId())) {
            throw new IllegalStateException("A product cannot be related to itself.");
        }
    }

    public Long getId() {
        return id;
    }

    public Product getSourceProduct() {
        return sourceProduct;
    }

    public void setSourceProduct(Product sourceProduct) {
        this.sourceProduct = sourceProduct;
    }

    public Product getRelatedProduct() {
        return relatedProduct;
    }

    public void setRelatedProduct(Product relatedProduct) {
        this.relatedProduct = relatedProduct;
    }

    public RelationshipType getRelationshipType() {
        return relationshipType;
    }

    public void setRelationshipType(RelationshipType relationshipType) {
        this.relationshipType = relationshipType;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(int displayOrder) {
        this.displayOrder = displayOrder;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

