package com.cosmetics.ecommerce.payment;

import com.cosmetics.ecommerce.dto.PaymentDtos.RazorpayQrResponse;
import com.cosmetics.ecommerce.dto.PaymentDtos.RefundResponse;
import com.cosmetics.ecommerce.entity.Order;
import com.cosmetics.ecommerce.entity.Payment;
import com.cosmetics.ecommerce.enums.PaymentMethod;
import com.cosmetics.ecommerce.enums.PaymentStatus;
import com.cosmetics.ecommerce.exception.BadRequestException;
import com.cosmetics.ecommerce.repository.PaymentRepository;
import com.razorpay.RazorpayClient;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

@Service
public class RazorpayService {
    private static final Logger log = LoggerFactory.getLogger(RazorpayService.class);

    private final RazorpayProperties properties;
    private final ObjectProvider<RazorpayClient> razorpayClientProvider;
    private final PaymentRepository paymentRepository;

    @Autowired(required = false)
    private ObjectProvider<RefundService> refundServiceProvider;

    public RazorpayService(RazorpayProperties properties,
                           ObjectProvider<RazorpayClient> razorpayClientProvider,
                           PaymentRepository paymentRepository) {
        this.properties = properties;
        this.razorpayClientProvider = razorpayClientProvider;
        this.paymentRepository = paymentRepository;
    }

    public RazorpayProperties getProperties() {
        return properties;
    }

    /**
     * Create Razorpay Order via Official SDK
     */
    public String createRazorpayOrder(Order order) {
        long amountInPaise = order.getTotalAmount()
                .multiply(BigDecimal.valueOf(100))
                .setScale(0, RoundingMode.HALF_UP)
                .longValue();

        RazorpayClient client = razorpayClientProvider.getIfAvailable();
        if (client != null && properties.isConfigured() && !properties.getKeyId().startsWith("rzp_test_MALKAZTestKey")) {
            try {
                JSONObject orderReq = new JSONObject();
                orderReq.put("amount", amountInPaise);
                orderReq.put("currency", properties.getCurrency());
                orderReq.put("receipt", order.getOrderNumber());
                orderReq.put("payment_capture", 1);

                JSONObject notes = new JSONObject();
                notes.put("order_id", String.valueOf(order.getId()));
                notes.put("order_number", order.getOrderNumber());
                if (order.getUser() != null) {
                    notes.put("customer_email", order.getUser().getEmail());
                }
                orderReq.put("notes", notes);

                com.razorpay.Order rzpOrder = client.orders.create(orderReq);
                return rzpOrder.get("id");
            } catch (Exception e) {
                log.error("Failed to create Razorpay order for order {}: {}", order.getOrderNumber(), e.getMessage(), e);
                throw new BadRequestException("Unable to create Razorpay payment order: " + e.getMessage());
            }
        }

        // Test fallback when real Razorpay credentials are not active
        return "order_test_" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);
    }

    /**
     * Create dynamic UPI QR code
     * STRICT PRODUCTION RULE: Never generate simulated UPI strings or fake third-party QR images.
     */
    public RazorpayQrResponse createDynamicQrCode(Order order, String razorpayOrderId) {
        long amountInPaise = order.getTotalAmount()
                .multiply(BigDecimal.valueOf(100))
                .setScale(0, RoundingMode.HALF_UP)
                .longValue();

        RazorpayClient client = razorpayClientProvider.getIfAvailable();
        if (client == null || !properties.isConfigured() || properties.getKeyId().startsWith("rzp_test_MALKAZTestKey")) {
            throw new BadRequestException("Dynamic UPI QR generation is not supported or configured on this environment. Please use standard UPI checkout.");
        }

        try {
            JSONObject qrReq = new JSONObject();
            qrReq.put("type", "upi_qr");
            qrReq.put("name", "MALKAZ Jewellery Order #" + order.getOrderNumber());
            qrReq.put("usage", "single_use");
            qrReq.put("fixed_amount", true);
            qrReq.put("payment_amount", amountInPaise);
            qrReq.put("description", "Payment for MALKAZ Order " + order.getOrderNumber());

            JSONObject notes = new JSONObject();
            notes.put("order_id", String.valueOf(order.getId()));
            notes.put("order_number", order.getOrderNumber());
            qrReq.put("notes", notes);

            com.razorpay.QrCode qrCode = client.qrCode.create(qrReq);
            String qrId = qrCode.get("id");
            String imageUrl = qrCode.has("image_url") ? (String) qrCode.get("image_url") : null;
            String paymentUrl = qrCode.has("payment_url") ? (String) qrCode.get("payment_url") : null;
            String status = qrCode.has("status") ? (String) qrCode.get("status") : "active";

            if (qrId == null || qrId.isBlank()) {
                throw new BadRequestException("Razorpay did not return a valid QR code ID.");
            }

            return new RazorpayQrResponse(
                    order.getId(),
                    order.getOrderNumber(),
                    razorpayOrderId,
                    qrId,
                    imageUrl,
                    paymentUrl,
                    order.getTotalAmount(),
                    amountInPaise,
                    properties.getCurrency(),
                    status
            );
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to generate dynamic UPI QR code via Razorpay SDK: {}", e.getMessage(), e);
            throw new BadRequestException("Dynamic UPI QR generation failed: " + e.getMessage());
        }
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void markPaymentFailed(Long paymentId, String code, String reason) {
        log.info("markPaymentFailed called for paymentId={}, code={}", paymentId, code);
        paymentRepository.findById(paymentId).ifPresentOrElse(p -> {
            log.info("Found payment {} to mark FAILED (was {})", paymentId, p.getPaymentStatus());
            p.setFailureCode(code);
            p.setFailureReason(reason);
            p.setPaymentStatus(PaymentStatus.FAILED);
            Payment saved = paymentRepository.saveAndFlush(p);
            log.info("Payment {} marked FAILED successfully, saved status={}", paymentId, saved.getPaymentStatus());
        }, () -> {
            log.warn("Payment {} NOT found in markPaymentFailed!", paymentId);
        });
    }

    /**
     * Validate Razorpay payment signature and verify against Razorpay API
     */
    public void verifyPaymentSignatureAndDetails(Payment payment, String razorpayOrderId, String razorpayPaymentId, String razorpaySignature) {
        // 1. HMAC SHA-256 verification
        boolean valid = RazorpaySignatureUtil.verifyPaymentSignature(
                razorpayOrderId, razorpayPaymentId, razorpaySignature, properties.getKeySecret()
        );
        if (!valid) {
            throw new BadRequestException("Payment verification failed: Invalid Razorpay signature.");
        }

        // 2. Fetch from Razorpay API if live client is available
        RazorpayClient client = razorpayClientProvider.getIfAvailable();
        if (client != null && properties.isConfigured() && !properties.getKeyId().startsWith("rzp_test_MALKAZTestKey")) {
            try {
                com.razorpay.Payment rzpPayment = client.payments.fetch(razorpayPaymentId);

                // Disallow Card payments
                String method = rzpPayment.get("method");
                if (method != null && (method.toLowerCase().contains("card") || method.equalsIgnoreCase("credit_card") || method.equalsIgnoreCase("debit_card"))) {
                    payment.setFailureCode("CARD_PAYMENT_DISALLOWED");
                    payment.setFailureReason("Card payments are strictly disallowed. Only UPI, Net Banking, and COD are permitted.");
                    payment.setPaymentStatus(PaymentStatus.FAILED);
                    paymentRepository.save(payment);
                    throw new BadRequestException("Card payments are not accepted. Only UPI, Net Banking, and Cash on Delivery are supported.");
                }

                // Verify Amount
                long expectedPaise = payment.getAmount()
                .multiply(BigDecimal.valueOf(100))
                        .setScale(0, RoundingMode.HALF_UP)
                        .longValue();
                Number amtObj = rzpPayment.get("amount");
                long actualPaise = amtObj != null ? amtObj.longValue() : 0;
                if (actualPaise < expectedPaise) {
                    payment.setFailureCode("AMOUNT_MISMATCH");
                    payment.setFailureReason("Paid amount " + actualPaise + " paise does not match expected " + expectedPaise + " paise.");
                    payment.setPaymentStatus(PaymentStatus.FAILED);
                    paymentRepository.save(payment);
                    throw new BadRequestException("Paid amount does not match expected order total. Amount tampering detected.");
                }

                // Verify capture status
                String status = rzpPayment.get("status");
                if ("failed".equalsIgnoreCase(status)) {
                    payment.setFailureCode("PAYMENT_FAILED_AT_GATEWAY");
                    payment.setFailureReason("Payment status reported as failed by Razorpay.");
                    payment.setPaymentStatus(PaymentStatus.FAILED);
                    paymentRepository.save(payment);
                    throw new BadRequestException("Payment failed at gateway.");
                }
            } catch (BadRequestException e) {
                throw e;
            } catch (Exception e) {
                log.error("Error fetching payment details from Razorpay: {}", e.getMessage(), e);
                // If it's a test payment or network issue, continue if signature was valid
            }
        }
    }

    /**
     * Process full or partial refund.
     * Delegates to RefundService for database locking, ledger persistence, and multi-refund idempotency.
     */
    public RefundResponse processRefund(Payment payment, BigDecimal requestedRefundAmount, String reason) {
        if (refundServiceProvider != null) {
            RefundService refundService = refundServiceProvider.getIfAvailable();
            if (refundService != null && payment != null && payment.getId() != null) {
                return refundService.processRefund(payment.getId(), requestedRefundAmount, reason, "ADMIN");
            }
        }
        throw new IllegalStateException("RefundService is not configured.");
    }
}
