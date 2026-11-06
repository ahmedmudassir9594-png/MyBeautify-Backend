# MALKAZ Luxury Jewellery — Payment Security Implementation Report

## 1. Security Architecture & Threat Mitigation Matrix

| Vulnerability / Attack Vector | Risk Level | Mitigation Architecture Implemented | Verification Test |
|---|---|---|---|
| **Client-side Amount Tampering** | Critical | Amount is completely excluded from client checkout requests. All order items, product prices, discounts, taxes, and shipping fees are computed server-side from database entities. The Razorpay order is created with this exact server amount. | `testPhaseACreateRazorpayOrder_Success` |
| **Payment Signature Forgery** | Critical | Razorpay checkout signature is cryptographically verified using `HmacSHA256(order_id + "\|" + payment_id, secret)` with constant-time equality checks (`MessageDigest.isEqual`) to prevent timing side-channel attacks. | `testPhaseBVerifyPayment_InvalidSignature_Rejected` |
| **Replay Attacks / Duplicate Stock Deduction** | High | `verifyRazorpayPayment` verifies if the target order is already `CONFIRMED` and payment is `SUCCESS`. If verified previously, returns cached confirmation idempotently without deducting stock again. | `testPhaseBVerifyPayment_Idempotent` |
| **Insecure Direct Object Reference (IDOR)** | High | Both `POST /api/payments/razorpay/verify` and `GET /api/payments/{orderId}` verify that `order.getUser().getId().equals(currentUser.getId())` unless caller possesses `ROLE_ADMIN` or `ROLE_STAFF`. Unauthorized cross-account access is rejected with `401/403`. | `testPhaseBVerifyPayment_IDOR_Protection` |
| **Disallowed Payment Channels (Cards)** | Medium | Credit and debit card payment methods (`CREDIT_CARD`, `DEBIT_CARD`, `CARD`) are rejected at both Jackson deserialization and gateway check layers. If Razorpay returns `method == "card"`, the backend flags and rejects the payment. | `testDisallowedPaymentMethods` |
| **Mock Gateway Bypass in Production** | Critical | `MockPaymentGateway.java` was deleted from the codebase. `PaymentMethod.MOCK` was removed. `ProductionPaymentGateway` strictly rejects mock payments and routes only authentic channels. | Entire test suite (155 tests) |
| **Plaintext Credential Exposure** | Critical | Removed hardcoded Google Mail App Password and sensitive fallbacks from `application.yml`. All credentials now require external environment variables (`MAIL_USERNAME`, `MAIL_PASSWORD`, `RAZORPAY_KEY_ID`, `RAZORPAY_KEY_SECRET`, `RAZORPAY_WEBHOOK_SECRET`). | Code inspection & YAML validation |
| **Webhook Spoofing & Replay** | High | `RazorpayWebhookController` verifies raw body UTF-8 HMAC against `X-Razorpay-Signature`. Deduplication via table `payment_webhook_events` ensures duplicate webhook events cannot re-trigger actions. | `testWebhook_PaymentCaptured_Success`, `testWebhook_InvalidSignature_Rejected` |
| **Unauthorized Refund Initiation** | Critical | Refund endpoint `/api/admin/payments/{paymentId}/refund` is restricted to `ROLE_ADMIN` via Spring Security URL matching and `@PreAuthorize("hasRole('ADMIN')")`. | `testAdminRefund_FullAndPartial` |

---

## 2. Deep Dive: Key Security Mitigations

### 2.1 Cryptographic Signature Verification (`RazorpaySignatureUtil.java`)
```java
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
```
**Security Attributes:**
1. Uses `MessageDigest.isEqual()` to enforce constant-time byte comparison, mitigating timing attacks.
2. UTF-8 byte encoding ensures platform-independent character canonicalization.

### 2.2 Server-Side Price Calculation & Anti-Tampering (`OrderService.java`)
The client can only send `shippingAddressId`, `paymentMethod`, and `couponCode`. The total is calculated server-side:
```java
// Client cannot submit "total" or "price".
BigDecimal subtotal = BigDecimal.ZERO;
for (CartItem item : cart.getItems()) {
    Product product = item.getProduct();
    // Validate live status and stock from DB
    if (!product.isActive()) throw new BadRequestException(...);
    if (product.getStockQuantity() < requiredQuantity) throw new InsufficientStockException(...);
    subtotal = subtotal.add(product.sellingPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
}
```

### 2.3 Webhook Idempotency Architecture (`RazorpayWebhookService.java`)
```java
if (webhookEventRepository.existsByRazorpayEventId(eventId)) {
    log.info("Webhook event {} already processed. Skipping duplicate.", eventId);
    return;
}
PaymentWebhookEvent webhookEvent = new PaymentWebhookEvent(eventId, eventType, "PROCESSING", rawPayload);
webhookEvent = webhookEventRepository.save(webhookEvent);
```
**Security Attributes:**
1. Database unique constraint on `razorpay_event_id` physically prevents race conditions.
2. Webhooks that arrive concurrently are de-duplicated at the transaction boundary.

---

## 3. Secret Rotation & Hygiene Recommendations

1. **Gmail App Password Rotation:**
   The previously hardcoded password `vcyp qtwk swep mxqj` was removed from `application.yml`. The Google account holder should:
   - Navigate to Google Account > Security > 2-Step Verification > App Passwords.
   - Revoke the compromised password and generate a new password.
   - Store the new password exclusively in production environment variable `MAIL_PASSWORD`.

2. **Git Commit History Cleanup:**
   Use `git-filter-repo` or BFG Repo-Cleaner if public repository mirroring is used, to rewrite any earlier commits where test credentials were committed.

