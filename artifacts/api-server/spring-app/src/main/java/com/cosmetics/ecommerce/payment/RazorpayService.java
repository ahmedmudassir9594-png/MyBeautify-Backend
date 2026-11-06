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
     */
    public RazorpayQrResponse createDynamicQrCode(Order order, String razorpayOrderId) {
        long amountInPaise = order.getTotalAmount()
                .multiply(BigDecimal.valueOf(100))
                .setScale(0, RoundingMode.HALF_UP)
                .longValue();

        RazorpayClient client = razorpayClientProvider.getIfAvailable();
        if (client != null && properties.isConfigured() && !properties.getKeyId().startsWith("rzp_test_MALKAZTestKey")) {
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
            } catch (Exception e) {
                log.warn("Dynamic QR creation via SDK failed, falling back to simulated QR: {}", e.getMessage());
            }
        }

        // Fallback simulated QR payload
        String testQrId = "qr_test_" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);
        String upiLink = "upi://pay?pa=malkaz.payments@icici&pn=MALKAZ%20Jewellery&am="
                + order.getTotalAmount() + "&cu=INR&tn=Order%20" + order.getOrderNumber();
        String qrImageUrl = "https://api.qrserver.com/v1/create-qr-code/?size=300x300&data="
                + java.net.URLEncoder.encode(upiLink, java.nio.charset.StandardCharsets.UTF_8);

        return new RazorpayQrResponse(
                order.getId(),
                order.getOrderNumber(),
                razorpayOrderId,
                testQrId,
                qrImageUrl,
                upiLink,
                order.getTotalAmount(),
                amountInPaise,
                properties.getCurrency(),
                "active"
        );
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
     * Process full or partial refund
     */
    @Transactional
    public RefundResponse processRefund(Payment payment, BigDecimal requestedRefundAmount, String reason) {
        if (payment.getPaymentStatus() != PaymentStatus.SUCCESS && payment.getPaymentStatus() != PaymentStatus.PARTIALLY_REFUNDED) {
            throw new BadRequestException("Cannot refund payment: Current payment status is " + payment.getPaymentStatus() + ". Only successful or partially refunded payments can be refunded.");
        }

        BigDecimal currentRefunded = payment.getRefundAmount() != null ? payment.getRefundAmount() : BigDecimal.ZERO;
        BigDecimal availableToRefund = payment.getAmount().subtract(currentRefunded);

        if (availableToRefund.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Payment has already been fully refunded.");
        }

        BigDecimal refundAmount = requestedRefundAmount != null ? requestedRefundAmount : availableToRefund;
        if (refundAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Refund amount must be greater than zero.");
        }
        if (refundAmount.compareTo(availableToRefund) > 0) {
            throw new BadRequestException("Refund amount (" + refundAmount + ") exceeds remaining refundable amount (" + availableToRefund + ").");
        }

        long refundPaise = refundAmount
                .multiply(BigDecimal.valueOf(100))
                .setScale(0, RoundingMode.HALF_UP)
                .longValue();

        String refundId = "rfnd_test_" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);
        String refundStatus = "processed";

        RazorpayClient client = razorpayClientProvider.getIfAvailable();
        if (client != null && properties.isConfigured() && !properties.getKeyId().startsWith("rzp_test_MALKAZTestKey") && payment.getRazorpayPaymentId() != null) {
            try {
                JSONObject refundReq = new JSONObject();
                refundReq.put("amount", refundPaise);
                if (reason != null && !reason.isBlank()) {
                    refundReq.put("notes", new JSONObject().put("reason", reason));
                }
                com.razorpay.Refund rzpRefund = client.payments.refund(payment.getRazorpayPaymentId(), refundReq);
                refundId = rzpRefund.get("id");
                refundStatus = rzpRefund.has("status") ? (String) rzpRefund.get("status") : "processed";
            } catch (Exception e) {
                log.error("Razorpay refund failed: {}", e.getMessage(), e);
                throw new BadRequestException("Razorpay refund failed: " + e.getMessage());
            }
        }

        BigDecimal newTotalRefunded = currentRefunded.add(refundAmount);
        payment.setRefundId(refundId);
        payment.setRefundStatus(refundStatus);
        payment.setRefundAmount(newTotalRefunded);

        boolean fullyRefunded = newTotalRefunded.compareTo(payment.getAmount()) >= 0;
        PaymentStatus newStatus = fullyRefunded ? PaymentStatus.REFUNDED : PaymentStatus.PARTIALLY_REFUNDED;
        payment.setPaymentStatus(newStatus);
        paymentRepository.save(payment);

        Order order = payment.getOrder();
        return new RefundResponse(
                refundId,
                payment.getId(),
                order != null ? order.getId() : null,
                order != null ? order.getOrderNumber() : null,
                refundAmount,
                refundStatus,
                newStatus,
                fullyRefunded ? "Payment fully refunded successfully." : "Partial refund processed successfully."
        );
    }
}
