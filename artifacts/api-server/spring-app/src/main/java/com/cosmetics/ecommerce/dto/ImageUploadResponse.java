package com.cosmetics.ecommerce.dto;

public record ImageUploadResponse(
        String imageUrl,
        String relativeUrl,
        String fileName,
        long size,
        String contentType
) {
    public static ImageUploadResponse from(StoredFile stored) {
        return new ImageUploadResponse(
                stored.fullUrl(),
                stored.relativeUrl(),
                stored.fileName(),
                stored.size(),
                stored.contentType()
        );
    }
}

