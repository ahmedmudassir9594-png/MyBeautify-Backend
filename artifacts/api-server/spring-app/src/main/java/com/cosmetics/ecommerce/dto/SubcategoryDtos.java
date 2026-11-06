package com.cosmetics.ecommerce.dto;

import com.cosmetics.ecommerce.entity.Subcategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class SubcategoryDtos {

    private SubcategoryDtos() {}

    public record SubcategoryRequest(
            @NotBlank(message = "Subcategory name is required.")
            @Size(max = 120, message = "Subcategory name must not exceed 120 characters.")
            String name,

            @NotNull(message = "Parent category ID is required.")
            Long categoryId,

            @Size(max = 1000, message = "Description must not exceed 1000 characters.")
            String description,

            String imageUrl,
            Boolean active
    ) {}

    public record SubcategoryResponse(
            Long id,
            String name,
            String slug,
            String description,
            String imageUrl,
            Long categoryId,
            String categoryName,
            boolean active,
            long productCount,
            Instant createdAt,
            Instant updatedAt
    ) {
        public static SubcategoryResponse from(Subcategory s) {
            return from(s, 0L);
        }

        public static SubcategoryResponse from(Subcategory s, long productCount) {
            return new SubcategoryResponse(
                    s.getId(),
                    s.getName(),
                    s.getSlug(),
                    s.getDescription(),
                    s.getImageUrl(),
                    s.getCategory() != null ? s.getCategory().getId() : null,
                    s.getCategory() != null ? s.getCategory().getName() : null,
                    s.isActive(),
                    productCount,
                    s.getCreatedAt(),
                    s.getUpdatedAt()
            );
        }
    }

    public record SubcategoryStatusUpdateRequest(
            @NotNull(message = "Active status is required.")
            Boolean active
    ) {}
}

