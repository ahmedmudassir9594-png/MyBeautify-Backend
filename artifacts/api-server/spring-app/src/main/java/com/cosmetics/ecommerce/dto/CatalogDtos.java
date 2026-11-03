package com.cosmetics.ecommerce.dto;

import com.cosmetics.ecommerce.entity.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;

public final class CatalogDtos {
    private CatalogDtos() {}
    public record CategoryResponse(Long id, String name, String description, String imageUrl, boolean active) {
        public static CategoryResponse from(Category c) { return new CategoryResponse(c.getId(), c.getName(), c.getDescription(), c.getImageUrl(), c.isActive()); }
    }
    public record CategoryRequest(@NotBlank @Size(max = 120) String name, @Size(max = 1000) String description, String imageUrl, Boolean active) {}

    public record ProductRequest(@NotBlank @Size(max = 180) String name, @NotBlank @Size(max = 80) String sku,
                                 @NotNull @Positive BigDecimal price, @PositiveOrZero BigDecimal discountPrice,
                                 @PositiveOrZero int stockQuantity, @PositiveOrZero int lowStockThreshold,
                                 @NotNull Long categoryId, String description, String shortDescription,
                                 String imageUrl, Boolean active, Boolean featured, String ingredients, String benefits,
                                 String howToUse, String skinType, String shade, String size, Instant expiryDate) {}
    public record ProductResponse(Long id, String name, String slug, String sku, String description, String shortDescription,
                                  BigDecimal price, BigDecimal discountPrice, BigDecimal sellingPrice, int stockQuantity,
                                  int lowStockThreshold, Long categoryId, String categoryName,
                                  String imageUrl, boolean active, boolean featured, String ingredients, String benefits,
                                  String howToUse, String skinType, String shade, String size, Instant expiryDate, double averageRating) {
        public static ProductResponse from(Product p, double rating) {
            return new ProductResponse(p.getId(), p.getName(), p.getSlug(), p.getSku(), p.getDescription(), p.getShortDescription(),
                    p.getPrice(), p.getDiscountPrice(), p.sellingPrice(), p.getStockQuantity(), p.getLowStockThreshold(),
                    p.getCategory().getId(), p.getCategory().getName(),
                    p.getImageUrl(), p.isActive(), p.isFeatured(), p.getIngredients(), p.getBenefits(), p.getHowToUse(),
                    p.getSkinType(), p.getShade(), p.getSize(), p.getExpiryDate(), rating);
        }
    }
}