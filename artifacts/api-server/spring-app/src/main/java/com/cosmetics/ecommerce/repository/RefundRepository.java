package com.cosmetics.ecommerce.repository;

import com.cosmetics.ecommerce.entity.Refund;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface RefundRepository extends JpaRepository<Refund, Long> {

    Optional<Refund> findByRazorpayRefundId(String razorpayRefundId);

    Optional<Refund> findByInternalRefundRef(String internalRefundRef);

    List<Refund> findByPaymentIdOrderByCreatedAtDesc(Long paymentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Refund r WHERE r.id = :id")
    Optional<Refund> findByIdForUpdate(@Param("id") Long id);

    @Query("SELECT COALESCE(SUM(r.amount), 0) FROM Refund r WHERE r.payment.id = :paymentId AND r.status IN (com.cosmetics.ecommerce.enums.RefundStatus.INITIATED, com.cosmetics.ecommerce.enums.RefundStatus.PENDING, com.cosmetics.ecommerce.enums.RefundStatus.PROCESSED, com.cosmetics.ecommerce.enums.RefundStatus.RECONCILIATION_REQUIRED)")
    BigDecimal sumReservedOrProcessedRefunds(@Param("paymentId") Long paymentId);

    @Query("SELECT COALESCE(SUM(r.amount), 0) FROM Refund r WHERE r.payment.id = :paymentId AND r.status = com.cosmetics.ecommerce.enums.RefundStatus.PROCESSED")
    BigDecimal sumProcessedRefunds(@Param("paymentId") Long paymentId);

    boolean existsByRazorpayRefundId(String razorpayRefundId);

    @Query("SELECT r FROM Refund r WHERE r.status IN (com.cosmetics.ecommerce.enums.RefundStatus.PENDING, com.cosmetics.ecommerce.enums.RefundStatus.RECONCILIATION_REQUIRED) AND r.createdAt <= :before ORDER BY r.createdAt ASC")
    List<Refund> findPendingRefundsOlderThan(@Param("before") java.time.Instant before, org.springframework.data.domain.Pageable pageable);
}

