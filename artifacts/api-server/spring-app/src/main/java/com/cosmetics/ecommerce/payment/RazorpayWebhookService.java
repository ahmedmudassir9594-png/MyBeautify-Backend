package com.cosmetics.ecommerce.payment;

import com.cosmetics.ecommerce.email.EmailService;
import com.cosmetics.ecommerce.entity.Cart;
import com.cosmetics.ecommerce.entity.Order;
import com.cosmetics.ecommerce.entity.OrderItem;
import com.cosmetics.ecommerce.entity.Payment;
import com.cosmetics.ecommerce.entity.PaymentWebhookEvent;
import com.cosmetics.ecommerce.entity.Product;
import com.cosmetics.ecommerce.enums.AuditAction;
import com.cosmetics.ecommerce.enums.AuditResult;
import com.cosmetics.ecommerce.enums.OrderStatus;
import com.cosmetics.ecommerce.enums.PaymentStatus;
import com.cosmetics.ecommerce.exception.BadRequestException;
import com.cosmetics.ecommerce.repository.CartRepository;
import com.cosmetics.ecommerce.repository.OrderRepository;
import com.cosmetics.ecommerce.repository.PaymentRepository;
import com.cosmetics.ecommerce.repository.PaymentWebhookEventRepository;
import com.cosmetics.ecommerce.repository.ProductRepository;
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

    public RazorpayWebhookService(RazorpayProperties properties,
                                  PaymentRepository paymentRepository,
                                  OrderRepository orderRepository,
                                  ProductRepository productRepository,
                                  CartRepository cartRepository,
                                  PaymentWebhookEventRepository webhookEventRepository,
                                  EmailService emailService,
                                  ReferralService referralService,
                                  ObjectProvider<AuditLogService> auditLogServiceProvider) {
        this.properties = properties;
        this.paymentRepository = paymentRepository;
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.cartRepository = cartRepository;
        this.webhookEventRepository = webhookEventRepository;
        this.emailService = emailService;
        this.referralService = referralService;
        this.auditLogServiceProvider = auditLogServiceProvider;
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

            if (payment.getPaymentStatus() == PaymentStatus.PENDING || payment.getPaymentStatus() == PaymentStatus.AUTHORIZED) {
                payment.setRazorpayPaymentId(razorpayPaymentId);
                payment.setPaymentStatus(PaymentStatus.SUCCESS);
                payment.setPaidAt(Instant.now());
                paymentRepository.save(payment);

                if (order != null && order.getOrderStatus() == OrderStatus.PENDING) {
                    order.setOrderStatus(OrderStatus.CONFIRMED);
                    order.setPaymentStatus(PaymentStatus.SUCCESS);

                    // Deduct stock for items
                    for (OrderItem item : order.getItems()) {
                        Product product = item.getProduct();
                        if (product != null) {
                            product.setStockQuantity(Math.max(0, product.getStockQuantity() - item.getQuantity()));
                            productRepository.save(product);
                        }
                    }

                    if (order.getCoupon() != null) {
                        order.getCoupon().incrementUsedCount();
                    }

                    Order saved = orderRepository.save(order);
                    referralService.recordQualifyingPurchase(saved);

                    // Clear customer cart
                    if (order.getUser() != null) {
                        cartRepository.findByUserId(order.getUser().getId()).ifPresent(c -> {
                            c.getItems().clear();
                            c.touch();
                            cartRepository.save(c);
                        });
                        emailService.sendOrderConfirmation(order.getUser().getEmail(), saved.getOrderNumber(), saved.getTotalAmount());
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

        String refundId = entity.optString("id");
        String paymentId = entity.optString("payment_id");
        long amountPaise = entity.optLong("amount", 0);
        BigDecimal refundAmt = BigDecimal.valueOf(amountPaise).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        Optional<Payment> paymentOpt = paymentRepository.findByRazorpayPaymentId(paymentId);
        if (paymentOpt.isPresent()) {
            Payment payment = paymentOpt.get();
            payment.setRefundId(refundId);
            payment.setRefundStatus("processed");
            BigDecimal totalRefunded = (payment.getRefundAmount() != null ? payment.getRefundAmount() : BigDecimal.ZERO).add(refundAmt);
            payment.setRefundAmount(totalRefunded);

            boolean fullyRefunded = totalRefunded.compareTo(payment.getAmount()) >= 0;
            payment.setPaymentStatus(fullyRefunded ? PaymentStatus.REFUNDED : PaymentStatus.PARTIALLY_REFUNDED);
            paymentRepository.save(payment);
        }
    }
}

