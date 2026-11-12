package com.cosmetics.ecommerce.enums;

public enum PaymentStatus {
    PENDING,
    AUTHORIZED,
    SUCCESS,
    FAILED,
    REFUND_PENDING,
    REFUNDED,
    PARTIALLY_REFUNDED,
    REFUND_REVIEW_REQUIRED,
    ORPHAN_CAPTURED
}
