# MALKAZ Luxury Jewellery — Razorpay Payment Gateway Integration

## 1. Executive Summary

This document describes the enterprise-grade payment processing architecture implemented for **MALKAZ Luxury Jewellery**. The previous mock payment system (`MockPaymentGateway`) has been completely removed from production and replaced with official **Razorpay Java SDK (v1.4.8)** integration, hardened against financial tampering, replay attacks, duplicate stock deductions, and unauthorized privilege escalation.

### Permitted & Disabled Payment Channels
- **Permitted Online Methods:** UPI (`UPI`), Dynamic UPI QR Code (`UPI_QR`), and Net Banking (`NET_BANKING`).
- **Permitted Offline Method:** Cash on Delivery (`CASH_ON_DELIVERY`).
- **Disabled/Blocked Channels:** Credit and Debit Cards (`CREDIT_CARD`, `DEBIT_CARD`, `CARD`) are strictly rejected at API validation and gateway verification levels. Mock payments (`MOCK`) have been removed from the system.

---

## 2. Architectural Overview

The integration follows a secure **Two-Phase Checkout** model to ensure transaction integrity:

```
[Customer Browser / Mobile]           [MALKAZ Spring Boot Backend]            [Razorpay Gateway API]
           |                                       |                                    |
           | ----- Phase A: Create Order --------> |                                    |
           |       POST /payments/razorpay/        |                                    |
           |       create-order                    |                                    |
           |                                       | -- Validates Cart, Stock, Prices   |
           |                                       | -- Computes Amount (Paise)         |
           |                                       | -- Generates Pending Order & Pay   |
           |                                       | -- orders.create() --------------> |
           |                                       | <----------------- rzp_order_id -- |
           | <---- Return Checkout Payload ------- |                                    |
           |       (amount, rzp_order_id, key_id)  |                                    |
           |                                       |                                    |
           | ====== Customer Completes UPI/Net Banking on Razorpay Modal / App ======== |
           |                                       |                                    |
           | ----- Phase B: Verify Signature ----> |                                    |
           |       POST /payments/razorpay/verify  |                                    |
           |                                       | -- HMAC-SHA256 Verification        |
           |                                       | -- Disallows Card Payments         |
           |                                       | -- Idempotent DB Commit            |
           |                                       | -- Inventory Deducted Atomically   |
           |                                       | -- Cart Cleared & Confirmation Sent|
           | <---- 200 OK (Confirmed Order) ------ |                                    |
           |                                       |                                    |
           |                                       | <---- Webhook: payment.captured -- |
           |                                       |       (Failsafe / Out-of-Band)     |
           |                                       | -- HMAC-SHA256 Webhook Sig Check   |
           |                                       | -- Deduplicates Event              |
           |                                       | -- Confirms if Phase B Dropped     |
```

---

## 3. Database Schema Upgrades

Flyway migration `V7__create_razorpay_payment_and_webhook_tables.sql` upgraded the database:

### 3.1 `payments` Table Additions
- `razorpay_order_id`: Indexed identifier returned by Razorpay Orders API.
- `razorpay_payment_id`: Indexed identifier representing the completed payment transaction.
- `razorpay_signature`: Cryptographic HMAC SHA-256 signature returned by Razorpay Checkout.
- `currency`: Defaulting to `INR`.
- `failure_code`: Gateway failure reason code (e.g. `INVALID_SIGNATURE`, `BAD_REQUEST_ERROR`).
- `failure_reason`: Human-readable error description.
- `refund_id`: Razorpay refund transaction identifier.
- `refund_status`: Status of refund (`processed`, `failed`, `pending`).
- `refund_amount`: Cumulative refunded monetary value.
- `updated_at`: Automatically updated timestamp.

### 3.2 `payment_webhook_events` Table (Idempotency Engine)
- `id`: Primary key (BIGINT AUTO_INCREMENT).
- `razorpay_event_id`: Unique index enforcing single-execution per gateway webhook event.
- `event_type`: Event type (`payment.captured`, `payment.failed`, `order.paid`, `refund.processed`).
- `processing_status`: Status (`PROCESSING`, `PROCESSED`, `FAILED`, `DUPLICATE_IGNORED`).
- `payload`: Raw JSON payload.
- `received_at`: Timestamp event received.
- `processed_at`: Timestamp event processing finalized.
- `error_message`: Error information if processing failed.

---

## 4. Source Files Modified & Created

| File | Type | Description |
|---|---|---|
| `pom.xml` | Modified | Added official `com.razorpay:razorpay-java:1.4.8` SDK. |
| `application.yml` | Modified | Removed plaintext Gmail password; added Razorpay configuration block with environment variables. |
| `application-test.yml` | Modified | Added simulated test keys for automated test runs. |
| `V7__create_razorpay_payment_and_webhook_tables.sql` | Created | Database migration for payment tracking and webhook idempotency. |
| `PaymentMethod.java` | Modified | Replaced `MOCK` with `UPI`, `UPI_QR`, `NET_BANKING`, `CASH_ON_DELIVERY`; explicit rejection of cards and mock. |
| `PaymentStatus.java` | Modified | Added `PENDING`, `AUTHORIZED`, `SUCCESS`, `FAILED`, `REFUND_PENDING`, `REFUNDED`, `PARTIALLY_REFUNDED`. |
| `Payment.java` | Modified | Added Razorpay transaction identifiers, signatures, refund tracking, and audit fields. |
| `PaymentWebhookEvent.java` | Created | Entity storing processed webhook events to guarantee idempotency. |
| `PaymentRepository.java` | Modified | Added finders by `razorpayOrderId`, `razorpayPaymentId`, `transactionId`, `orderId`. |
| `PaymentWebhookEventRepository.java` | Created | Repository for querying and checking uniqueness of webhook event IDs. |
| `RazorpayProperties.java` | Created | Strongly typed configuration properties for Razorpay credentials. |
| `RazorpayConfig.java` | Created | Spring bean definition initializing `RazorpayClient`. |
| `RazorpaySignatureUtil.java` | Created | Constant-time HMAC-SHA256 signature verification for checkouts and webhooks. |
| `RazorpayService.java` | Created | Official SDK wrapper for order creation, dynamic QR codes, live payment verification, and refunds. |
| `RazorpayWebhookService.java` | Created | Idempotent webhook receiver handling raw HMAC signatures and order finalization. |
| `OrderService.java` | Modified | Refactored checkout into two-phase checkout; removed mock gateway; added COD handling. |
| `ProductionPaymentGateway.java` | Created | Replaced `MockPaymentGateway`; isolates COD from online payments. |
| `RazorpayPaymentController.java` | Created | Customer endpoints: Phase A order creation, Phase B verification, dynamic QR, payment status. |
| `RazorpayWebhookController.java` | Created | Public endpoint for Razorpay webhook callbacks (`POST /api/payments/razorpay/webhook`). |
| `AdminPaymentController.java` | Created | Admin-only refund processing endpoint (`POST /api/admin/payments/{paymentId}/refund`). |
| `SecurityConfig.java` | Modified | Permitted webhook endpoint without JWT; secured admin refund endpoint with `ROLE_ADMIN`. |
| `RazorpayPaymentIntegrationTest.java` | Created | 11 comprehensive automated tests covering all checkout, verification, webhook, and refund flows. |

