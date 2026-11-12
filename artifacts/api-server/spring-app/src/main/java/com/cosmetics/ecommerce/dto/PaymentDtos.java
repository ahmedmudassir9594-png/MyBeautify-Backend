package com.cosmetics.ecommerce.dto;

import com.cosmetics.ecommerce.entity.Payment;
import com.cosmetics.ecommerce.enums.PaymentMethod;
import com.cosmetics.ecommerce.enums.PaymentStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.Instant;

public final class PaymentDtos {
    private PaymentDtos() {}

    public record RazorpayOrderRequest(
            @NotNull(message = "Shipping address ID is required")
            Long shippingAddressId,

            @NotNull(message = "Payment method is required")
            PaymentMethod paymentMethod,

            String couponCode
    ) {}

    public record RazorpayOrderResponse(
            Long orderId,
            String orderNumber,
            String razorpayOrderId,
            BigDecimal amount,
            Long amountInPaise,
            String currency,
            String razorpayKeyId,
            String customerName,
            String customerEmail,
            String customerPhone,
            String companyName,
            String themeColor,
            PaymentMethod paymentMethod
    ) {}

    public record RazorpayVerifyRequest(
            @NotNull(message = "Order ID is required")
            Long orderId,

            @NotBlank(message = "Razorpay order ID is required")
            String razorpayOrderId,

            @NotBlank(message = "Razorpay payment ID is required")
            String razorpayPaymentId,

            @NotBlank(message = "Razorpay signature is required")
            String razorpaySignature
    ) {}

    public record RazorpayVerifyResponse(
            Long orderId,
            String orderNumber,
            PaymentStatus paymentStatus,
            String transactionId,
            String razorpayPaymentId,
            BigDecimal amount,
            String message
    ) {}

    public record RazorpayQrRequest(
            @NotNull(message = "Shipping address ID is required")
            Long shippingAddressId,

            String couponCode
    ) {}

    public record RazorpayQrResponse(
            Long orderId,
            String orderNumber,
            String razorpayOrderId,
            String qrCodeId,
            String qrImageUrl,
            String paymentUrl,
            BigDecimal amount,
            Long amountInPaise,
            String currency,
            String status
    ) {}

    public record RefundRequest(
            @Positive(message = "Refund amount must be greater than zero")
            BigDecimal amount,

            String reason
    ) {}

    public record RefundResponse(
            String refundId,
            Long paymentId,
            Long orderId,
            String orderNumber,
            BigDecimal refundAmount,
            String refundStatus,
            PaymentStatus paymentStatus,
            String message
    ) {}

    public record RefundRecordResponse(
            Long id,
            Long paymentId,
            String internalRefundRef,
            String razorpayRefundId,
            BigDecimal amount,
            String currency,
            com.cosmetics.ecommerce.enums.RefundStatus status,
            String reason,
            String initiatedBy,
            Instant createdAt,
            Instant processedAt,
            String failureReason
    ) {
        public static RefundRecordResponse from(com.cosmetics.ecommerce.entity.Refund refund) {
            return new RefundRecordResponse(
                    refund.getId(),
                    refund.getPayment().getId(),
                    refund.getInternalRefundRef(),
                    refund.getRazorpayRefundId(),
                    refund.getAmount(),
                    refund.getCurrency(),
                    refund.getStatus(),
                    refund.getReason(),
                    refund.getInitiatedBy(),
                    refund.getCreatedAt(),
                    refund.getProcessedAt(),
                    refund.getFailureReason()
            );
        }
    }

    public record PaymentStatusResponse(
            Long paymentId,
            Long orderId,
            String orderNumber,
            String transactionId,
            String paymentGateway,
            PaymentMethod paymentMethod,
            PaymentStatus paymentStatus,
            BigDecimal amount,
            String currency,
            String razorpayOrderId,
            String razorpayPaymentId,
            Instant paidAt,
            Instant createdAt,
            String failureCode,
            String failureReason,
            String refundId,
            String refundStatus,
            BigDecimal refundAmount
    ) {
        public static PaymentStatusResponse from(Payment payment) {
            return new PaymentStatusResponse(
                    payment.getId(),
                    payment.getOrder() != null ? payment.getOrder().getId() : null,
                    payment.getOrder() != null ? payment.getOrder().getOrderNumber() : null,
                    payment.getTransactionId(),
                    payment.getPaymentGateway(),
                    payment.getPaymentMethod(),
                    payment.getPaymentStatus(),
                    payment.getAmount(),
                    payment.getCurrency(),
                    payment.getRazorpayOrderId(),
                    payment.getRazorpayPaymentId(),
                    payment.getPaidAt(),
                    payment.getCreatedAt(),
                    payment.getFailureCode(),
                    payment.getFailureReason(),
                    payment.getRefundId(),
                    payment.getRefundStatus(),
                    payment.getRefundAmount()
            );
        }
    }
}

