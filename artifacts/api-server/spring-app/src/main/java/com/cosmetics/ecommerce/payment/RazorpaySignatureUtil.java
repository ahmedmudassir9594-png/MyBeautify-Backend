package com.cosmetics.ecommerce.payment;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

public final class RazorpaySignatureUtil {
    private static final String HMAC_SHA256 = "HmacSHA256";

    private RazorpaySignatureUtil() {}

    /**
     * Verifies payment signature returned by Razorpay Checkout.
     * Expected data: orderId + "|" + paymentId
     */
    public static boolean verifyPaymentSignature(String orderId, String paymentId, String signature, String secret) {
        if (orderId == null || paymentId == null || signature == null || secret == null) {
            return false;
        }
        try {
            String payload = orderId + "|" + paymentId;
            String generated = calculateHmacSha256(payload, secret);
            return MessageDigest.isEqual(
                    generated.getBytes(StandardCharsets.UTF_8),
                    signature.trim().getBytes(StandardCharsets.UTF_8)
            );
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Verifies webhook signature sent in X-Razorpay-Signature header.
     * Expected data: exact raw request payload
     */
    public static boolean verifyWebhookSignature(String rawPayload, String signature, String webhookSecret) {
        if (rawPayload == null || signature == null || webhookSecret == null) {
            return false;
        }
        try {
            String generated = calculateHmacSha256(rawPayload, webhookSecret);
            return MessageDigest.isEqual(
                    generated.getBytes(StandardCharsets.UTF_8),
                    signature.trim().getBytes(StandardCharsets.UTF_8)
            );
        } catch (Exception e) {
            return false;
        }
    }

    public static String calculateHmacSha256(String data, String secret) throws Exception {
        Mac mac = Mac.getInstance(HMAC_SHA256);
        SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256);
        mac.init(secretKeySpec);
        byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hash);
    }
}

