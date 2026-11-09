package com.cosmetics.ecommerce.dto;

import com.cosmetics.ecommerce.entity.Product;
import com.cosmetics.ecommerce.entity.ProductImage;
import com.cosmetics.ecommerce.entity.ProductRelationship;
import com.cosmetics.ecommerce.enums.RelationshipType;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class ProductRelationshipDtos {

    private ProductRelationshipDtos() {}

    public record ProductRelationshipRequest(
            @NotNull Long relatedProductId,
            RelationshipType relationshipType,
            Integer displayOrder
    ) {
        public ProductRelationshipRequest {
            if (relationshipType == null) relationshipType = RelationshipType.SIMILAR;
            if (displayOrder == null) displayOrder = 0;
        }
    }

    public record ProductRelationshipResponse(
            Long id,
            Long sourceProductId,
            String sourceProductName,
            Long relatedProductId,
            String relatedProductName,
            String relatedProductSlug,
            String relatedProductImageUrl,
            BigDecimal relatedProductPrice,
            BigDecimal relatedProductSellingPrice,
            RelationshipType relationshipType,
            int displayOrder,
            Instant createdAt
    ) {
        public static ProductRelationshipResponse from(ProductRelationship rel) {
            Product src = rel.getSourceProduct();
            Product relProd = rel.getRelatedProduct();
            return new ProductRelationshipResponse(
                    rel.getId(),
                    src != null ? src.getId() : null,
                    src != null ? src.getName() : null,
                    relProd != null ? relProd.getId() : null,
                    relProd != null ? relProd.getName() : null,
                    relProd != null ? relProd.getSlug() : null,
                    relProd != null ? relProd.getImageUrl() : null,
                    relProd != null ? relProd.getPrice() : null,
                    relProd != null ? relProd.sellingPrice() : null,
                    rel.getRelationshipType(),
                    rel.getDisplayOrder(),
                    rel.getCreatedAt()
            );
        }
    }

    public record SimilarProductResponse(
            Long id,
            String name,
            String slug,
            String sku,
            String description,
            String shortDescription,
            BigDecimal price,
            BigDecimal discountPrice,
            BigDecimal sellingPrice,
            int stockQuantity,
            int lowStockThreshold,
            Long brandId,
            String brandName,
            Long categoryId,
            String categoryName,
            String imageUrl,
            List<String> images,
            boolean active,
            boolean featured,
            boolean inStock,
            String skinType,
            String shade,
            String size,
            Instant expiryDate,
            double averageRating,
            String relationshipType,
            int displayOrder
    ) {
        public static SimilarProductResponse from(Product p, double rating, String relationshipType, int displayOrder) {
            boolean inStock = p.isActive() && p.getStockQuantity() > 0;

            List<String> galleryImages = (p.getImages() != null && !p.getImages().isEmpty())
                    ? p.getImages().stream().map(ProductImage::getImageUrl).toList()
                    : (p.getImageUrl() != null ? List.of(p.getImageUrl()) : List.of());

            return new SimilarProductResponse(
                    p.getId(),
                    p.getName(),
                    p.getSlug(),
                    p.getSku(),
                    p.getDescription(),
                    p.getShortDescription(),
                    p.getPrice(),
                    p.getDiscountPrice(),
                    p.sellingPrice(),
                    p.getStockQuantity(),
                    p.getLowStockThreshold(),
                    1L,
                    "MALKAZ",
                    p.getCategory() != null ? p.getCategory().getId() : null,
                    p.getCategory() != null ? p.getCategory().getName() : "Fine Jewellery",
                    p.getImageUrl(),
                    galleryImages,
                    p.isActive(),
                    p.isFeatured(),
                    inStock,
                    p.getSkinType(),
                    p.getShade(),
                    p.getSize(),
                    p.getExpiryDate(),
                    rating,
                    relationshipType,
                    displayOrder
            );
        }
    }
}
