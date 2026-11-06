package com.cosmetics.ecommerce.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum PaymentMethod {
    UPI("UPI"),
    UPI_QR("UPI_QR"),
    NET_BANKING("NET_BANKING"),
    CASH_ON_DELIVERY("CASH_ON_DELIVERY"),
    COD("CASH_ON_DELIVERY");

    private final String standardName;

    PaymentMethod(String standardName) {
        this.standardName = standardName;
    }

    @JsonValue
    public String getStandardName() {
        return standardName;
    }

    public boolean isOnline() {
        return this == UPI || this == UPI_QR || this == NET_BANKING;
    }

    public boolean isCod() {
        return this == CASH_ON_DELIVERY || this == COD;
    }

    @JsonCreator
    public static PaymentMethod fromString(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Payment method is required.");
        }
        String normalized = value.trim().toUpperCase();
        if ("MOCK".equals(normalized)) {
            throw new IllegalArgumentException("MOCK payment method is strictly forbidden in production.");
        }
        if ("CREDIT_CARD".equals(normalized) || "DEBIT_CARD".equals(normalized) || "CARD".equals(normalized)) {
            throw new IllegalArgumentException("Card payments (Credit/Debit cards) are not accepted. MALKAZ exclusively supports UPI, Net Banking, and Cash on Delivery.");
        }
        if ("COD".equals(normalized) || "CASH_ON_DELIVERY".equals(normalized)) {
            return CASH_ON_DELIVERY;
        }
        if ("UPI".equals(normalized)) {
            return UPI;
        }
        if ("UPI_QR".equals(normalized) || "UPI-QR".equals(normalized) || "UPIQR".equals(normalized)) {
            return UPI_QR;
        }
        if ("NET_BANKING".equals(normalized) || "NETBANKING".equals(normalized) || "NET_BANK".equals(normalized)) {
            return NET_BANKING;
        }
        try {
            return PaymentMethod.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid payment method: '" + value + "'. Supported methods: UPI, UPI_QR, NET_BANKING, CASH_ON_DELIVERY.");
        }
    }
}
