package com.cosmetics.ecommerce.dto;

import com.cosmetics.ecommerce.entity.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class CatalogDtos {
    private CatalogDtos() {}

    public record CategoryResponse(
            Long id,
            String name,
            String description,
            String imageUrl,
            boolean active,
            long subcategoriesCount,
            List<SubcategoryDtos.SubcategoryResponse> subcategories
    ) {
        public CategoryResponse(Long id, String name, String description, String imageUrl, boolean active) {
            this(id, name, description, imageUrl, active, 0L, Collections.emptyList());
        }

        public CategoryResponse(Long id, String name, String description, String imageUrl, boolean active, long subcategoriesCount) {
            this(id, name, description, imageUrl, active, subcategoriesCount, Collections.emptyList());
        }

        public static CategoryResponse from(Category c) {
            return new CategoryResponse(c.getId(), c.getName(), c.getDescription(), c.getImageUrl(), c.isActive(), 0L, Collections.emptyList());
        }

        public static CategoryResponse from(Category c, long subcategoriesCount) {
            return new CategoryResponse(c.getId(), c.getName(), c.getDescription(), c.getImageUrl(), c.isActive(), subcategoriesCount, Collections.emptyList());
        }

        public static CategoryResponse from(Category c, long subcategoriesCount, List<SubcategoryDtos.SubcategoryResponse> subcategories) {
            return new CategoryResponse(c.getId(), c.getName(), c.getDescription(), c.getImageUrl(), c.isActive(), subcategoriesCount,
                    subcategories != null ? subcategories : Collections.emptyList());
        }
    }

    public record CategoryRequest(
            @NotBlank @Size(max = 120) String name,
            @Size(max = 1000) String description,
            String imageUrl,
            Boolean active
    ) {}

    public record ProductRequest(
            @NotBlank @Size(max = 180) String name,
            @NotBlank @Size(max = 80) String sku,
            @NotNull @Positive BigDecimal price,
            @PositiveOrZero BigDecimal discountPrice,
            @PositiveOrZero int stockQuantity,
            @PositiveOrZero int lowStockThreshold,
            @NotNull Long categoryId,
            String description,
            String shortDescription,
            String imageUrl,
            List<String> images,
            Boolean active,
            Boolean featured,
            String ingredients,
            String benefits,
            String howToUse,
            String skinType,
            String shade,
            String size,
            Instant expiryDate,
            Long subcategoryId,
            Map<String, String> attributes
    ) {
        public ProductRequest(
                String name, String sku, BigDecimal price, BigDecimal discountPrice,
                int stockQuantity, int lowStockThreshold, Long categoryId,
                String description, String shortDescription, String imageUrl,
                Boolean active, Boolean featured, String ingredients, String benefits,
                String howToUse, String skinType, String shade, String size, Instant expiryDate
        ) {
            this(name, sku, price, discountPrice, stockQuantity, lowStockThreshold, categoryId,
                    description, shortDescription, imageUrl,
                    imageUrl != null ? List.of(imageUrl) : Collections.emptyList(),
                    active, featured, ingredients, benefits, howToUse, skinType, shade, size, expiryDate, null, Collections.emptyMap());
        }

        public ProductRequest(
                String name, String sku, BigDecimal price, BigDecimal discountPrice,
                int stockQuantity, int lowStockThreshold, Long categoryId,
                String description, String shortDescription, String imageUrl,
                List<String> images, Boolean active, Boolean featured, String ingredients, String benefits,
                String howToUse, String skinType, String shade, String size, Instant expiryDate
        ) {
            this(name, sku, price, discountPrice, stockQuantity, lowStockThreshold, categoryId,
                    description, shortDescription, imageUrl, images,
                    active, featured, ingredients, benefits, howToUse, skinType, shade, size, expiryDate, null, Collections.emptyMap());
        }

        public ProductRequest(
                String name, String sku, BigDecimal price, BigDecimal discountPrice,
                int stockQuantity, int lowStockThreshold, Long categoryId,
                String description, String shortDescription, String imageUrl,
                List<String> images, Boolean active, Boolean featured, String ingredients, String benefits,
                String howToUse, String skinType, String shade, String size, Instant expiryDate,
                Long subcategoryId
        ) {
            this(name, sku, price, discountPrice, stockQuantity, lowStockThreshold, categoryId,
                    description, shortDescription, imageUrl, images,
                    active, featured, ingredients, benefits, howToUse, skinType, shade, size, expiryDate, subcategoryId, Collections.emptyMap());
        }
    }

    public record ProductResponse(
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
            Long categoryId,
            String categoryName,
            String imageUrl,
            List<String> images,
            boolean active,
            boolean featured,
            String ingredients,
            String benefits,
            String howToUse,
            String skinType,
            String shade,
            String size,
            Instant expiryDate,
            double averageRating,
            Long brandId,
            String brandName,
            String relationshipType,
            List<ProductResponse> similarProducts,
            Long subcategoryId,
            String subcategoryName,
            Map<String, String> attributes
    ) {
        public ProductResponse(
                Long id, String name, String slug, String sku, String description, String shortDescription,
                BigDecimal price, BigDecimal discountPrice, BigDecimal sellingPrice, int stockQuantity,
                int lowStockThreshold, Long categoryId, String categoryName,
                String imageUrl, List<String> images, boolean active, boolean featured, String ingredients, String benefits,
                String howToUse, String skinType, String shade, String size, Instant expiryDate, double averageRating
        ) {
            this(id, name, slug, sku, description, shortDescription, price, discountPrice, sellingPrice, stockQuantity,
                    lowStockThreshold, categoryId, categoryName, imageUrl, images, active, featured, ingredients, benefits,
                    howToUse, skinType, shade, size, expiryDate, averageRating, 1L, "MALKAZ", null, Collections.emptyList(), null, null, Collections.emptyMap());
        }

        public ProductResponse(
                Long id, String name, String slug, String sku, String description, String shortDescription,
                BigDecimal price, BigDecimal discountPrice, BigDecimal sellingPrice, int stockQuantity,
                int lowStockThreshold, Long categoryId, String categoryName,
                String imageUrl, List<String> images, boolean active, boolean featured, String ingredients, String benefits,
                String howToUse, String skinType, String shade, String size, Instant expiryDate, double averageRating,
                Long brandId, String brandName, String relationshipType, List<ProductResponse> similarProducts
        ) {
            this(id, name, slug, sku, description, shortDescription, price, discountPrice, sellingPrice, stockQuantity,
                    lowStockThreshold, categoryId, categoryName, imageUrl, images, active, featured, ingredients, benefits,
                    howToUse, skinType, shade, size, expiryDate, averageRating, brandId, brandName, relationshipType, similarProducts, null, null, Collections.emptyMap());
        }

        public ProductResponse(
                Long id, String name, String slug, String sku, String description, String shortDescription,
                BigDecimal price, BigDecimal discountPrice, BigDecimal sellingPrice, int stockQuantity,
                int lowStockThreshold, Long categoryId, String categoryName,
                String imageUrl, List<String> images, boolean active, boolean featured, String ingredients, String benefits,
                String howToUse, String skinType, String shade, String size, Instant expiryDate, double averageRating,
                Long brandId, String brandName, String relationshipType, List<ProductResponse> similarProducts,
                Long subcategoryId, String subcategoryName
        ) {
            this(id, name, slug, sku, description, shortDescription, price, discountPrice, sellingPrice, stockQuantity,
                    lowStockThreshold, categoryId, categoryName, imageUrl, images, active, featured, ingredients, benefits,
                    howToUse, skinType, shade, size, expiryDate, averageRating, brandId, brandName, relationshipType, similarProducts, subcategoryId, subcategoryName, Collections.emptyMap());
        }

        public static ProductResponse from(Product p, double rating) {
            return from(p, rating, Collections.emptyList());
        }

        public static ProductResponse from(Product p, double rating, List<ProductResponse> similar) {
            List<String> galleryImages = (p.getImages() != null && !p.getImages().isEmpty())
                    ? p.getImages().stream().map(ProductImage::getImageUrl).toList()
                    : (p.getImageUrl() != null ? List.of(p.getImageUrl()) : Collections.emptyList());

            return new ProductResponse(
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
                    p.getCategory() != null ? p.getCategory().getId() : null,
                    p.getCategory() != null ? p.getCategory().getName() : "Fine Jewellery",
                    p.getImageUrl(),
                    galleryImages,
                    p.isActive(),
                    p.isFeatured(),
                    p.getIngredients(),
                    p.getBenefits(),
                    p.getHowToUse(),
                    p.getSkinType(),
                    p.getShade(),
                    p.getSize(),
                    p.getExpiryDate(),
                    rating,
                    1L,
                    "MALKAZ",
                    null,
                    similar != null ? similar : Collections.emptyList(),
                    p.getSubcategory() != null ? p.getSubcategory().getId() : null,
                    p.getSubcategory() != null ? p.getSubcategory().getName() : null,
                    p.getAttributes() != null ? p.getAttributes() : Collections.emptyMap()
            );
        }
    }
}