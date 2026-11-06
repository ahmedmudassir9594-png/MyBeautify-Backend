package com.cosmetics.ecommerce.payment;

import com.cosmetics.ecommerce.enums.PaymentMethod;
import com.cosmetics.ecommerce.exception.BadRequestException;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

@Component
@Primary
public class ProductionPaymentGateway implements PaymentGateway {

    @Override
    public PaymentResult charge(BigDecimal amount, PaymentMethod method) {
        if (method == null) {
            throw new BadRequestException("Payment method is required.");
        }
        if (method.isCod()) {
            String txnId = "COD-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
            return new PaymentResult(txnId, "CASH_ON_DELIVERY", true);
        }
        if (method.isOnline()) {
            throw new BadRequestException(
                    "Online payments (" + method + ") must be initiated via Razorpay checkout at /api/payments/razorpay/create-order."
            );
        }
        throw new BadRequestException("Unsupported payment method: " + method);
    }
}

