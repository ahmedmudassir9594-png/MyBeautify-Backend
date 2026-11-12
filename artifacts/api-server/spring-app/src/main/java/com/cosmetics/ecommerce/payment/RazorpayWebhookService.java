package com.cosmetics.ecommerce.payment;

import com.cosmetics.ecommerce.email.EmailService;
import com.cosmetics.ecommerce.entity.Cart;
import com.cosmetics.ecommerce.entity.Order;
import com.cosmetics.ecommerce.entity.OrderItem;
import com.cosmetics.ecommerce.entity.Payment;
import com.cosmetics.ecommerce.entity.PaymentWebhookEvent;
import com.cosmetics.ecommerce.entity.Product;
import com.cosmetics.ecommerce.entity.Refund;
import com.cosmetics.ecommerce.enums.AuditAction;
import com.cosmetics.ecommerce.enums.AuditResult;
import com.cosmetics.ecommerce.enums.OrderStatus;
import com.cosmetics.ecommerce.enums.PaymentStatus;
import com.cosmetics.ecommerce.enums.RefundStatus;
import com.cosmetics.ecommerce.exception.BadRequestException;
import com.cosmetics.ecommerce.repository.CartRepository;
import com.cosmetics.ecommerce.repository.OrderRepository;
import com.cosmetics.ecommerce.repository.PaymentRepository;
import com.cosmetics.ecommerce.repository.PaymentWebhookEventRepository;
import com.cosmetics.ecommerce.repository.ProductRepository;
import com.cosmetics.ecommerce.repository.RefundRepository;
import com.cosmetics.ecommerce.service.AuditLogService;
import com.cosmetics.ecommerce.service.ReferralService;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class RazorpayWebhookService {
    private static final Logger log = LoggerFactory.getLogger(RazorpayWebhookService.class);

    private final RazorpayProperties properties;
    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final CartRepository cartRepository;
    private final PaymentWebhookEventRepository webhookEventRepository;
    private final EmailService emailService;
    private final ReferralService referralService;
    private final ObjectProvider<AuditLogService> auditLogServiceProvider;
    private final RefundRepository refundRepository;

    @org.springframework.beans.factory.annotation.Autowired
    public RazorpayWebhookService(RazorpayProperties properties,
                                  PaymentRepository paymentRepository,
                                  OrderRepository orderRepository,
                                  ProductRepository productRepository,
                                  CartRepository cartRepository,
                                  PaymentWebhookEventRepository webhookEventRepository,
                                  EmailService emailService,
                                  ReferralService referralService,
                                  ObjectProvider<AuditLogService> auditLogServiceProvider,
                                  RefundRepository refundRepository) {
        this.properties = properties;
        this.paymentRepository = paymentRepository;
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.cartRepository = cartRepository;
        this.webhookEventRepository = webhookEventRepository;
        this.emailService = emailService;
        this.referralService = referralService;
        this.auditLogServiceProvider = auditLogServiceProvider;
        this.refundRepository = refundRepository;
    }

    public RazorpayWebhookService(RazorpayProperties properties,
                                  PaymentRepository paymentRepository,
                                  OrderRepository orderRepository,
                                  ProductRepository productRepository,
                                  CartRepository cartRepository,
                                  PaymentWebhookEventRepository webhookEventRepository,
                                  EmailService emailService,
                                  ReferralService referralService,
                                  ObjectProvider<AuditLogService> auditLogServiceProvider) {
        this(properties, paymentRepository, orderRepository, productRepository, cartRepository,
             webhookEventRepository, emailService, referralService, auditLogServiceProvider, null);
    }

    @Transactional
    public void processWebhook(String rawPayload, String signatureHeader) {
        // 1. Verify Webhook Signature
        String webhookSecret = properties.getWebhookSecret();
        if (webhookSecret == null || webhookSecret.isBlank()) {
            webhookSecret = properties.getKeySecret(); // fallback to key secret if not separately configured
        }

        if (!RazorpaySignatureUtil.verifyWebhookSignature(rawPayload, signatureHeader, webhookSecret)) {
            AuditLogService audit = auditLogServiceProvider.getIfAvailable();
            if (audit != null) {
                try {
                    audit.logSecurityFailure(AuditAction.SECURITY_ACCESS_DENIED, null,
                            "Invalid Razorpay webhook signature detected.");
                } catch (Exception ignored) {}
            }
            log.error("Razorpay webhook signature verification failed.");
            throw new BadRequestException("Invalid webhook signature.");
        }

        // 2. Parse Event JSON
        JSONObject root = new JSONObject(rawPayload);
        String eventId = root.optString("id");
        String eventType = root.optString("event");

        if (eventId == null || eventId.isBlank()) {
            throw new BadRequestException("Webhook event ID missing.");
        }

        // 3. Deduplication / Idempotency Check
        if (webhookEventRepository.existsByRazorpayEventId(eventId)) {
            log.info("Webhook event {} already processed. Skipping duplicate.", eventId);
            return;
        }

        PaymentWebhookEvent webhookEvent = new PaymentWebhookEvent(eventId, eventType, "PROCESSING", rawPayload);
        webhookEvent = webhookEventRepository.save(webhookEvent);

        try {
            JSONObject payloadObj = root.optJSONObject("payload");
            if (payloadObj != null) {
                switch (eventType) {
                    case "payment.captured", "order.paid" -> handlePaymentCaptured(payloadObj);
                    case "payment.failed" -> handlePaymentFailed(payloadObj);
                    case "refund.processed" -> handleRefundProcessed(payloadObj);
                    case "refund.failed" -> handleRefundFailed(payloadObj);
                    default -> log.info("Unhandled Razorpay webhook event: {}", eventType);
                }
            }
            webhookEvent.setProcessingStatus("PROCESSED");
            webhookEvent.setProcessedAt(Instant.now());
        } catch (Exception e) {
            log.error("Failed processing webhook event {}: {}", eventId, e.getMessage(), e);
            webhookEvent.setProcessingStatus("FAILED");
            webhookEvent.setErrorMessage(e.getMessage());
            webhookEvent.setProcessedAt(Instant.now());
            throw e;
        } finally {
            webhookEventRepository.save(webhookEvent);
        }
    }

    private void handlePaymentCaptured(JSONObject payload) {
        JSONObject paymentObj = payload.optJSONObject("payment");
        if (paymentObj == null) return;
        JSONObject entity = paymentObj.optJSONObject("entity");
        if (entity == null) return;

        String razorpayPaymentId = entity.optString("id");
        String razorpayOrderId = entity.optString("order_id");
        String method = entity.optString("method");

        // Disallow cards
        if (method != null && (method.toLowerCase().contains("card") || method.equalsIgnoreCase("credit_card") || method.equalsIgnoreCase("debit_card"))) {
            log.warn("Webhook rejected: Card payment method detected on payment {}", razorpayPaymentId);
            return;
        }

        Optional<Payment> paymentOpt = paymentRepository.findByRazorpayOrderId(razorpayOrderId);
        if (paymentOpt.isEmpty() && razorpayPaymentId != null) {
            paymentOpt = paymentRepository.findByRazorpayPaymentId(razorpayPaymentId);
        }

        if (paymentOpt.isPresent()) {
            Payment payment = paymentOpt.get();
            Order order = payment.getOrder();

            // Scenario 1: Order is CANCELLED, payment is FAILED / SUPERSEDED -> Orphan Captured Payment
            boolean isCancelledOrSuperseded = (order != null && order.getOrderStatus() == OrderStatus.CANCELLED)
                    || payment.getPaymentStatus() == PaymentStatus.FAILED
                    || "SUPERSEDED".equals(payment.getFailureCode());

            if (isCancelledOrSuperseded) {
                // If already flagged for review, maintain idempotency
                if (payment.getPaymentStatus() == PaymentStatus.REFUND_REVIEW_REQUIRED
                        || payment.getPaymentStatus() == PaymentStatus.ORPHAN_CAPTURED
                        || payment.getPaymentStatus() == PaymentStatus.REFUNDED) {
                    log.info("Orphan captured payment {} for order {} already recorded. Skipping duplicate processing.",
                            razorpayPaymentId, order != null ? order.getOrderNumber() : "unknown");
                    return;
                }

                // Verify captured amount
                long expectedPaise = payment.getAmount().multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).longValue();
                Number amtObj = entity.optNumber("amount", 0);
                long actualPaise = amtObj.longValue();
                BigDecimal capturedAmount = BigDecimal.valueOf(actualPaise).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                String currency = entity.optString("currency", "INR");
                boolean amountMismatch = (actualPaise < expectedPaise);

                payment.setRazorpayPaymentId(razorpayPaymentId);
                payment.setPaidAt(Instant.now());
                payment.setPaymentStatus(PaymentStatus.REFUND_REVIEW_REQUIRED);
                payment.setFailureCode(amountMismatch ? "AMOUNT_MISMATCH_ON_CANCELLED_ORDER" : "ORPHAN_CAPTURE_ON_CANCELLED_ORDER");
                payment.setFailureReason("Payment of " + capturedAmount + " " + currency + " was captured on Razorpay after internal order #"
                        + (order != null ? order.getOrderNumber() : "N/A") + " was CANCELLED/SUPERSEDED. Requires refund or manual reconciliation.");
                paymentRepository.save(payment);

                // DO NOT confirm order, DO NOT deduct inventory, DO NOT clear customer cart

                // Durable Audit Log
                AuditLogService audit = auditLogServiceProvider.getIfAvailable();
                if (audit != null) {
                    String orphanDetails = String.format("{\"orderNumber\":\"%s\",\"razorpayPaymentId\":\"%s\",\"capturedAmount\":%s,\"currency\":\"%s\",\"expectedAmount\":%s,\"orderStatus\":\"CANCELLED\",\"reconciliation\":\"REFUND_REVIEW_REQUIRED\"}",
                            order != null ? order.getOrderNumber() : "N/A", razorpayPaymentId, capturedAmount, currency, payment.getAmount());
                    audit.logEvent(AuditAction.PAYMENT_FAILURE, AuditResult.FAILURE, "PAYMENT", razorpayPaymentId,
                            "CRITICAL: Late payment captured for CANCELLED order " + (order != null ? order.getOrderNumber() : "N/A")
                                    + ". Flagged for REFUND_REVIEW_REQUIRED.", null, orphanDetails);
                }

                // Administrator Alert Notification
                if (order != null) {
                    emailService.sendAdminAlert(
                            "CRITICAL: Orphan Payment Captured for Cancelled Order #" + order.getOrderNumber(),
                            "A payment of " + capturedAmount + " " + currency + " (Razorpay Payment ID: " + razorpayPaymentId + ") "
                                    + "was captured on Razorpay for order #" + order.getOrderNumber() + ", which is marked CANCELLED in MALKAZ.\n\n"
                                    + "Action Required: Please navigate to Admin > Payments > Review Required to initiate a full refund or reconcile the transaction."
                    );
                }
                log.warn("Late captured payment {} recorded as REFUND_REVIEW_REQUIRED for cancelled order {}",
                        razorpayPaymentId, order != null ? order.getOrderNumber() : "N/A");
                return;
            }

            // Scenario 2: Standard Active Pending Order Payment Capture
            if (payment.getPaymentStatus() == PaymentStatus.PENDING || payment.getPaymentStatus() == PaymentStatus.AUTHORIZED) {
                // Verify captured amount and currency
                long expectedPaise = payment.getAmount().multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).longValue();
                Number amtObj = entity.optNumber("amount", 0);
                long actualPaise = amtObj.longValue();
                BigDecimal capturedAmount = BigDecimal.valueOf(actualPaise).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                String currency = entity.optString("currency", "INR");

                if (actualPaise < expectedPaise || (currency != null && !currency.equalsIgnoreCase(payment.getCurrency()))) {
                    String mismatchReason = actualPaise < expectedPaise
                            ? "Paid amount " + actualPaise + " paise is less than expected " + expectedPaise + " paise."
                            : "Payment currency " + currency + " does not match expected " + payment.getCurrency() + ".";

                    log.warn("CRITICAL: Payment integrity failure for order {}: {}",
                            order != null ? order.getOrderNumber() : "N/A", mismatchReason);

                    payment.setRazorpayPaymentId(razorpayPaymentId);
                    payment.setPaidAt(Instant.now());
                    payment.setPaymentStatus(PaymentStatus.REFUND_REVIEW_REQUIRED);
                    payment.setFailureCode(actualPaise < expectedPaise ? "AMOUNT_MISMATCH" : "CURRENCY_MISMATCH");
                    payment.setFailureReason(mismatchReason);
                    paymentRepository.save(payment);

                    AuditLogService audit = auditLogServiceProvider.getIfAvailable();
                    if (audit != null) {
                        String auditDetails = String.format("{\"orderNumber\":\"%s\",\"razorpayPaymentId\":\"%s\",\"capturedAmount\":%s,\"currency\":\"%s\",\"expectedAmount\":%s,\"reason\":\"%s\"}",
                                order != null ? order.getOrderNumber() : "N/A", razorpayPaymentId, capturedAmount, currency, payment.getAmount(), mismatchReason);
                        audit.logEvent(AuditAction.PAYMENT_FAILURE, AuditResult.FAILURE, "PAYMENT", razorpayPaymentId,
                                "Payment integrity check failed: " + mismatchReason, null, auditDetails);
                    }

                    if (order != null) {
                        emailService.sendAdminAlert(
                                "SECURITY ALERT: Payment Integrity Mismatch for Order #" + order.getOrderNumber(),
                                "A payment capture webhook was received with an amount or currency mismatch.\n\n"
                                        + "Order Number: " + order.getOrderNumber() + "\n"
                                        + "Expected: " + payment.getAmount() + " " + payment.getCurrency() + "\n"
                                        + "Received: " + capturedAmount + " " + currency + "\n"
                                        + "Payment ID: " + razorpayPaymentId + "\n"
                                        + "Action: Order has NOT been confirmed. Payment flagged for REFUND_REVIEW_REQUIRED."
                        );
                    }
                    return;
                }

                payment.setRazorpayPaymentId(razorpayPaymentId);
                payment.setPaymentStatus(PaymentStatus.SUCCESS);
                payment.setPaidAt(Instant.now());
                paymentRepository.save(payment);

                if (order != null && order.getId() != null) {
                    Order lockedOrder = orderRepository.findByIdForUpdate(order.getId()).orElse(order);
                    if (lockedOrder.getOrderStatus() == OrderStatus.CONFIRMED) {
                        log.info("Order {} is already confirmed (e.g. by frontend verification). Idempotent webhook skip of stock deduction.", lockedOrder.getOrderNumber());
                        return;
                    }

                    if (lockedOrder.getOrderStatus() == OrderStatus.PENDING) {
                        lockedOrder.setOrderStatus(OrderStatus.CONFIRMED);
                        lockedOrder.setPaymentStatus(PaymentStatus.SUCCESS);

                        // Deduct stock for items atomically under order row lock
                        for (OrderItem item : lockedOrder.getItems()) {
                            Product product = item.getProduct();
                            if (product != null) {
                                product.setStockQuantity(Math.max(0, product.getStockQuantity() - item.getQuantity()));
                                productRepository.save(product);
                            }
                        }

                        if (lockedOrder.getCoupon() != null) {
                            lockedOrder.getCoupon().incrementUsedCount();
                        }

                        Order saved = orderRepository.save(lockedOrder);
                        referralService.recordQualifyingPurchase(saved);

                        // Clear customer cart
                        if (saved.getUser() != null) {
                            cartRepository.findByUserId(saved.getUser().getId()).ifPresent(c -> {
                                c.getItems().clear();
                                c.touch();
                                cartRepository.save(c);
                            });
                            emailService.sendOrderConfirmation(saved.getUser().getEmail(), saved.getOrderNumber(), saved.getTotalAmount());
                        }

                        AuditLogService audit = auditLogServiceProvider.getIfAvailable();
                        if (audit != null) {
                            audit.logEvent(AuditAction.PAYMENT_SUCCESS, AuditResult.SUCCESS, "PAYMENT", razorpayPaymentId,
                                    "Payment captured via Razorpay webhook for order " + saved.getOrderNumber(), null,
                                    "{\"orderNumber\":\"" + saved.getOrderNumber() + "\",\"razorpayPaymentId\":\"" + razorpayPaymentId + "\"}");
                        }
                    }
                }
            }
        }
    }

    private void handlePaymentFailed(JSONObject payload) {
        JSONObject paymentObj = payload.optJSONObject("payment");
        if (paymentObj == null) return;
        JSONObject entity = paymentObj.optJSONObject("entity");
        if (entity == null) return;

        String razorpayPaymentId = entity.optString("id");
        String razorpayOrderId = entity.optString("order_id");
        String errorCode = entity.optString("error_code");
        String errorDesc = entity.optString("error_description");

        Optional<Payment> paymentOpt = paymentRepository.findByRazorpayOrderId(razorpayOrderId);
        if (paymentOpt.isPresent()) {
            Payment payment = paymentOpt.get();
            if (payment.getPaymentStatus() == PaymentStatus.PENDING) {
                payment.setRazorpayPaymentId(razorpayPaymentId);
                payment.setFailureCode(errorCode);
                payment.setFailureReason(errorDesc);
                payment.setPaymentStatus(PaymentStatus.FAILED);
                paymentRepository.save(payment);

                AuditLogService audit = auditLogServiceProvider.getIfAvailable();
                if (audit != null) {
                    audit.logEvent(AuditAction.PAYMENT_FAILURE, AuditResult.FAILURE, "PAYMENT", razorpayPaymentId,
                            "Payment failed via Razorpay webhook: " + errorDesc, null,
                            "{\"errorCode\":\"" + errorCode + "\",\"reason\":\"" + errorDesc + "\"}");
                }
            }
        }
    }

    private void handleRefundProcessed(JSONObject payload) {
        JSONObject refundObj = payload.optJSONObject("refund");
        if (refundObj == null) return;
        JSONObject entity = refundObj.optJSONObject("entity");
        if (entity == null) return;

        String razorpayRefundId = entity.optString("id");
        String razorpayPaymentId = entity.optString("payment_id");
        long amountPaise = entity.optLong("amount", 0);
        BigDecimal refundAmt = BigDecimal.valueOf(amountPaise).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        String currency = entity.optString("currency", "INR");

        if (razorpayRefundId == null || razorpayRefundId.isBlank() || razorpayPaymentId == null || razorpayPaymentId.isBlank()) {
            log.warn("Invalid refund.processed webhook payload: missing refund ID or payment ID.");
            return;
        }

        if (refundRepository == null) {
            log.warn("RefundRepository is not configured, skipping ledger refund processing.");
            return;
        }

        Payment payment = null;
        Optional<Refund> existingRefundOpt = refundRepository.findByRazorpayRefundId(razorpayRefundId);

        if (existingRefundOpt.isPresent()) {
            Refund refund = existingRefundOpt.get();
            payment = refund.getPayment();
            if (refund.getStatus() == RefundStatus.PROCESSED) {
                log.info("Refund {} already marked as PROCESSED in ledger. Idempotent skip.", razorpayRefundId);
                return;
            }
            refund.setStatus(RefundStatus.PROCESSED);
            refund.setProcessedAt(Instant.now());
            refundRepository.save(refund);
        } else {
            Optional<Payment> paymentOpt = paymentRepository.findByRazorpayPaymentId(razorpayPaymentId);
            if (paymentOpt.isEmpty()) {
                log.warn("Received refund.processed webhook for unknown payment ID: {}. Ignoring without mutating ledger.", razorpayPaymentId);
                return;
            }
            payment = paymentOpt.get();

            // Match any pending reservation
            List<Refund> activeRefunds = refundRepository.findByPaymentIdOrderByCreatedAtDesc(payment.getId());
            Refund matchedReservation = null;
            for (Refund r : activeRefunds) {
                if ((r.getStatus() == RefundStatus.INITIATED || r.getStatus() == RefundStatus.PENDING || r.getStatus() == RefundStatus.RECONCILIATION_REQUIRED)
                        && (r.getRazorpayRefundId() == null || r.getRazorpayRefundId().equals(razorpayRefundId))
                        && r.getAmount().compareTo(refundAmt) == 0) {
                    matchedReservation = r;
                    break;
                }
            }

            if (matchedReservation != null) {
                matchedReservation.setRazorpayRefundId(razorpayRefundId);
                matchedReservation.setStatus(RefundStatus.PROCESSED);
                matchedReservation.setProcessedAt(Instant.now());
                refundRepository.save(matchedReservation);
            } else {
                // External refund (e.g. from Razorpay dashboard)
                BigDecimal currentProcessed = refundRepository.sumProcessedRefunds(payment.getId());
                if (currentProcessed.add(refundAmt).compareTo(payment.getAmount()) > 0) {
                    log.error("External refund {} amount {} exceeds remaining payment balance for payment {}",
                            razorpayRefundId, refundAmt, payment.getId());
                    return;
                }
                String internalRef = "EXT-REF-" + razorpayRefundId;
                Refund newRefund = new Refund(
                        payment,
                        internalRef,
                        refundAmt,
                        currency,
                        RefundStatus.PROCESSED,
                        "External/Dashboard refund processed",
                        "RAZORPAY_WEBHOOK"
                );
                newRefund.setRazorpayRefundId(razorpayRefundId);
                newRefund.setProcessedAt(Instant.now());
                refundRepository.save(newRefund);
            }
        }

        // Authoritative update from the unique records in the refund ledger
        BigDecimal totalProcessed = refundRepository.sumProcessedRefunds(payment.getId());
        payment.setRefundAmount(totalProcessed);
        payment.setRefundId(razorpayRefundId);
        payment.setRefundStatus("processed");

        boolean fullyRefunded = totalProcessed.compareTo(payment.getAmount()) >= 0;
        payment.setPaymentStatus(fullyRefunded ? PaymentStatus.REFUNDED : PaymentStatus.PARTIALLY_REFUNDED);
        paymentRepository.save(payment);

        log.info("Reconciled refund.processed webhook for payment {}: refundId={}, totalProcessedRefunds={}",
                payment.getId(), razorpayRefundId, totalProcessed);
    }

    private void handleRefundFailed(JSONObject payload) {
        JSONObject refundObj = payload.optJSONObject("refund");
        if (refundObj == null) return;
        JSONObject entity = refundObj.optJSONObject("entity");
        if (entity == null) return;

        String razorpayRefundId = entity.optString("id");
        String razorpayPaymentId = entity.optString("payment_id");
        String errorCode = entity.optString("error_code");
        String errorDesc = entity.optString("error_description");

        if (razorpayRefundId == null || razorpayRefundId.isBlank()) {
            log.warn("Invalid refund.failed webhook payload: missing refund ID.");
            return;
        }

        if (refundRepository == null) return;

        Optional<Refund> refundOpt = refundRepository.findByRazorpayRefundId(razorpayRefundId);
        if (refundOpt.isPresent()) {
            Refund refund = refundOpt.get();
            if (refund.getStatus() == RefundStatus.PROCESSED) {
                log.warn("Ignoring refund.failed webhook for refund {} because it is already marked PROCESSED.", razorpayRefundId);
                return;
            }
            refund.setStatus(RefundStatus.FAILED);
            refund.setFailureReason("Razorpay refund failed: " + (errorDesc != null ? errorDesc : errorCode));
            refundRepository.save(refund);

            Payment payment = refund.getPayment();
            if (payment != null) {
                BigDecimal totalProcessed = refundRepository.sumProcessedRefunds(payment.getId());
                payment.setRefundAmount(totalProcessed);
                boolean fullyRefunded = totalProcessed.compareTo(payment.getAmount()) >= 0;
                if (fullyRefunded) {
                    payment.setPaymentStatus(PaymentStatus.REFUNDED);
                } else if (totalProcessed.compareTo(BigDecimal.ZERO) > 0) {
                    payment.setPaymentStatus(PaymentStatus.PARTIALLY_REFUNDED);
                } else {
                    payment.setPaymentStatus(PaymentStatus.SUCCESS);
                }
                paymentRepository.save(payment);

                AuditLogService audit = auditLogServiceProvider.getIfAvailable();
                if (audit != null) {
                    audit.logEvent(AuditAction.PAYMENT_FAILURE, AuditResult.FAILURE, "REFUND", razorpayRefundId,
                            "Refund " + razorpayRefundId + " failed via webhook: " + errorDesc, null,
                            "{\"refundId\":\"" + razorpayRefundId + "\",\"errorCode\":\"" + errorCode + "\",\"reason\":\"" + errorDesc + "\"}");
                }
            }
        }
    }
}

