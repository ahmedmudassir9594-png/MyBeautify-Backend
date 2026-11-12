package com.cosmetics.ecommerce.repository;

import com.cosmetics.ecommerce.entity.Payment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByTransactionId(String transactionId);
    Optional<Payment> findByOrderId(Long orderId);
    Optional<Payment> findByRazorpayOrderId(String razorpayOrderId);
    Optional<Payment> findByRazorpayPaymentId(String razorpayPaymentId);
    Optional<Payment> findByOrderOrderNumber(String orderNumber);
    java.util.List<Payment> findByPaymentStatus(com.cosmetics.ecommerce.enums.PaymentStatus paymentStatus);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.id = :id")
    Optional<Payment> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.order.id = :orderId")
    Optional<Payment> findByOrderIdForUpdate(@Param("orderId") Long orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.razorpayOrderId = :razorpayOrderId")
    Optional<Payment> findByRazorpayOrderIdForUpdate(@Param("razorpayOrderId") String razorpayOrderId);

    @Query("SELECT p FROM Payment p WHERE p.paymentStatus = :paymentStatus AND p.paymentGateway = 'RAZORPAY' AND p.razorpayOrderId IS NOT NULL AND p.createdAt <= :before ORDER BY p.createdAt ASC")
    java.util.List<Payment> findPendingRazorpayPaymentsOlderThan(
            @Param("paymentStatus") com.cosmetics.ecommerce.enums.PaymentStatus paymentStatus,
            @Param("before") java.time.Instant before,
            org.springframework.data.domain.Pageable pageable);
}

