package com.cosmetics.ecommerce.dto;

import com.cosmetics.ecommerce.entity.HeroBanner;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;

public final class BannerDtos {
    private BannerDtos() {}

    public record HeroBannerRequest(
            @NotBlank(message = "Banner title is required.")
            String title,
            String subtitle,
            String description,
            String ctaText,
            String ctaLink,
            @NotBlank(message = "Banner image URL is required.")
            String imageUrl,
            Integer displayOrder,
            Boolean active
    ) {}

    public record HeroBannerResponse(
            Long id,
            String title,
            String subtitle,
            String description,
            String ctaText,
            String ctaLink,
            String imageUrl,
            int displayOrder,
            boolean active,
            Instant createdAt
    ) {
        public static HeroBannerResponse from(HeroBanner b) {
            return new HeroBannerResponse(
                    b.getId(),
                    b.getTitle(),
                    b.getSubtitle(),
                    b.getDescription(),
                    b.getCtaText(),
                    b.getCtaLink(),
                    b.getImageUrl(),
                    b.getDisplayOrder(),
                    b.isActive(),
                    b.getCreatedAt()
            );
        }
    }
}

