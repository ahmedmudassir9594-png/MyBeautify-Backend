# MALKAZ Luxury Jewellery — Razorpay Payment API Documentation

All payment endpoints are hosted under the context path `/api`. All endpoints except the Razorpay Webhook endpoint require a valid JWT Bearer token in the `Authorization` header.

---

## 1. Customer Endpoints

### 1.1 Phase A: Create Razorpay Order
Initiates checkout by validating cart inventory and computing live order totals on the server. Creates a pending order and a Razorpay gateway order.

- **Method:** `POST`
- **Path:** `/api/payments/razorpay/create-order`
- **Authentication:** `Bearer <JWT_TOKEN>` (Role: `CUSTOMER`, `ADMIN`, `STAFF`)
- **Headers:** `Content-Type: application/json`

#### Request Payload
```json
{
  "shippingAddressId": 1,
  "paymentMethod": "UPI",
  "couponCode": "FESTIVE10"
}
```
*Note: `paymentMethod` must be one of `UPI`, `UPI_QR`, `NET_BANKING`. If empty or containing card values, a 400 Bad Request is returned. The `amount` field is strictly computed server-side to prevent tampering.*

#### Response Payload (`200 OK`)
```json
{
  "success": true,
  "message": "Razorpay payment order created successfully.",
  "data": {
    "orderId": 42,
    "orderNumber": "COS-2026-A1B2C3D4",
    "razorpayOrderId": "order_EKwxwAgituvdBO",
    "amount": 2698.92,
    "amountInPaise": 269892,
    "currency": "INR",
    "razorpayKeyId": "rzp_live_xxxxxxxxxxxx",
    "customerName": "Fatima Zahra",
    "customerEmail": "fatima@example.com",
    "customerPhone": "+919876543210",
    "companyName": "MALKAZ Luxury Jewellery",
    "themeColor": "#0A3B2C",
    "paymentMethod": "UPI"
  }
}
```

---

### 1.2 Phase B: Verify Payment Signature
Verifies the cryptographic HMAC SHA-256 signature returned by Razorpay Checkout upon payment completion. Upon verification, the order is confirmed, stock is deducted, the customer's cart is cleared, and an email confirmation is sent.

- **Method:** `POST`
- **Path:** `/api/payments/razorpay/verify`
- **Authentication:** `Bearer <JWT_TOKEN>` (Must be owner of the order or Admin)
- **Headers:** `Content-Type: application/json`

#### Request Payload
```json
{
  "orderId": 42,
  "razorpayOrderId": "order_EKwxwAgituvdBO",
  "razorpayPaymentId": "pay_29QQoUBi66xm2f",
  "razorpaySignature": "9ef4700e129b35b029823429f10e92e0924007f3d9a0e121497ccd50e75413f0"
}
```

#### Response Payload (`200 OK`)
```json
{
  "success": true,
  "message": "Payment verified and order confirmed successfully.",
  "data": {
    "orderId": 42,
    "orderNumber": "COS-2026-A1B2C3D4",
    "paymentStatus": "SUCCESS",
    "transactionId": "TXN-83F19A2BCDE109",
    "razorpayPaymentId": "pay_29QQoUBi66xm2f",
    "amount": 2698.92,
    "message": "Payment verified successfully. Order confirmed."
  }
}
```

#### Error Response — Invalid Signature (`400 Bad Request`)
```json
{
  "success": false,
  "message": "Payment verification failed: Invalid Razorpay signature.",
  "status": 400,
  "timestamp": "2026-11-06T03:00:00Z"
}
```

---

### 1.3 Dynamic UPI QR Code Generation
Generates a dedicated, single-use UPI QR code for the customer's order.

- **Method:** `POST`
- **Path:** `/api/payments/razorpay/qr-code`
- **Authentication:** `Bearer <JWT_TOKEN>`
- **Headers:** `Content-Type: application/json`

#### Request Payload
```json
{
  "shippingAddressId": 1,
  "couponCode": null
}
```

#### Response Payload (`200 OK`)
```json
{
  "success": true,
  "message": "Dynamic UPI QR code generated successfully.",
  "data": {
    "orderId": 43,
    "orderNumber": "COS-2026-F9E8D7C6",
    "razorpayOrderId": "order_MLKZ98765432",
    "qrCodeId": "qr_1234567890abcdef",
    "qrImageUrl": "https://api.qrserver.com/v1/create-qr-code/?size=300x300&data=upi...",
    "paymentUrl": "upi://pay?pa=malkaz.payments@icici&pn=MALKAZ%20Jewellery&am=1499.00&cu=INR&tn=Order%20COS-2026-F9E8D7C6",
    "amount": 1499.00,
    "amountInPaise": 149900,
    "currency": "INR",
    "status": "active"
  }
}
```

---

### 1.4 Get Payment Status
Retrieves full payment details and lifecycle status for a specific order.

- **Method:** `GET`
- **Path:** `/api/payments/{orderId}`
- **Authentication:** `Bearer <JWT_TOKEN>` (Must be order owner or Admin)

#### Response Payload (`200 OK`)
```json
{
  "success": true,
  "message": "Payment details retrieved successfully.",
  "data": {
    "paymentId": 55,
    "orderId": 42,
    "orderNumber": "COS-2026-A1B2C3D4",
    "transactionId": "TXN-83F19A2BCDE109",
    "paymentGateway": "RAZORPAY",
    "paymentMethod": "UPI",
    "paymentStatus": "SUCCESS",
    "amount": 2698.92,
    "currency": "INR",
    "razorpayOrderId": "order_EKwxwAgituvdBO",
    "razorpayPaymentId": "pay_29QQoUBi66xm2f",
    "paidAt": "2026-11-06T03:02:15Z",
    "createdAt": "2026-11-06T03:00:10Z",
    "failureCode": null,
    "failureReason": null,
    "refundId": null,
    "refundStatus": null,
    "refundAmount": null
  }
}
```

---

## 2. Webhook Endpoint

### 2.1 Razorpay Webhook Handler
Receives asynchronous event notifications directly from Razorpay's servers.

- **Method:** `POST`
- **Path:** `/api/payments/razorpay/webhook`
- **Authentication:** None (Exempt from JWT in `SecurityConfig`). Protected by `X-Razorpay-Signature` HMAC SHA-256 header.
- **Headers:**
  - `Content-Type: application/json`
  - `X-Razorpay-Signature: <HMAC_HEX_DIGEST>`

#### Supported Events
- `payment.captured`
- `order.paid`
- `payment.failed`
- `refund.processed`

#### Response (`200 OK`)
```json
{
  "status": "ok"
}
```

#### Idempotency Behavior
Duplicate webhooks with the same `id` are logged and immediately returned with `200 OK` without triggering duplicate inventory or order modifications.

---

## 3. Admin Endpoints

### 3.1 Process Full or Partial Refund
Allows administrators to refund captured Razorpay transactions directly from the admin panel.

- **Method:** `POST`
- **Path:** `/api/admin/payments/{paymentId}/refund`
- **Authentication:** `Bearer <JWT_TOKEN>` (Strictly requires `ROLE_ADMIN`)
- **Headers:** `Content-Type: application/json`

#### Request Payload
```json
{
  "amount": 500.00,
  "reason": "Customer damaged item return"
}
```
*Note: If `amount` is omitted or null, the full remaining refundable balance is refunded.*

#### Response Payload (`200 OK`)
```json
{
  "success": true,
  "message": "Partial refund processed successfully.",
  "data": {
    "refundId": "rfnd_K8m9201934981",
    "paymentId": 55,
    "orderId": 42,
    "orderNumber": "COS-2026-A1B2C3D4",
    "refundAmount": 500.00,
    "refundStatus": "processed",
    "paymentStatus": "PARTIALLY_REFUNDED",
    "message": "Partial refund processed successfully."
  }
}
```

---

## 4. Cash on Delivery (COD) Endpoint

### 4.1 Place COD Order
Standard checkout endpoint for Cash on Delivery.

- **Method:** `POST`
- **Path:** `/api/orders`
- **Authentication:** `Bearer <JWT_TOKEN>`
- **Headers:** `Content-Type: application/json`

#### Request Payload
```json
{
  "shippingAddressId": 1,
  "paymentMethod": "CASH_ON_DELIVERY",
  "couponCode": null
}
```

#### Response Payload (`200 OK`)
```json
{
  "success": true,
  "message": "Order placed successfully.",
  "data": {
    "id": 44,
    "orderNumber": "COS-2026-98CBA765",
    "orderStatus": "CONFIRMED",
    "paymentStatus": "PENDING",
    "subtotal": 1499.00,
    "shippingCharge": 8.99,
    "taxAmount": 119.92,
    "totalAmount": 1627.91,
    "paymentMethod": "CASH_ON_DELIVERY"
  }
}
```

