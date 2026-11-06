# MALKAZ Luxury Jewellery — Razorpay Production Deployment Guide

## 1. Environment Variables Configuration

In production environments (e.g., Docker, AWS ECS, Kubernetes, Render, Railway, or Heroku), configure the following environment variables:

```bash
# ==========================================
# RAZORPAY PRODUCTION GATEWAY CONFIGURATION
# ==========================================
RAZORPAY_KEY_ID=rzp_live_xxxxxxxxxxxxxxxx
RAZORPAY_KEY_SECRET=yyyyyyyyyyyyyyyyyyyyyyyy
RAZORPAY_WEBHOOK_SECRET=zzzzzzzzzzzzzzzzzzzzzzzz
RAZORPAY_CURRENCY=INR
RAZORPAY_COMPANY_NAME="MALKAZ Luxury Jewellery"
RAZORPAY_THEME_COLOR="#0A3B2C"

# ==========================================
# MAIL CONFIGURATION (SENSITIVE - NO HARDCODED DEFAULTS)
# ==========================================
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=your_production_email@malkaz.com
MAIL_PASSWORD=your_secure_app_password
MAIL_AUTH=true
MAIL_STARTTLS=true

# ==========================================
# CORS AND DOMAIN CONFIGURATION
# ==========================================
CORS_ALLOWED_ORIGINS=https://malkaz.vercel.app,https://fashionmalkaz.com,https://www.fashionmalkaz.com
```

> [!CAUTION]
> Never commit `RAZORPAY_KEY_SECRET` or `MAIL_PASSWORD` into source control. Any existing compromised credentials in Git commit history must be revoked and rotated immediately in Google Account and Razorpay Dashboard.

---

## 2. Razorpay Dashboard Webhook Configuration

To receive real-time out-of-band updates when customers complete payments via UPI apps or Net Banking:

1. Log in to the [Razorpay Dashboard](https://dashboard.razorpay.com).
2. Navigate to **Settings** > **Webhooks**.
3. Click **Add New Webhook**.
4. Configure the fields:
   - **Webhook URL:** `https://api.fashionmalkaz.com/api/payments/razorpay/webhook` (or your active production backend domain + context path `/api/payments/razorpay/webhook`).
   - **Secret:** Enter a strong 32-character random string (this must match `RAZORPAY_WEBHOOK_SECRET` in your backend environment).
   - **Alert Email:** `alerts@fashionmalkaz.com` (for delivery issue notifications).
5. Select the following **Active Events**:
   - `payment.captured`
   - `payment.failed`
   - `order.paid`
   - `refund.processed`
6. Click **Save**.

---

## 3. Frontend Razorpay Checkout Integration Snippet

On the React frontend, when the customer clicks "Pay with UPI / Net Banking", execute the following two-phase flow:

```javascript
// Step 1: Call Phase A backend endpoint to compute server-side amount & order
const response = await fetch('/api/payments/razorpay/create-order', {
  method: 'POST',
  headers: {
    'Content-Type': 'application/json',
    'Authorization': `Bearer ${userJwtToken}`
  },
  body: JSON.stringify({
    shippingAddressId: selectedAddressId,
    paymentMethod: 'UPI', // or 'NET_BANKING', 'UPI_QR'
    couponCode: appliedCouponCode || null
  })
});
const { data: orderData } = await response.json();

// Step 2: Open Razorpay Checkout Modal
// Note: 'method' config restricts customer options to UPI and Net Banking ONLY.
const options = {
  key: orderData.razorpayKeyId,
  amount: orderData.amountInPaise,
  currency: orderData.currency,
  name: orderData.companyName,
  description: `Order #${orderData.orderNumber}`,
  order_id: orderData.razorpayOrderId,
  prefill: {
    name: orderData.customerName,
    email: orderData.customerEmail,
    contact: orderData.customerPhone
  },
  theme: {
    color: orderData.themeColor || '#0A3B2C'
  },
  // STRICTLY DISABLE CARDS & ENFORCE UPI/NET BANKING
  config: {
    display: {
      blocks: {
        banks: {
          name: "Pay via UPI or Net Banking",
          instruments: [
            { method: "upi" },
            { method: "netbanking" }
          ]
        }
      },
      sequence: ["block.banks"],
      preferences: {
        show_default_blocks: false
      }
    }
  },
  handler: async function (response) {
    // Step 3: Call Phase B backend verification endpoint
    const verifyRes = await fetch('/api/payments/razorpay/verify', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${userJwtToken}`
      },
      body: JSON.stringify({
        orderId: orderData.orderId,
        razorpayOrderId: response.razorpay_order_id,
        razorpayPaymentId: response.razorpay_payment_id,
        razorpaySignature: response.razorpay_signature
      })
    });
    const verifyResult = await verifyRes.json();
    if (verifyResult.success) {
      window.location.href = `/order-confirmation/${orderData.orderNumber}`;
    } else {
      alert("Payment verification failed: " + verifyResult.message);
    }
  },
  modal: {
    ondismiss: function () {
      console.log("Customer closed the checkout modal.");
    }
  }
};

const razorpay = new window.Razorpay(options);
razorpay.open();
```

---

## 4. Production Verification Checklist

Before launching to live customers:

- [x] Run Flyway migration `V7__create_razorpay_payment_and_webhook_tables.sql` against the production MySQL database.
- [x] Ensure `RAZORPAY_KEY_ID` starts with `rzp_live_`.
- [x] Ensure `RAZORPAY_KEY_SECRET` matches the Live Key secret in Razorpay Dashboard.
- [x] Test UPI Intent checkout on a mobile device and verify that payment captures cleanly.
- [x] Trigger an invalid signature test using curl and verify the request is rejected with `400 Bad Request`.
- [x] Verify webhook delivery from the Razorpay Dashboard (click "Send Test Webhook" for `payment.captured`).
- [x] Verify that Card payment attempts are blocked at both client and server layers.
- [x] Confirm that Cash on Delivery orders initialize payment status as `PENDING` and allow customer order completion.

