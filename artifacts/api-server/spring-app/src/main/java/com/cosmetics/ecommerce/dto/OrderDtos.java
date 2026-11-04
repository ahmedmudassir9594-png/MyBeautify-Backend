package com.cosmetics.ecommerce.dto;

import com.cosmetics.ecommerce.entity.*;
import com.cosmetics.ecommerce.enums.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class OrderDtos {
    private OrderDtos() {}
    public record OrderRequest(@NotNull Long shippingAddressId, @NotNull PaymentMethod paymentMethod, String couponCode) {}
    public record AddressRequest(@NotBlank String fullName, @NotBlank String phoneNumber, @NotBlank String addressLine1,
                                 String addressLine2, @NotBlank String city, String state, @NotBlank String postalCode,
                                 @NotBlank String country, AddressType addressType, boolean isDefault) {}
    public record AddressResponse(Long id, String fullName, String phoneNumber, String addressLine1, String addressLine2,
                                  String city, String state, String postalCode, String country, AddressType addressType,
                                  boolean isDefault) {
        public static AddressResponse from(Address a) { return new AddressResponse(a.getId(), a.getFullName(), a.getPhoneNumber(),
                a.getAddressLine1(), a.getAddressLine2(), a.getCity(), a.getState(), a.getPostalCode(), a.getCountry(),
                a.getAddressType(), a.isDefault()); }
    }
    public record OrderItemResponse(
            Long productId,
            String productName,
            int quantity,
            BigDecimal price,
            BigDecimal subtotal,
            Long comboId,
            String comboGroupId,
            String comboType,
            String comboName,
            BigDecimal originalPrice,
            BigDecimal discountAmount
    ) {
        public OrderItemResponse(Long productId, String productName, int quantity, BigDecimal price, BigDecimal subtotal) {
            this(productId, productName, quantity, price, subtotal, null, null, null, null, null, null);
        }

        public static OrderItemResponse from(OrderItem i) {
            return new OrderItemResponse(
                    i.getProduct().getId(),
                    i.getProductName(),
                    i.getQuantity(),
                    i.getPrice(),
                    i.getSubtotal(),
                    i.getComboId(),
                    i.getComboGroupId(),
                    i.getComboType() != null ? i.getComboType().name() : null,
                    i.getComboName(),
                    i.getOriginalPrice(),
                    i.getDiscountAmount()
            );
        }
    }
    public record OrderResponse(Long id, String orderNumber, BigDecimal subtotal, BigDecimal discountAmount, BigDecimal shippingCharge,
                                BigDecimal taxAmount, BigDecimal totalAmount, PaymentStatus paymentStatus, OrderStatus orderStatus,
                                PaymentMethod paymentMethod, AddressResponse shippingAddress, List<OrderItemResponse> items, Instant createdAt) {
        public static OrderResponse from(Order o) { return new OrderResponse(o.getId(), o.getOrderNumber(), o.getSubtotal(), o.getDiscountAmount(),
                o.getShippingCharge(), o.getTaxAmount(), o.getTotalAmount(), o.getPaymentStatus(), o.getOrderStatus(), o.getPaymentMethod(),
                AddressResponse.from(o.getShippingAddress()), o.getItems().stream().map(OrderItemResponse::from).toList(), o.getCreatedAt()); }
    }
}
