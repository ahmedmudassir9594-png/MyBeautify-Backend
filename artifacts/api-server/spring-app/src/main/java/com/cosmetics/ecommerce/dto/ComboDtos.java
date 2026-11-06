package com.cosmetics.ecommerce.dto;

import com.cosmetics.ecommerce.entity.Combo;
import com.cosmetics.ecommerce.entity.ComboDiscountRule;
import com.cosmetics.ecommerce.entity.ComboItem;
import com.cosmetics.ecommerce.entity.Product;
import com.cosmetics.ecommerce.enums.DiscountType;
import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class ComboDtos {

    private ComboDtos() {}

    // --- Predefined Combo DTOs ---

    public record ComboItemRequest(
            @NotNull Long productId,
            @Min(1) int quantity,
            BigDecimal customPrice
    ) {}

    public record ComboItemResponse(
            Long id,
            Long productId,
            String productName,
            String productSlug,
            String imageUrl,
            int quantity,
            BigDecimal basePrice,
            BigDecimal customPrice,
            BigDecimal effectivePrice,
            BigDecimal subtotal,
            int availableStock,
            boolean inStock
    ) {
        public static ComboItemResponse from(ComboItem item) {
            Product p = item.getProduct();
            BigDecimal base = p != null ? p.sellingPrice() : BigDecimal.ZERO;
            BigDecimal effective = item.effectivePrice();
            int stock = p != null ? p.getStockQuantity() : 0;
            boolean inStock = p != null && p.isActive() && stock >= item.getQuantity();
            return new ComboItemResponse(
                    item.getId(),
                    p != null ? p.getId() : null,
                    p != null ? p.getName() : "",
                    p != null ? p.getSlug() : "",
                    p != null ? p.getImageUrl() : null,
                    item.getQuantity(),
                    base,
                    item.getCustomPrice(),
                    effective,
                    item.subtotal(),
                    stock,
                    inStock
            );
        }
    }

    public record ComboRequest(
            @NotBlank @Size(max = 180) String name,
            String slug,
            String description,
            @JsonAlias({"imageUrl", "image_url", "imageFileName", "image_file_name"})
            String imageFileName,
            @NotNull @DecimalMin("0.01") BigDecimal sellingPrice,
            BigDecimal originalPrice,
            Instant startDate,
            Instant endDate,
            boolean active,
            boolean featured,
            @NotEmpty @Valid List<ComboItemRequest> items
    ) {}

    public record ComboResponse(
            Long id,
            String name,
            String slug,
            String description,
            String imageFileName,
            String imageUrl,
            BigDecimal originalPrice,
            BigDecimal sellingPrice,
            BigDecimal savingsAmount,
            BigDecimal savingsPercentage,
            boolean inStock,
            boolean active,
            boolean featured,
            Instant startDate,
            Instant endDate,
            List<ComboItemResponse> items,
            Instant createdAt,
            Instant updatedAt
    ) {
        public static ComboResponse from(Combo combo) {
            List<ComboItemResponse> itemResponses = combo.getItems() != null
                    ? combo.getItems().stream().map(ComboItemResponse::from).toList()
                    : List.of();
            boolean inStock = combo.isActive()
                    && !itemResponses.isEmpty()
                    && itemResponses.stream().allMatch(ComboItemResponse::inStock);

            String fileName = combo.getImageFileName();
            String imageUrl = null;
            if (fileName != null && !fileName.isBlank()) {
                if (fileName.startsWith("http://") || fileName.startsWith("https://") || fileName.startsWith("/")) {
                    imageUrl = fileName;
                } else {
                    imageUrl = "/api/uploads/" + fileName;
                }
            }

            return new ComboResponse(
                    combo.getId(),
                    combo.getName(),
                    combo.getSlug(),
                    combo.getDescription(),
                    fileName,
                    imageUrl,
                    combo.getOriginalPrice(),
                    combo.getSellingPrice(),
                    combo.getDiscountAmount(),
                    combo.getSavingsPercentage(),
                    inStock,
                    combo.isActive(),
                    combo.isFeatured(),
                    combo.getStartDate(),
                    combo.getEndDate(),
                    itemResponses,
                    combo.getCreatedAt(),
                    combo.getUpdatedAt()
            );
        }
    }

    public record AddPredefinedComboToCartRequest(
            @Min(1) int quantity
    ) {
        public AddPredefinedComboToCartRequest() {
            this(1);
        }
    }

    // --- Custom Combo / Build Your Own Combo DTOs ---

    public record CustomComboItemRequest(
            @NotNull Long productId,
            @Min(1) int quantity
    ) {}

    public record CustomComboPreviewRequest(
            @NotEmpty @Valid List<CustomComboItemRequest> items
    ) {}

    public record CustomComboItemPreview(
            Long productId,
            String productName,
            String imageUrl,
            int quantity,
            BigDecimal unitPrice,
            BigDecimal subtotal,
            BigDecimal discountedUnitPrice,
            BigDecimal allocatedDiscount,
            boolean inStock,
            int availableStock
    ) {}

    public record CustomComboPreviewResponse(
            List<CustomComboItemPreview> items,
            int totalItemCount,
            int distinctProductCount,
            BigDecimal originalSubtotal,
            BigDecimal discountAmount,
            BigDecimal finalTotal,
            BigDecimal discountPercentage,
            String appliedRuleName,
            boolean eligible,
            String eligibleMessage
    ) {}

    public record AddCustomComboToCartRequest(
            @NotEmpty @Valid List<CustomComboItemRequest> items
    ) {}

    // --- Combo Discount Rule DTOs ---

    public record ComboDiscountRuleRequest(
            @NotBlank @Size(max = 150) String name,
            String description,
            @Min(1) int minProducts,
            @NotNull DiscountType discountType,
            @NotNull @DecimalMin("0.01") BigDecimal discountValue,
            BigDecimal minSubtotal,
            BigDecimal maxDiscountAmount,
            Instant startDate,
            Instant endDate,
            boolean active
    ) {}

    public record ComboDiscountRuleResponse(
            Long id,
            String name,
            String description,
            int minProducts,
            DiscountType discountType,
            BigDecimal discountValue,
            BigDecimal minSubtotal,
            BigDecimal maxDiscountAmount,
            Instant startDate,
            Instant endDate,
            boolean active,
            Instant createdAt,
            Instant updatedAt
    ) {
        public static ComboDiscountRuleResponse from(ComboDiscountRule rule) {
            return new ComboDiscountRuleResponse(
                    rule.getId(),
                    rule.getName(),
                    rule.getDescription(),
                    rule.getMinProducts(),
                    rule.getDiscountType(),
                    rule.getDiscountValue(),
                    rule.getMinSubtotal(),
                    rule.getMaxDiscountAmount(),
                    rule.getStartDate(),
                    rule.getEndDate(),
                    rule.isActive(),
                    rule.getCreatedAt(),
                    rule.getUpdatedAt()
            );
        }
    }

    public record ComboStatusPatchRequest(
            boolean active
    ) {}
}

