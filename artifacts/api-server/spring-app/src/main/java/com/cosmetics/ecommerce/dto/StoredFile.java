package com.cosmetics.ecommerce.dto;

public record StoredFile(
        String fileName,
        String relativeUrl,
        String fullUrl,
        long size,
        String contentType
) {}

