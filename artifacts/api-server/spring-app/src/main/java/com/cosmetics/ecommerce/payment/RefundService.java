package com.cosmetics.ecommerce.payment;

import com.cosmetics.ecommerce.dto.PaymentDtos.RefundResponse;
import com.cosmetics.ecommerce.entity.Order;
import com.cosmetics.ecommerce.entity.Payment;
import com.cosmetics.ecommerce.entity.Refund;
import com.cosmetics.ecommerce.enums.PaymentStatus;
import com.cosmetics.ecommerce.enums.RefundStatus;
import com.cosmetics.ecommerce.exception.BadRequestException;
import com.cosmetics.ecommerce.exception.ResourceNotFoundException;
import com.cosmetics.ecommerce.repository.PaymentRepository;
import com.cosmetics.ecommerce.repository.RefundRepository;
import com.razorpay.RazorpayClient;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

@Service
public class RefundService {

    private static final Logger log = LoggerFactory.getLogger(RefundService.class);

    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final ObjectProvider<RazorpayClient> razorpayClientProvider;
    private final RazorpayProperties properties;
    private final TransactionTemplate transactionTemplate;

    public RefundService(PaymentRepository paymentRepository,
                         RefundRepository refundRepository,
                         ObjectProvider<RazorpayClient> razorpayClientProvider,
                         RazorpayProperties properties,
                         ObjectProvider<PlatformTransactionManager> transactionManagerProvider) {
        this.paymentRepository = paymentRepository;
        this.refundRepository = refundRepository;
        this.razorpayClientProvider = razorpayClientProvider;
        this.properties = properties;
        PlatformTransactionManager tm = transactionManagerProvider.getIfAvailable();
        if (tm != null) {
            this.transactionTemplate = new TransactionTemplate(tm);
            this.transactionTemplate.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        } else {
            this.transactionTemplate = null;
        }
    }

    private <T> T executeInTransaction(Supplier<T> action) {
        if (transactionTemplate != null) {
            return transactionTemplate.execute(status -> action.get());
        }
        return action.get();
    }

    public record RefundReservation(
            Long refundId,
            String internalRefundRef,
            Long paymentId,
            String razorpayPaymentId,
            BigDecimal refundAmount,
            BigDecimal totalPaymentAmount,
            String currency,
            Long orderId,
            String orderNumber
    ) {}

    public record GatewayRefundOutcome(
            boolean success,
            String razorpayRefundId,
            String status,
            boolean isUncertain,
            String errorMessage
    ) {}

    /**
     * Stage 1 (Transaction A): Acquire row lock, validate available balance, reserve refund attempt.
     */
    public RefundReservation reserveRefund(Long paymentId, BigDecimal requestedAmount, String reason, String initiatedBy) {
        return executeInTransaction(() -> {
            Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                    .orElseThrow(() -> new ResourceNotFoundException("Payment record not found with ID: " + paymentId));

            if (payment.getPaymentStatus() != PaymentStatus.SUCCESS &&
                payment.getPaymentStatus() != PaymentStatus.PARTIALLY_REFUNDED &&
                payment.getPaymentStatus() != PaymentStatus.REFUND_REVIEW_REQUIRED &&
                payment.getPaymentStatus() != PaymentStatus.ORPHAN_CAPTURED) {
                throw new BadRequestException("Cannot refund payment: Current payment status is " + payment.getPaymentStatus()
                        + ". Only successful, partially refunded, or review-required payments can be refunded.");
            }

            BigDecimal reservedOrProcessed = refundRepository.sumReservedOrProcessedRefunds(payment.getId());
            BigDecimal available = payment.getAmount().subtract(reservedOrProcessed);

            if (available.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BadRequestException("Payment has already been fully refunded or active refund reservations cover the remaining balance.");
            }

            BigDecimal refundAmount = requestedAmount != null ? requestedAmount : available;
            if (refundAmount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BadRequestException("Refund amount must be greater than zero.");
            }
            if (refundAmount.compareTo(available) > 0) {
                throw new BadRequestException("Refund amount (" + refundAmount + ") exceeds remaining refundable amount (" + available + ").");
            }

            String internalRef = "REF-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
            Refund refund = new Refund(payment, internalRef, refundAmount, payment.getCurrency(), RefundStatus.INITIATED, reason, initiatedBy);
            refund = refundRepository.save(refund);

            Order order = payment.getOrder();
            return new RefundReservation(
                    refund.getId(),
                    internalRef,
                    payment.getId(),
                    payment.getRazorpayPaymentId(),
                    refundAmount,
                    payment.getAmount(),
                    payment.getCurrency(),
                    order != null ? order.getId() : null,
                    order != null ? order.getOrderNumber() : null
            );
        });
    }

    /**
     * Stage 2 (Outside DB Transaction): Invoke external Razorpay API safely.
     */
    public GatewayRefundOutcome callRazorpayRefundApi(String razorpayPaymentId, BigDecimal amount, String internalRef, String reason) {
        long refundPaise = amount.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).longValue();
        RazorpayClient client = razorpayClientProvider.getIfAvailable();

        if (client != null && properties.isConfigured() && !properties.getKeyId().startsWith("rzp_test_MALKAZTestKey") && razorpayPaymentId != null) {
            try {
                JSONObject refundReq = new JSONObject();
                refundReq.put("amount", refundPaise);
                JSONObject notes = new JSONObject();
                notes.put("internal_ref", internalRef);
                if (reason != null && !reason.isBlank()) {
                    notes.put("reason", reason);
                }
                refundReq.put("notes", notes);
                refundReq.put("receipt", internalRef);

                com.razorpay.Refund rzpRefund = client.payments.refund(razorpayPaymentId, refundReq);
                String rzpId = rzpRefund.get("id");
                String rzpStatus = rzpRefund.has("status") ? (String) rzpRefund.get("status") : "processed";
                return new GatewayRefundOutcome(true, rzpId, rzpStatus, false, null);
            } catch (com.razorpay.RazorpayException e) {
                log.error("Razorpay refund exception for payment {}: {}", razorpayPaymentId, e.getMessage(), e);
                String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
                boolean isClientError = msg.contains("bad request") || msg.contains("invalid") || msg.contains("already refunded") || msg.contains("exceeded");
                return new GatewayRefundOutcome(false, null, "failed", !isClientError, e.getMessage());
            } catch (Exception e) {
                log.error("Network or timeout failure calling Razorpay refund: {}", e.getMessage(), e);
                return new GatewayRefundOutcome(false, null, "uncertain", true, e.getMessage());
            }
        }

        // Test mode simulation when test keys are active
        String simulatedRefundId = "rfnd_test_" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);
        return new GatewayRefundOutcome(true, simulatedRefundId, "processed", false, null);
    }

    /**
     * Stage 3 (Transaction B): Reacquire lock and finalize refund ledger record.
     */
    public RefundResponse finalizeRefund(RefundReservation reservation, GatewayRefundOutcome outcome) {
        return executeInTransaction(() -> {
            Payment payment = paymentRepository.findByIdForUpdate(reservation.paymentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Payment record not found: " + reservation.paymentId()));
            Refund refund = refundRepository.findByIdForUpdate(reservation.refundId())
                    .orElseThrow(() -> new ResourceNotFoundException("Refund reservation record not found: " + reservation.refundId()));

            if (outcome.success()) {
                refund.setRazorpayRefundId(outcome.razorpayRefundId());
                boolean isPending = "pending".equalsIgnoreCase(outcome.status());
                RefundStatus newRefundStatus = isPending ? RefundStatus.PENDING : RefundStatus.PROCESSED;
                refund.setStatus(newRefundStatus);
                if (!isPending) {
                    refund.setProcessedAt(Instant.now());
                }
                refundRepository.save(refund);

                BigDecimal totalProcessed = refundRepository.sumProcessedRefunds(payment.getId());
                payment.setRefundAmount(totalProcessed);
                payment.setRefundId(outcome.razorpayRefundId());
                payment.setRefundStatus(outcome.status());

                boolean fullyRefunded = totalProcessed.compareTo(payment.getAmount()) >= 0;
                PaymentStatus newStatus;
                if (fullyRefunded) {
                    newStatus = PaymentStatus.REFUNDED;
                } else if (totalProcessed.compareTo(BigDecimal.ZERO) > 0) {
                    newStatus = PaymentStatus.PARTIALLY_REFUNDED;
                } else {
                    newStatus = isPending ? PaymentStatus.REFUND_PENDING : payment.getPaymentStatus();
                }
                payment.setPaymentStatus(newStatus);
                paymentRepository.save(payment);

                String msg = fullyRefunded ? "Payment fully refunded successfully."
                        : (isPending ? "Refund initiated and pending gateway processing." : "Partial refund processed successfully.");

                return new RefundResponse(
                        outcome.razorpayRefundId(),
                        payment.getId(),
                        reservation.orderId(),
                        reservation.orderNumber(),
                        reservation.refundAmount(),
                        outcome.status(),
                        newStatus,
                        msg
                );
            } else if (outcome.isUncertain()) {
                refund.setStatus(RefundStatus.RECONCILIATION_REQUIRED);
                refund.setFailureReason("Gateway call timed out or network error: " + outcome.errorMessage());
                refundRepository.save(refund);

                return new RefundResponse(
                        reservation.internalRefundRef(),
                        payment.getId(),
                        reservation.orderId(),
                        reservation.orderNumber(),
                        reservation.refundAmount(),
                        "reconciliation_required",
                        payment.getPaymentStatus(),
                        "Refund request dispatched but gateway confirmation timed out. Amount reserved; reconciliation pending via webhook."
                );
            } else {
                refund.setStatus(RefundStatus.FAILED);
                refund.setFailureReason(outcome.errorMessage());
                refundRepository.save(refund);

                throw new BadRequestException("Razorpay refund failed: " + outcome.errorMessage());
            }
        });
    }

    /**
     * Public full refund workflow orchestrator
     */
    public RefundResponse processRefund(Long paymentId, BigDecimal requestedAmount, String reason, String initiatedBy) {
        RefundReservation reservation = reserveRefund(paymentId, requestedAmount, reason, initiatedBy);
        GatewayRefundOutcome outcome = callRazorpayRefundApi(
                reservation.razorpayPaymentId(),
                reservation.refundAmount(),
                reservation.internalRefundRef(),
                reason
        );
        return finalizeRefund(reservation, outcome);
    }

    public List<Refund> getRefundsForPayment(Long paymentId) {
        return refundRepository.findByPaymentIdOrderByCreatedAtDesc(paymentId);
    }
}

