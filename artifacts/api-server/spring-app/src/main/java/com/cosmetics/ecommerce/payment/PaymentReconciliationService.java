package com.cosmetics.ecommerce.payment;

import com.cosmetics.ecommerce.entity.Order;
import com.cosmetics.ecommerce.entity.Payment;
import com.cosmetics.ecommerce.entity.Refund;
import com.cosmetics.ecommerce.enums.AuditAction;
import com.cosmetics.ecommerce.enums.AuditResult;
import com.cosmetics.ecommerce.enums.OrderStatus;
import com.cosmetics.ecommerce.enums.PaymentStatus;
import com.cosmetics.ecommerce.enums.RefundStatus;
import com.cosmetics.ecommerce.repository.OrderRepository;
import com.cosmetics.ecommerce.repository.PaymentRepository;
import com.cosmetics.ecommerce.repository.RefundRepository;
import com.cosmetics.ecommerce.service.AuditLogService;
import com.cosmetics.ecommerce.service.OrderService;
import com.razorpay.RazorpayClient;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
public class PaymentReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(PaymentReconciliationService.class);
    private static final int BATCH_SIZE = 20;

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final RefundRepository refundRepository;
    private final ObjectProvider<OrderService> orderServiceProvider;
    private final RefundService refundService;
    private final ObjectProvider<RazorpayClient> razorpayClientProvider;
    private final RazorpayProperties razorpayProperties;
    private final ObjectProvider<AuditLogService> auditLogServiceProvider;
    private final TransactionTemplate transactionTemplate;

    public PaymentReconciliationService(PaymentRepository paymentRepository,
                                        OrderRepository orderRepository,
                                        RefundRepository refundRepository,
                                        ObjectProvider<OrderService> orderServiceProvider,
                                        RefundService refundService,
                                        ObjectProvider<RazorpayClient> razorpayClientProvider,
                                        RazorpayProperties razorpayProperties,
                                        ObjectProvider<AuditLogService> auditLogServiceProvider,
                                        ObjectProvider<PlatformTransactionManager> transactionManagerProvider) {
        this.paymentRepository = paymentRepository;
        this.orderRepository = orderRepository;
        this.refundRepository = refundRepository;
        this.orderServiceProvider = orderServiceProvider;
        this.refundService = refundService;
        this.razorpayClientProvider = razorpayClientProvider;
        this.razorpayProperties = razorpayProperties;
        this.auditLogServiceProvider = auditLogServiceProvider;
        PlatformTransactionManager tm = transactionManagerProvider.getIfAvailable();
        this.transactionTemplate = tm != null ? new TransactionTemplate(tm) : null;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartupReconciliation() {
        log.info("Starting initial bounded payment and refund reconciliation check...");
        try {
            reconcileAll();
        } catch (Exception e) {
            log.warn("Startup payment reconciliation encountered non-fatal error: {}", e.getMessage());
        }
    }

    @Scheduled(fixedDelay = 900000, initialDelay = 120000) // Runs every 15 minutes
    public void scheduledReconciliation() {
        log.info("Executing scheduled payment and refund reconciliation...");
        try {
            reconcileAll();
        } catch (Exception e) {
            log.error("Scheduled payment reconciliation failed: {}", e.getMessage(), e);
        }
    }

    public void reconcileAll() {
        reconcilePendingPayments();
        reconcilePendingRefunds();
    }

    public int reconcilePendingPayments() {
        Instant threshold = Instant.now().minus(Duration.ofMinutes(15));
        List<Payment> pendingPayments = paymentRepository.findPendingRazorpayPaymentsOlderThan(
                PaymentStatus.PENDING, threshold, PageRequest.of(0, BATCH_SIZE)
        );

        int reconciledCount = 0;
        for (Payment payment : pendingPayments) {
            try {
                boolean reconciled = reconcileSinglePayment(payment);
                if (reconciled) reconciledCount++;
            } catch (Exception e) {
                log.warn("Error reconciling pending payment {}: {}", payment.getId(), e.getMessage());
            }
        }
        return reconciledCount;
    }

    public boolean claimPaymentForReconciliation(Long paymentId) {
        if (transactionTemplate == null) return true;
        return Boolean.TRUE.equals(transactionTemplate.execute(status -> {
            Payment p = paymentRepository.findByIdForUpdate(paymentId).orElse(null);
            if (p == null || p.getPaymentStatus() != PaymentStatus.PENDING) {
                return false;
            }
            // Active lease check: 5-minute lease expiry
            if ("RECONCILING".equals(p.getFailureCode()) && p.getUpdatedAt() != null
                    && p.getUpdatedAt().isAfter(Instant.now().minus(Duration.ofMinutes(5)))) {
                log.info("Payment {} is actively leased by another instance. Skipping.", paymentId);
                return false;
            }
            p.setFailureCode("RECONCILING");
            paymentRepository.save(p);
            return true;
        }));
    }

    public void releasePaymentLease(Long paymentId) {
        if (transactionTemplate == null) return;
        transactionTemplate.executeWithoutResult(status -> {
            Payment p = paymentRepository.findByIdForUpdate(paymentId).orElse(null);
            if (p != null && p.getPaymentStatus() == PaymentStatus.PENDING && "RECONCILING".equals(p.getFailureCode())) {
                p.setFailureCode(null);
                paymentRepository.save(p);
            }
        });
    }

    public boolean claimRefundForReconciliation(Long refundId) {
        if (transactionTemplate == null) return true;
        return Boolean.TRUE.equals(transactionTemplate.execute(status -> {
            Refund r = refundRepository.findByIdForUpdate(refundId).orElse(null);
            if (r == null || (r.getStatus() != RefundStatus.PENDING && r.getStatus() != RefundStatus.RECONCILIATION_REQUIRED)) {
                return false;
            }
            // Active lease check: 5-minute lease expiry
            if ("RECONCILING".equals(r.getFailureReason()) && r.getUpdatedAt() != null
                    && r.getUpdatedAt().isAfter(Instant.now().minus(Duration.ofMinutes(5)))) {
                log.info("Refund {} is actively leased by another instance. Skipping.", refundId);
                return false;
            }
            r.setFailureReason("RECONCILING");
            refundRepository.save(r);
            return true;
        }));
    }

    public void releaseRefundLease(Long refundId, String previousReason) {
        if (transactionTemplate == null) return;
        transactionTemplate.executeWithoutResult(status -> {
            Refund r = refundRepository.findByIdForUpdate(refundId).orElse(null);
            if (r != null && (r.getStatus() == RefundStatus.PENDING || r.getStatus() == RefundStatus.RECONCILIATION_REQUIRED)
                    && "RECONCILING".equals(r.getFailureReason())) {
                r.setFailureReason(previousReason);
                refundRepository.save(r);
            }
        });
    }

    private boolean reconcileSinglePayment(Payment payment) {
        // Multi-instance distributed claim before any external gateway API call
        if (!claimPaymentForReconciliation(payment.getId())) {
            return false;
        }

        RazorpayClient client = razorpayClientProvider.getIfAvailable();
        if (client == null || !razorpayProperties.isConfigured() || razorpayProperties.getKeyId().startsWith("rzp_test_MALKAZTestKey")) {
            releasePaymentLease(payment.getId());
            return false;
        }

        String razorpayOrderId = payment.getRazorpayOrderId();
        if (razorpayOrderId == null || razorpayOrderId.isBlank()) {
            releasePaymentLease(payment.getId());
            return false;
        }

        try {
            List<com.razorpay.Payment> rzpPayments = client.orders.fetchPayments(razorpayOrderId);
            if (rzpPayments == null || rzpPayments.isEmpty()) {
                // If payment is older than 2 hours with no gateway attempts, mark it failed and order cancelled
                if (payment.getCreatedAt() != null && payment.getCreatedAt().isBefore(Instant.now().minus(Duration.ofHours(2)))) {
                    if (transactionTemplate != null) {
                        transactionTemplate.executeWithoutResult(status -> {
                            Payment p = paymentRepository.findByIdForUpdate(payment.getId()).orElse(payment);
                            if (p.getPaymentStatus() == PaymentStatus.PENDING) {
                                p.setPaymentStatus(PaymentStatus.FAILED);
                                p.setFailureCode("PENDING_CHECKOUT_TIMEOUT");
                                p.setFailureReason("Checkout was abandoned without payment completion.");
                                paymentRepository.save(p);

                                if (p.getOrder() != null) {
                                    Order o = orderRepository.findByIdForUpdate(p.getOrder().getId()).orElse(p.getOrder());
                                    if (o.getOrderStatus() == OrderStatus.PENDING) {
                                        o.setOrderStatus(OrderStatus.CANCELLED);
                                        orderRepository.save(o);
                                    }
                                }
                            }
                        });
                    }
                    return false;
                }
                releasePaymentLease(payment.getId());
                return false;
            }

            for (com.razorpay.Payment rzpPayment : rzpPayments) {
                String status = rzpPayment.get("status");
                if ("captured".equalsIgnoreCase(status)) {
                    String rzpPaymentId = rzpPayment.get("id");
                    Number amtObj = rzpPayment.get("amount");
                    long actualPaise = amtObj.longValue();
                    String currency = rzpPayment.has("currency") ? (String) rzpPayment.get("currency") : "INR";

                    long expectedPaise = payment.getAmount().multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).longValue();
                    if (actualPaise >= expectedPaise && "INR".equalsIgnoreCase(currency)) {
                        OrderService orderService = orderServiceProvider.getIfAvailable();
                        if (orderService != null && payment.getOrder() != null) {
                            orderService.finalizeSuccessfulOrder(payment.getOrder().getId(), rzpPaymentId, null, payment.getOrder().getUser());
                            log.info("Reconciled captured payment {} for order {}", rzpPaymentId, payment.getOrder().getOrderNumber());
                            return true;
                        }
                    }
                }
            }
            releasePaymentLease(payment.getId());
        } catch (Exception e) {
            log.warn("Failed to query Razorpay order payments for {}: {}", razorpayOrderId, e.getMessage());
            releasePaymentLease(payment.getId());
        }
        return false;
    }

    public int reconcilePendingRefunds() {
        Instant threshold = Instant.now().minus(Duration.ofMinutes(10));
        List<Refund> pendingRefunds = refundRepository.findPendingRefundsOlderThan(threshold, PageRequest.of(0, BATCH_SIZE));

        int reconciledCount = 0;
        for (Refund refund : pendingRefunds) {
            try {
                boolean reconciled = reconcileSingleRefund(refund);
                if (reconciled) reconciledCount++;
            } catch (Exception e) {
                log.warn("Error reconciling pending refund {}: {}", refund.getId(), e.getMessage());
            }
        }
        return reconciledCount;
    }

    private boolean reconcileSingleRefund(Refund refund) {
        String razorpayRefundId = refund.getRazorpayRefundId();
        if (razorpayRefundId == null || razorpayRefundId.isBlank()) {
            return false;
        }

        // Multi-instance distributed claim before gateway call
        if (!claimRefundForReconciliation(refund.getId())) {
            return false;
        }

        String originalReason = refund.getFailureReason();

        RazorpayClient client = razorpayClientProvider.getIfAvailable();
        if (client == null || !razorpayProperties.isConfigured() || razorpayProperties.getKeyId().startsWith("rzp_test_MALKAZTestKey")) {
            releaseRefundLease(refund.getId(), originalReason);
            return false;
        }

        try {
            com.razorpay.Refund rzpRefund = client.refunds.fetch(razorpayRefundId);
            String status = rzpRefund.has("status") ? (String) rzpRefund.get("status") : null;

            if ("processed".equalsIgnoreCase(status)) {
                if (transactionTemplate != null) {
                    transactionTemplate.executeWithoutResult(s -> {
                        Refund r = refundRepository.findByIdForUpdate(refund.getId()).orElse(refund);
                        if (r.getStatus() == RefundStatus.PROCESSED) {
                            log.info("Refund {} already marked PROCESSED. Skipping duplicate reconciliation.", refund.getId());
                            return;
                        }
                        r.setStatus(RefundStatus.PROCESSED);
                        r.setProcessedAt(Instant.now());
                        refundRepository.save(r);

                        Payment p = paymentRepository.findByIdForUpdate(r.getPayment().getId()).orElse(r.getPayment());
                        BigDecimal totalProcessed = refundRepository.sumProcessedRefunds(p.getId());
                        p.setRefundAmount(totalProcessed);
                        p.setRefundStatus("processed");
                        boolean fullyRefunded = totalProcessed.compareTo(p.getAmount()) >= 0;
                        p.setPaymentStatus(fullyRefunded ? PaymentStatus.REFUNDED : PaymentStatus.PARTIALLY_REFUNDED);
                        paymentRepository.save(p);
                    });
                }
                log.info("Reconciled refund {} to PROCESSED status", razorpayRefundId);
                return true;
            } else if ("failed".equalsIgnoreCase(status)) {
                if (transactionTemplate != null) {
                    transactionTemplate.executeWithoutResult(s -> {
                        Refund r = refundRepository.findByIdForUpdate(refund.getId()).orElse(refund);
                        if (r.getStatus() == RefundStatus.PROCESSED || r.getStatus() == RefundStatus.FAILED) {
                            log.info("Refund {} is already terminal ({}). Skipping duplicate reconciliation.", refund.getId(), r.getStatus());
                            return;
                        }
                        r.setStatus(RefundStatus.FAILED);
                        r.setFailureReason("Reconciliation discovered gateway refund failure.");
                        refundRepository.save(r);

                        Payment p = paymentRepository.findByIdForUpdate(r.getPayment().getId()).orElse(r.getPayment());
                        BigDecimal totalProcessed = refundRepository.sumProcessedRefunds(p.getId());
                        p.setRefundAmount(totalProcessed);
                        if (totalProcessed.compareTo(BigDecimal.ZERO) == 0) {
                            p.setPaymentStatus(PaymentStatus.SUCCESS);
                        } else if (totalProcessed.compareTo(p.getAmount()) >= 0) {
                            p.setPaymentStatus(PaymentStatus.REFUNDED);
                        } else {
                            p.setPaymentStatus(PaymentStatus.PARTIALLY_REFUNDED);
                        }
                        paymentRepository.save(p);
                    });
                }
                log.info("Reconciled refund {} to FAILED status", razorpayRefundId);
                return true;
            }
            releaseRefundLease(refund.getId(), originalReason);
        } catch (Exception e) {
            log.warn("Failed to fetch Razorpay refund {}: {}", razorpayRefundId, e.getMessage());
            releaseRefundLease(refund.getId(), originalReason);
        }
        return false;
    }
}

