package com.cosmetics.ecommerce.repository;

import com.cosmetics.ecommerce.entity.PaymentWebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentWebhookEventRepository extends JpaRepository<PaymentWebhookEvent, Long> {
    Optional<PaymentWebhookEvent> findByRazorpayEventId(String razorpayEventId);
    boolean existsByRazorpayEventId(String razorpayEventId);
}

