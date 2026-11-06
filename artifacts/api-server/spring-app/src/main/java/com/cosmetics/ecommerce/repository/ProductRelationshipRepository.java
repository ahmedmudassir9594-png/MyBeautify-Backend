package com.cosmetics.ecommerce.repository;

import com.cosmetics.ecommerce.entity.ProductRelationship;
import com.cosmetics.ecommerce.enums.RelationshipType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductRelationshipRepository extends JpaRepository<ProductRelationship, Long> {

    List<ProductRelationship> findBySourceProductIdOrderByDisplayOrderAscCreatedAtDesc(Long sourceProductId);

    List<ProductRelationship> findBySourceProductIdAndRelationshipTypeOrderByDisplayOrderAscCreatedAtDesc(
            Long sourceProductId, RelationshipType relationshipType);

    Optional<ProductRelationship> findBySourceProductIdAndRelatedProductIdAndRelationshipType(
            Long sourceProductId, Long relatedProductId, RelationshipType relationshipType);

    Optional<ProductRelationship> findBySourceProductIdAndRelatedProductId(
            Long sourceProductId, Long relatedProductId);

    boolean existsBySourceProductIdAndRelatedProductIdAndRelationshipType(
            Long sourceProductId, Long relatedProductId, RelationshipType relationshipType);

    boolean existsBySourceProductIdAndRelatedProductId(
            Long sourceProductId, Long relatedProductId);

    void deleteBySourceProductIdAndRelatedProductIdAndRelationshipType(
            Long sourceProductId, Long relatedProductId, RelationshipType relationshipType);

    void deleteBySourceProductIdAndRelatedProductId(
            Long sourceProductId, Long relatedProductId);

    @Query("""
        SELECT r FROM ProductRelationship r
        JOIN FETCH r.relatedProduct p
        JOIN FETCH p.category
        WHERE r.sourceProduct.id = :sourceProductId
          AND (:type IS NULL OR r.relationshipType = :type)
          AND p.active = true
        ORDER BY r.displayOrder ASC, r.createdAt DESC
    """)
    List<ProductRelationship> findActiveRelatedProducts(
            @Param("sourceProductId") Long sourceProductId,
            @Param("type") RelationshipType type);
}

