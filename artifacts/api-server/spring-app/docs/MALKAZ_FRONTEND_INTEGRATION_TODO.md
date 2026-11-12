# MALKAZ — Frontend Integration Developer Action Plan & TODO Checklist (Reconciled)

**Document Version:** 2.0.0 (Reconciled from Authoritative Source Code)  
**Target Audience:** Frontend React/TypeScript Developers  
**Scope:** Actionable tasks to achieve 100% backend feature integration, resolve critical blockers, and enforce the MALKAZ Payment Policy.  

---

## 1. Verified Completed Integrations (Evidence-Based)

The following 12 functional domains are **fully implemented and verified** in both backend and frontend source code:

| Domain | Backend Files & Endpoints | Frontend Files & Components | Verification Evidence |
|---|---|---|---|
| **Customer Auth & JWT** | `AuthController.java` (`/api/auth/register`, `/login`, `/refresh-token`) | `auth.service.ts`, `AuthPage.tsx`, `client.ts` | Stateless JWT stored; Axios interceptor attaches `Authorization: Bearer <token>`; token auto-refreshes on 401. |
| **Google OAuth 2.0** | `AuthController.java` (`/api/auth/google`) | `auth.service.ts` (`googleLogin`), `AuthPage.tsx` | Validates Google credential token and returns session tokens. |
| **Customer Profile** | `AuthController.java` (`/api/auth/me`) | `auth.service.ts`, `CustomerDashboard.tsx` | Profile details fetch and update operational. |
| **Address Book** | `OrderController.java` (`/api/addresses/**`) | `addresses.service.ts`, `AddressModal.tsx` | Full CRUD for delivery addresses, default toggle, checkout address selector. |
| **Product Catalog & Search** | `ProductController.java` (`/api/products/**`) | `products.service.ts`, `CustomerDashboard.tsx` | Multi-filter query params (category, price, sorting, pagination) mapped correctly. |
| **Product Relationships** | `ProductController.java` (`/api/products/{id}/similar`) | `products.service.ts` (`fetchSimilarProducts`), `ProductDetailPage.tsx` | "Complete the Look" recommendations active. |
| **Category Management** | `CatalogController.java` (`/api/categories`, `/api/admin/categories/**`) | `categories.service.ts`, `AdminPage.tsx` (`Categories` tab) | Admin category CRUD with multipart image upload operational. |
| **Custom Combo Builder (BYOC)** | `ComboController.java` (`/api/combos/products`, `/custom/preview`, `/custom/cart`) | `combos.service.ts`, `CombosOffersPage.tsx` | Dynamic slot selection, real-time preview, tiered bundle discounts operational. |
| **Customer Wishlist** | `WishlistController.java` (`/api/wishlist/**`) | `wishlist.service.ts`, `CustomerDashboard.tsx` | Add, remove, and move wishlist items to cart operational. |
| **Customer Checkout (COD)** | `OrderController.java` (`POST /api/orders`) | `orders.service.ts`, `CheckoutPage.tsx` | Cash on Delivery orders created, stock deducted, customer redirected to confirmation. |
| **Referral Program** | `ReferralController.java` (`/api/referrals/**`) | `referral.service.ts`, `ShareReferralPage.tsx`, `AuthPage.tsx` | Referral code generation, link sharing, registration attribution active. |
| **Enterprise Audit Logging** | `AuditLogController.java` (`/api/admin/audit-logs/**`) | `audit.service.ts`, `AuditLogsView.tsx` in `AdminPage.tsx` | Audit logs viewable by admin with actor, action, and entity filters. |

---

## 2. Broken Integrations (Must Be Fixed First)

### Broken Item 1: Online Checkout Blocked by 'MOCK' Method
- **Status:** `BROKEN INTEGRATION (CRITICAL BLOCKER)`
- **Affected File:** [`masterim/.../src/pages/checkout/CheckoutPage.tsx`](file:///c:/Users/HP/OneDrive/Desktop/MALKAZ/masterim/MyBeautify-cosmetics-store/artifacts/mybeautify-store/src/pages/checkout/CheckoutPage.tsx)
- **Root Cause:** When user selects "Online Payment", frontend opens a fake modal and posts `paymentMethod: 'MOCK'` to `POST /api/orders`. Backend `OrderServiceImpl` explicitly throws: `IllegalArgumentException: MOCK payment method is strictly forbidden in production`.
- **Impact:** 100% of online payment attempts fail with HTTP 400.

### Broken Item 2: Combo Group Deletion Endpoint URL Mismatch
- **Status:** `BROKEN INTEGRATION (HIGH PRIORITY)`
- **Affected File:** [`masterim/.../src/services/combos/combos.service.ts`](file:///c:/Users/HP/OneDrive/Desktop/MALKAZ/masterim/MyBeautify-cosmetics-store/artifacts/mybeautify-store/src/services/combos/combos.service.ts#L71)
- **Root Cause:** Calls `DELETE /api/cart/combos/${comboGroupId}`. Backend `ComboController.java` maps this at `DELETE /api/combos/cart/groups/{comboGroupId}`.
- **Impact:** Removing a custom combo group from cart returns HTTP 404 Not Found.

### Broken Item 3: Cards Removal Policy Violation in Frontend
- **Status:** `POLICY VIOLATION (HIGH PRIORITY)`
- **Affected File:** [`masterim/.../src/pages/checkout/CheckoutPage.tsx`](file:///c:/Users/HP/OneDrive/Desktop/MALKAZ/masterim/MyBeautify-cosmetics-store/artifacts/mybeautify-store/src/pages/checkout/CheckoutPage.tsx)
- **Root Cause:** Renders a "Cards" button tab (lines 1358–1371) and credit card input fields (lines 1408–1430). Backend `PaymentMethod.java` and `RazorpayService.java` strictly disallow card payments.
- **Impact:** Misleads customers into entering card details that MALKAZ does not accept.

---

## 3. Prioritized Actionable Developer Checklist

```
┌────────────────────────────────────────────────────────────────────────┐
│  P0: CRITICAL BLOCKERS (Payment & Security)                            │
│      ├── Task 1: Implement Official Razorpay SDK & Online Checkout    │
│      └── Task 2: Remove Cards UI from Checkout (Policy Enforcement)   │
├────────────────────────────────────────────────────────────────────────┤
│  P1: HIGH PRIORITY (Core Architecture & URL Fixes)                     │
│      ├── Task 3: Fix Combo Group Deletion Endpoint URL Mismatch        │
│      ├── Task 4: Build Subcategory Management Admin Panel & Types      │
│      ├── Task 5: Integrate Subcategories into Storefront Catalog       │
│      ├── Task 6: Implement Admin Orders Management & Status Updates   │
│      └── Task 7: Replace Combo Image URL with File Storage Upload      │
├────────────────────────────────────────────────────────────────────────┤
│  P2: MEDIUM PRIORITY (Admin Capabilities & Payment Enhancements)       │
│      ├── Task 8: Implement Dynamic UPI QR Code Scan-and-Pay Modal     │
│      ├── Task 9: Build Admin Coupon Management View                    │
│      ├── Task 10: Build Admin Review Moderation View                   │
│      ├── Task 11: Connect Admin Dashboard Overview to Backend KPI API  │
│      └── Task 12: Build Admin Payment Refund View                      │
├────────────────────────────────────────────────────────────────────────┤
│  P3: CLEANUP & POLISHING                                               │
│      ├── Task 13: Remove Deprecated Brand System (Dead Code 404s)      │
│      └── Task 14: Align Shipping Calculation with Backend Logic        │
└────────────────────────────────────────────────────────────────────────┘
```

---

### P0: Critical Blockers (Payment & Security)

#### Task 1: Implement Official Razorpay SDK & Online Checkout Flow
- [ ] **Priority:** `P0 - CRITICAL BLOCKER`
- [ ] **Target Files:**
  - `src/pages/checkout/CheckoutPage.tsx`
  - `src/services/payments/payments.service.ts` (create new service)
  - `src/types/payment.types.ts` (create new types)
- [ ] **Backend Endpoints:**
  - `POST /api/payments/razorpay/create-order`
  - `POST /api/payments/razorpay/verify`
- [ ] **Exact Verified Backend Contract:**
  - **Create Order Request (`POST /api/payments/razorpay/create-order`):**
    ```json
    {
      "shippingAddressId": 12,
      "paymentMethod": "UPI", // Allowed: "UPI", "UPI_QR", "NET_BANKING"
      "couponCode": "MALKAZ10" // Optional
    }
    ```
  - **Create Order Response:**
    ```json
    {
      "success": true,
      "data": {
        "orderId": 45,
        "orderNumber": "ORD-2026-0045",
        "razorpayOrderId": "order_EK3wd48gXDF18z",
        "amount": 24999.00,
        "amountInPaise": 2499900,
        "currency": "INR",
        "razorpayKeyId": "rzp_live_xxxxxxxxxxxx",
        "customerName": "Fatima Khan",
        "customerEmail": "customer@malkaz.com",
        "customerPhone": "+919876543210",
        "companyName": "MALKAZ Luxury Jewellery",
        "themeColor": "#0A3B2C",
        "paymentMethod": "UPI"
      }
    }
    ```
  - **Verify Payment Request (`POST /api/payments/razorpay/verify`):**
    ```json
    {
      "orderId": 45,
      "razorpayOrderId": "order_EK3wd48gXDF18z",
      "razorpayPaymentId": "pay_EK3xYZ12345678",
      "razorpaySignature": "4a718c3b99..."
    }
    ```
- [ ] **Implementation Steps:**
  1. Dynamically inject Razorpay Checkout SDK (`https://checkout.razorpay.com/v1/checkout.js`).
  2. When customer selects an online method (UPI or Net Banking) and clicks "Proceed to Pay":
     - Step 1: Call `POST /api/payments/razorpay/create-order` with `{ shippingAddressId, paymentMethod, couponCode }`.
     - Step 2: Open Razorpay modal with `options`:
       ```javascript
       const options = {
         key: data.razorpayKeyId,
         amount: data.amountInPaise,
         currency: data.currency,
         name: data.companyName,
         order_id: data.razorpayOrderId,
         prefill: { name: data.customerName, email: data.customerEmail, contact: data.customerPhone },
         theme: { color: data.themeColor },
         handler: async function (response) {
           await paymentsService.verifyPayment({
             orderId: data.orderId,
             razorpayOrderId: response.razorpay_order_id,
             razorpayPaymentId: response.razorpay_payment_id,
             razorpaySignature: response.razorpay_signature
           });
           // On success, redirect to order confirmation
         }
       };
       ```
  3. Eliminate all code passing `paymentMethod: 'MOCK'`.
- [ ] **Acceptance Criteria:**
  - Zero occurrences of `paymentMethod: 'MOCK'`.
  - Razorpay checkout modal opens seamlessly and verifies payment cryptographically.

---

#### Task 2: Remove Cards UI from Checkout (Policy Enforcement)
- [ ] **Priority:** `P0 - POLICY ENFORCEMENT`
- [ ] **Target Files:**
  - `src/pages/checkout/CheckoutPage.tsx`
- [ ] **Implementation Steps:**
  1. Remove "Cards" button tab (lines 1358–1371).
  2. Remove card input fields (Card Number, Expiry, CVV) (lines 1408–1430).
  3. Replace `<CreditCard />` icons with `<Smartphone />` (UPI) and `<Building2 />` (Net Banking).
  4. Display supported payment options:
     - **UPI** (Google Pay, PhonePe, Paytm, BHIM via Razorpay)
     - **Dynamic UPI QR Code**
     - **Net Banking**
     - **Cash on Delivery (COD)**
- [ ] **Acceptance Criteria:**
  - Cards are completely absent from selectable checkout payment methods.

---

### P1: High Priority (Core Architecture & URL Fixes)

#### Task 3: Fix Combo Group Deletion Endpoint URL Mismatch
- [ ] **Priority:** `P1 - HIGH`
- [ ] **Target Files:**
  - `src/services/combos/combos.service.ts`
- [ ] **Fix:**
  Update line 71 of `combos.service.ts`:
  ```typescript
  // BEFORE (Incorrect -> 404):
  await request<unknown>(`/cart/combos/${encodeURIComponent(comboGroupId)}`, { method: 'DELETE' });

  // AFTER (Corrected):
  await request<unknown>(`/combos/cart/groups/${encodeURIComponent(comboGroupId)}`, { method: 'DELETE' });
  ```
- [ ] **Acceptance Criteria:**
  - Custom combo groups can be deleted from cart without 404 errors.

---

#### Task 4: Build Subcategory Management Admin Panel & Types
- [ ] **Priority:** `P1 - HIGH`
- [ ] **Target Files:**
  - `src/types/subcategory.types.ts`
  - `src/services/subcategories/subcategories.service.ts`
  - `src/pages/admin/SubcategoriesAdminView.tsx`
  - `src/pages/admin/AdminPage.tsx`
- [ ] **Backend Endpoints:**
  - `GET /api/admin/subcategories`
  - `POST /api/admin/subcategories`
  - `PUT /api/admin/subcategories/{id}`
  - `PATCH /api/admin/subcategories/{id}/status?active={bool}`
  - `DELETE /api/admin/subcategories/{id}`
- [ ] **Payload Structure (`POST /api/admin/subcategories`):**
  ```json
  {
    "name": "Pendant Necklaces",
    "categoryId": 1,
    "description": "Elegant pendant necklaces collection",
    "imageUrl": "necklace_pendant.webp",
    "active": true
  }
  ```
- [ ] **Implementation Steps:**
  1. Create `subcategories.service.ts` connecting all admin endpoints.
  2. Implement `SubcategoriesAdminView.tsx` matching the MALKAZ dashboard mockup (parent category dropdown, subcategory name, description, image uploader, active toggle, subcategory listing table).
  3. Register "Subcategories" tab in `AdminPage.tsx`.
- [ ] **Acceptance Criteria:**
  - Admins can manage subcategories linked to parent categories.

---

#### Task 5: Integrate Subcategories into Storefront Catalog
- [ ] **Priority:** `P1 - HIGH`
- [ ] **Target Files:**
  - `src/pages/shop/CustomerDashboard.tsx`
  - `src/services/products/products.service.ts`
- [ ] **Backend Endpoints:**
  - `GET /api/subcategories?categoryId={id}`
  - `GET /api/products?subcategoryId={id}`
- [ ] **Implementation Steps:**
  1. When a category is selected in the catalog, fetch subcategories via `GET /api/subcategories?categoryId={id}`.
  2. Display subcategory pills underneath the category bar.
  3. Append `subcategoryId` to `fetchProducts` queries.
- [ ] **Acceptance Criteria:**
  - Storefront catalog filters products by subcategory.

---

#### Task 6: Implement Admin Orders Management & Status Updates
- [ ] **Priority:** `P1 - HIGH`
- [ ] **Target Files:**
  - `src/services/orders/orders.service.ts`
  - `src/pages/admin/AdminOrdersView.tsx`
  - `src/pages/admin/AdminPage.tsx`
- [ ] **Backend Endpoints:**
  - `GET /api/admin/orders`
  - `PATCH /api/admin/orders/{id}/status?status={status}`
- [ ] **Exact Verified Backend Contract:**
  - **Method:** `PATCH` (not PUT)
  - **URL:** `/api/admin/orders/{id}/status?status=PROCESSING` (query parameter, not JSON body)
  - **Allowed Statuses:** `CONFIRMED`, `PROCESSING`, `SHIPPED`, `DELIVERED`, `CANCELLED`
- [ ] **Implementation Steps:**
  1. Add `fetchAdminOrders(page, size)` and `updateOrderStatus(orderId, status)` to `orders.service.ts`.
  2. Build `AdminOrdersView.tsx` with order status filter pills, customer delivery details, and status transition action buttons.
  3. Add "Orders" tab to `AdminPage.tsx`.
- [ ] **Acceptance Criteria:**
  - Admins can monitor customer orders and transition fulfillment statuses.

---

#### Task 7: Replace Combo Image URL with File Storage Upload
- [ ] **Priority:** `P1 - HIGH`
- [ ] **Target Files:**
  - `src/pages/admin/CombosOffersAdminView.tsx`
- [ ] **Implementation Steps:**
  1. Replace `<input type="url">` with a file dropzone.
  2. Upload selected image via `POST /api/uploads/image`.
  3. Set returned filename in form state before creating combo.
- [ ] **Acceptance Criteria:**
  - Combo images are persisted locally in `uploads/` matching Categories and Products.

---

### P2: Medium Priority (Admin Management & Enhancements)

#### Task 8: Implement Dynamic UPI QR Code Scan-and-Pay Modal
- [ ] **Priority:** `P2 - MEDIUM`
- [ ] **Target Files:**
  - `src/pages/checkout/CheckoutPage.tsx`
  - `src/components/checkout/UpiQrModal.tsx`
- [ ] **Backend Endpoint:** `POST /api/payments/razorpay/qr-code`
- [ ] **Verified Request:** `{ "shippingAddressId": 12, "couponCode": "MALKAZ10" }`
- [ ] **Response:** `{ "data": { "qrImageUrl": "...", "amount": 24999.00, "razorpayOrderId": "..." } }`
- [ ] **Acceptance Criteria:**
  - Customers can scan dynamic QR code on screen using GPay, PhonePe, or Paytm.

---

#### Task 9: Build Admin Coupon Management View
- [ ] **Priority:** `P2 - MEDIUM`
- [ ] **Target Files:**
  - `src/pages/admin/AdminCouponsView.tsx`
  - `src/pages/admin/AdminPage.tsx`
- [ ] **Backend Endpoints:** `GET, POST, PUT, DELETE /api/admin/coupons/**`
- [ ] **Acceptance Criteria:**
  - Admins can issue, edit, and deactivate promotional discount coupons.

---

#### Task 10: Build Admin Review Moderation View
- [ ] **Priority:** `P2 - MEDIUM`
- [ ] **Target Files:**
  - `src/pages/admin/AdminReviewsView.tsx`
  - `src/pages/admin/AdminPage.tsx`
- [ ] **Backend Endpoints:**
  - `GET /api/admin/reviews`
  - `PATCH /api/admin/reviews/{id}/approve`
  - `PATCH /api/admin/reviews/{id}/reject`
- [ ] **Acceptance Criteria:**
  - Admins can moderate customer product reviews.

---

#### Task 11: Connect Admin Dashboard Overview to Backend KPI API
- [ ] **Priority:** `P2 - MEDIUM`
- [ ] **Target Files:**
  - `src/pages/admin/AdminPage.tsx`
- [ ] **Backend Endpoint:** `GET /api/admin/dashboard`
- [ ] **Acceptance Criteria:**
  - Overview cards display store-wide statistics rather than counts from current page slice.

---

#### Task 12: Build Admin Payment Refund View
- [ ] **Priority:** `P2 - MEDIUM`
- [ ] **Target Files:**
  - `src/pages/admin/AdminPaymentsView.tsx`
  - `src/pages/admin/AdminPage.tsx`
- [ ] **Backend Endpoint:** `POST /api/admin/payments/{paymentId}/refund`
- [ ] **Request:** `{ "amount": 24999.00, "reason": "Customer cancellation" }`
- [ ] **Acceptance Criteria:**
  - Admins can trigger payment refunds through Razorpay.

---

### P3: Cleanup & Polishing

#### Task 13: Remove Deprecated Brand System (Dead Code 404s)
- [ ] **Priority:** `P3 - CLEANUP`
- [ ] **Target Files to Delete / Update:**
  - Delete `src/pages/shop/BrandsPage.tsx`
  - Delete `src/services/brands/brands.service.ts`
  - Remove route `/brands` in `AppRoutes.tsx`
  - Remove "Brands" links from `Navbar.tsx` and `Footer.tsx`
- [ ] **Acceptance Criteria:**
  - Zero console 404 errors from `/api/brands`.

---

#### Task 14: Align Shipping Calculation with Backend Logic
- [ ] **Priority:** `P3 - POLISHING`
- [ ] **Target Files:**
  - `src/pages/checkout/CheckoutPage.tsx`
- [ ] **Implementation Steps:**
  1. Synchronize shipping charge calculation with `CHECKOUT_SHIPPING` backend configuration.
  2. Prevent 1-rupee rounding mismatches.
- [ ] **Acceptance Criteria:**
  - Cart preview subtotal, checkout total, and backend order total match to the exact rupee.
