# MALKAZ — Backend Features & API Contract Specification (Authoritative)

**Audit & Reconciliation Date:** November 2026  
**Backend Framework:** Spring Boot 3.4.3 (Java 21), Spring Data JPA, Spring Security, Flyway, MySQL/TiDB  
**Authoritative Backend Path:** `c:\Users\HP\OneDrive\Desktop\MALKAZ\cosmeticszainab\Cosmetics-E-Commerce-Backend\artifacts\api-server\spring-app`  
**Servlet Context Path:** `/api` (configured via `server.servlet.context-path: /api` in `application.yml`)  
**Specification Status:** 100% Verified against Java Source Code  

---

## 1. Architecture & Counting Methodology

### 1.1 Counting Methodology Rules
1. **Functional Domains as Single Features:** Only distinct, end-to-end business capabilities are counted as backend features (e.g., "Subcategory Management" is 1 feature encompassing its 11 controller endpoints, JPA repository, and service methods).
2. **Exclusion of Structural Components:** Individual DTOs, entities, repositories, and helper classes are not counted as features.
3. **Exclusion of Infrastructure Probes:** Low-level infrastructure health probes (e.g., `HealthController` `/healthz`) are excluded from business feature counts.
4. **Exclusion of Obsolete Code:** Deprecated or deleted modules (e.g., legacy Brand entity/controller) are excluded from active backend feature counts.
5. **Denominator Definition:** The total number of active backend functional domains is **23**.

---

## 2. Verified Backend Functional Domains (23 Total)

```
┌────────────────────────────────────────────────────────────────────────┐
│                     MALKAZ BACKEND ARCHITECTURE                        │
├────────────────────────────────────────────────────────────────────────┤
│ 1. User Authentication & Stateless JWT    13. Promotional Coupons      │
│ 2. Google OAuth 2.0 Federation            14. Customer Checkout & COD  │
│ 3. Customer Profile Management            15. Razorpay Online Payment  │
│ 4. Customer Address Book                  16. Razorpay Dynamic UPI QR  │
│ 5. Product Catalog & Advanced Search      17. Razorpay Webhooks        │
│ 6. Product Relationship Graph (Similar)   18. Administrative Refunds   │
│ 7. Category Management                    19. Product Reviews & Ratings│
│ 8. Subcategory Management                 20. Referral & Rewards       │
│ 9. Predefined Combos & Bundles            21. Enterprise Audit Logs    │
│ 10. Custom Combo Builder (BYOC)           22. File Storage & Serving   │
│ 11. Shopping Cart Management              23. Admin Dashboard & KPIs   │
│ 12. Customer Wishlist Management                                       │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Comprehensive Domain Specifications

### Domain 1: User Authentication & Stateless JWT
- **Controller:** `com.cosmetics.ecommerce.controller.AuthController`
- **Service:** `com.cosmetics.ecommerce.service.AuthService`
- **Endpoints:**
  - `POST /api/auth/register` — Public. Request: `RegisterRequest(firstName, lastName, email, password, phoneNumber, referralCode)`. Response: `ApiResponse<AuthResponse>`.
  - `POST /api/auth/login` — Public. Request: `LoginRequest(email, password)`. Response: `ApiResponse<AuthResponse>`.
  - `POST /api/auth/refresh-token` — Public. Request: `RefreshTokenRequest(refreshToken)`. Response: `ApiResponse<AuthResponse>`.
  - `POST /api/auth/forgot-password` — Public. Request: `ForgotPasswordRequest(email)`. Response: `ApiResponse<Void>`.
  - `POST /api/auth/reset-password` — Public. Request: `ResetPasswordRequest(token, newPassword)`. Response: `ApiResponse<Void>`.
- **Security:** Public permitAll in `SecurityConfig`. Passwords hashed via BCrypt.

### Domain 2: Google OAuth 2.0 Identity Federation
- **Controller:** `com.cosmetics.ecommerce.controller.AuthController`
- **Endpoint:** `POST /api/auth/google` — Public.
- **Request DTO:** `GoogleAuthRequest(String credential)`
- **Response DTO:** `ApiResponse<AuthResponse>`
- **Logic:** Validates Google ID token using `com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier`. Provisions new customer user automatically if email is not found, or links existing account.

### Domain 3: Customer Profile Management
- **Controller:** `com.cosmetics.ecommerce.controller.AuthController`
- **Endpoints:**
  - `GET /api/auth/me` — Authenticated (`User`). Response: `ApiResponse<UserResponse>`.
  - `PUT /api/auth/me` — Authenticated (`User`). Request: `UpdateProfileRequest(firstName, lastName, phoneNumber)`. Response: `ApiResponse<UserResponse>`.

### Domain 4: Customer Address Book Management
- **Controller:** `com.cosmetics.ecommerce.controller.OrderController` (mapped at root under `/api/addresses`)
- **Service:** `com.cosmetics.ecommerce.service.AddressService`
- **Endpoints:**
  - `GET /api/addresses` — Authenticated. Response: `ApiResponse<List<AddressResponse>>`.
  - `POST /api/addresses` — Authenticated. Request: `AddressRequest(fullName, phoneNumber, addressLine1, addressLine2, city, state, postalCode, country, addressType, isDefault)`. Response: `ApiResponse<AddressResponse>`.
  - `PUT /api/addresses/{id}` — Authenticated. Request: `AddressRequest`. Response: `ApiResponse<AddressResponse>`.
  - `DELETE /api/addresses/{id}` — Authenticated. Response: `ApiResponse<Void>`.

### Domain 5: Product Catalog & Advanced Search
- **Controller:** `com.cosmetics.ecommerce.controller.ProductController` (mapped at `/products` and `/admin/products`)
- **Service:** `com.cosmetics.ecommerce.service.ProductService`
- **Public Endpoints:**
  - `GET /api/products` — Filter by `query`, `categoryId`, `subcategoryId`, `featured`, `minPrice`, `maxPrice`, pagination. Response: `ApiResponse<PageResponse<ProductResponse>>`.
  - `GET /api/products/{id}` — Response: `ApiResponse<ProductResponse>`.
  - `GET /api/products/slug/{slug}` — Response: `ApiResponse<ProductResponse>`.
  - `GET /api/products/categories/{categoryId}/products` — Response: `ApiResponse<PageResponse<ProductResponse>>`.
  - `GET /api/products/subcategories/{subcategoryId}/products` — Response: `ApiResponse<PageResponse<ProductResponse>>`.
- **Admin Endpoints:**
  - `POST /api/admin/products` — PreAuthorize `ADMIN`. JSON or Multipart. Request: `ProductRequest`.
  - `PUT /api/admin/products/{id}` — PreAuthorize `ADMIN`. JSON or Multipart.
  - `DELETE /api/admin/products/{id}` — PreAuthorize `ADMIN`.
  - `PATCH /api/admin/products/{id}/stock` — PreAuthorize `ADMIN, STAFF`. Param: `quantity`.

### Domain 6: Product Relationship Graph (Similar Products / "Complete the Look")
- **Controller:** `com.cosmetics.ecommerce.controller.ProductController`
- **Endpoints:**
  - `GET /api/products/{id}/similar` — Public. Query: `type`, `limit`. Response: `ApiResponse<PageResponse<SimilarProductResponse>>`.
  - `GET /api/products/slug/{slug}/similar` — Public.
  - `GET /api/admin/products/{id}/relationships` — PreAuthorize `ADMIN, STAFF`.
  - `POST /api/admin/products/{id}/relationships` — PreAuthorize `ADMIN, STAFF`. Request: `ProductRelationshipRequest`.
  - `DELETE /api/admin/products/{id}/relationships/{relatedId}` — PreAuthorize `ADMIN, STAFF`.

### Domain 7: Category Management
- **Controller:** `com.cosmetics.ecommerce.controller.CatalogController`
- **Service:** `com.cosmetics.ecommerce.service.CatalogService`
- **Endpoints:**
  - `GET /api/categories` — Public. Response: `ApiResponse<List<CategoryResponse>>`.
  - `GET /api/categoriesASC` — Public. Alphabetical sorting.
  - `GET /api/categories/{id}` — Public.
  - `POST /api/admin/categories` — PreAuthorize `ADMIN`. JSON or Multipart (`CategoryRequest`, `image`).
  - `PUT /api/admin/categories/{id}` — PreAuthorize `ADMIN`. JSON or Multipart.
  - `POST /api/admin/categories/{id}/image` — PreAuthorize `ADMIN`. Multipart image update.
  - `POST /api/admin/categories/upload-image` — PreAuthorize `ADMIN`. Standalone image upload.
  - `DELETE /api/admin/categories/{id}` — PreAuthorize `ADMIN`.

### Domain 8: Subcategory Management (Parent-Child Jewellery Hierarchy)
- **Controllers:**
  - `com.cosmetics.ecommerce.controller.SubcategoryController` (Public)
  - `com.cosmetics.ecommerce.controller.AdminSubcategoryController` (Admin)
- **Service:** `com.cosmetics.ecommerce.service.SubcategoryService`
- **Public Endpoints:**
  - `GET /api/subcategories` — Query: `categoryId` (optional). Response: `ApiResponse<List<SubcategoryResponse>>`.
  - `GET /api/subcategories/{id}` — Response: `ApiResponse<SubcategoryResponse>`.
  - `GET /api/subcategories/slug/{slug}` — Response: `ApiResponse<SubcategoryResponse>`.
  - `GET /api/categories/{categoryId}/subcategories` — Response: `ApiResponse<List<SubcategoryResponse>>`.
- **Admin Endpoints:**
  - `GET /api/admin/subcategories` — PreAuthorize `ADMIN, STAFF`. Query: `query`, `categoryId`, `active`, Pageable. Response: `ApiResponse<PageResponse<SubcategoryResponse>>`.
  - `GET /api/admin/subcategories/{id}` — PreAuthorize `ADMIN, STAFF`.
  - `POST /api/admin/subcategories` — PreAuthorize `ADMIN, STAFF`. JSON or Multipart. Request: `SubcategoryRequest(name, categoryId, description, imageUrl, active)`.
  - `PUT /api/admin/subcategories/{id}` — PreAuthorize `ADMIN, STAFF`. JSON or Multipart.
  - `PATCH /api/admin/subcategories/{id}/status` — PreAuthorize `ADMIN, STAFF`. Param or Body: `active`.
  - `DELETE /api/admin/subcategories/{id}` — PreAuthorize `ADMIN, STAFF`.
  - `POST /api/admin/subcategories/upload-image` — PreAuthorize `ADMIN, STAFF`. Multipart image upload.

### Domain 9: Predefined Combos & Bundles
- **Controllers:**
  - `com.cosmetics.ecommerce.controller.ComboController` (Public & Customer Cart)
  - `com.cosmetics.ecommerce.controller.AdminComboController` (Admin CRUD & Rules)
- **Service:** `com.cosmetics.ecommerce.service.ComboService`
- **Endpoints:**
  - `GET /api/combos` — Public. Paginated predefined combos. Response: `ApiResponse<PageResponse<ComboResponse>>`.
  - `GET /api/combos/featured` — Public. Featured bundles.
  - `GET /api/combos/id/{id}` & `GET /api/combos/{slug}` — Public.
  - `POST /api/combos/{id}/cart` — Authenticated. Request: `AddPredefinedComboToCartRequest(quantity)`.
  - `GET /api/admin/combos` — PreAuthorize `ADMIN, STAFF`.
  - `POST /api/admin/combos` — PreAuthorize `ADMIN, STAFF`. JSON or Multipart. Request: `ComboRequest`.
  - `PUT /api/admin/combos/{id}` — PreAuthorize `ADMIN, STAFF`. JSON or Multipart.
  - `PATCH /api/admin/combos/{id}/status` — PreAuthorize `ADMIN, STAFF`.
  - `DELETE /api/admin/combos/{id}` — PreAuthorize `ADMIN, STAFF`.
  - `POST /api/admin/combos/upload-image` — Standalone multipart image upload.
  - `GET, POST, PUT, DELETE /api/admin/combos/rules/**` — Tiered discount rules.

### Domain 10: Custom Combo Builder ("Build Your Own Combo" / BYOC)
- **Controller:** `com.cosmetics.ecommerce.controller.ComboController`
- **Endpoints:**
  - `GET /api/combos/products` (and `/api/combos/custom/products`) — Public. Returns products eligible for custom combos.
  - `POST /api/combos/custom/preview` — Public. Request: `CustomComboPreviewRequest(items)`. Computes tiered combo discount and savings.
  - `POST /api/combos/custom/cart` — Authenticated. Request: `AddCustomComboToCartRequest(items)`. Atomically adds custom bundle as a grouped set of items in cart.

### Domain 11: Shopping Cart Management
- **Controllers:**
  - `com.cosmetics.ecommerce.controller.CartController`
  - `com.cosmetics.ecommerce.controller.ComboController` (`/cart/groups/{id}`)
- **Service:** `com.cosmetics.ecommerce.service.CartService`
- **Endpoints:**
  - `GET /api/cart` — Authenticated. Response: `ApiResponse<CartResponse>`.
  - `POST /api/cart/items` — Authenticated. Request: `CartItemRequest(productId, quantity)`.
  - `PUT /api/cart/items/{itemId}` — Authenticated. Param: `quantity`.
  - `DELETE /api/cart/items/{itemId}` — Authenticated.
  - `DELETE /api/cart/clear` — Authenticated.
  - `DELETE /api/combos/cart/groups/{comboGroupId}` — Authenticated. Atomically removes all items sharing `comboGroupId`.

### Domain 12: Customer Wishlist Management
- **Controller:** `com.cosmetics.ecommerce.controller.WishlistController`
- **Service:** `com.cosmetics.ecommerce.service.WishlistService`
- **Endpoints:**
  - `GET /api/wishlist` — Authenticated. Response: `ApiResponse<List<ProductResponse>>`.
  - `POST /api/wishlist/{productId}` — Authenticated.
  - `DELETE /api/wishlist/{productId}` — Authenticated.

### Domain 13: Promotional Coupons & Discount Engine
- **Controller:** `com.cosmetics.ecommerce.controller.CouponController`
- **Service:** `com.cosmetics.ecommerce.service.CouponService`
- **Endpoints:**
  - `POST /api/coupons/apply` — Public/Authenticated. Request: `ApplyCouponRequest(code, orderAmount)`. Response: `ApiResponse<CouponResponse>`.
  - `GET /api/admin/coupons` — PreAuthorize `ADMIN`.
  - `POST /api/admin/coupons` — PreAuthorize `ADMIN`. Request: `CouponRequest`.
  - `PUT /api/admin/coupons/{id}` — PreAuthorize `ADMIN`. Request: `CouponRequest`.
  - `DELETE /api/admin/coupons/{id}` — PreAuthorize `ADMIN`.

### Domain 14: Customer Checkout & Order Placement (Cash on Delivery)
- **Controller:** `com.cosmetics.ecommerce.controller.OrderController`
- **Service:** `com.cosmetics.ecommerce.service.OrderService`
- **Endpoints:**
  - `POST /api/orders` — Authenticated. Request: `OrderRequest(shippingAddressId, paymentMethod, couponCode)`. Response: `ApiResponse<OrderResponse>`.
  - `GET /api/orders` — Authenticated. Customer's paginated order history.
  - `GET /api/orders/{id}` — Authenticated (IDOR protected).
  - `GET /api/orders/number/{orderNumber}` — Authenticated (IDOR protected).
  - `POST /api/orders/{id}/cancel` — Authenticated.

### Domain 15: Razorpay Online Payment Integration
- **Controller:** `com.cosmetics.ecommerce.controller.RazorpayPaymentController`
- **Service:** `com.cosmetics.ecommerce.service.OrderService`, `com.cosmetics.ecommerce.payment.RazorpayService`
- **Endpoints:**
  - `POST /api/payments/razorpay/create-order` — Authenticated.
    - **Request:** `RazorpayOrderRequest(Long shippingAddressId, PaymentMethod paymentMethod, String couponCode)`
    - **Response:** `RazorpayOrderResponse(orderId, orderNumber, razorpayOrderId, amount, amountInPaise, currency, razorpayKeyId, customerName, customerEmail, customerPhone, companyName, themeColor, paymentMethod)`
  - `POST /api/payments/razorpay/verify` — Authenticated.
    - **Request:** `RazorpayVerifyRequest(Long orderId, String razorpayOrderId, String razorpayPaymentId, String razorpaySignature)`
    - **Response:** `RazorpayVerifyResponse(orderId, orderNumber, paymentStatus, transactionId, razorpayPaymentId, amount, message)`
  - `GET /api/payments/{orderId}` — Authenticated (IDOR protected). Returns `PaymentStatusResponse`.

### Domain 16: Razorpay Dynamic UPI QR Code Generation
- **Controller:** `com.cosmetics.ecommerce.controller.RazorpayPaymentController`
- **Endpoint:** `POST /api/payments/razorpay/qr-code` — Authenticated.
  - **Request:** `RazorpayQrRequest(Long shippingAddressId, String couponCode)`
  - **Response:** `RazorpayQrResponse(orderId, orderNumber, razorpayOrderId, qrCodeId, qrImageUrl, paymentUrl, amount, amountInPaise, currency, status)`

### Domain 17: Razorpay Webhook Processing & Idempotency
- **Controller:** `com.cosmetics.ecommerce.controller.RazorpayWebhookController`
- **Service:** `com.cosmetics.ecommerce.payment.RazorpayWebhookService`
- **Endpoint:** `POST /api/payments/razorpay/webhook` — Public. Header: `X-Razorpay-Signature`.
- **Events Handled:** `payment.captured`, `order.paid`, `payment.failed`, `refund.processed`.
- **Deduplication:** Enforced via `PaymentWebhookEventRepository` checking `existsByRazorpayEventId(eventId)`.
- **Card Rejection:** Explicitly rejects webhook events if payment method contains "card".

### Domain 18: Administrative Payment Refunds
- **Controller:** `com.cosmetics.ecommerce.controller.AdminPaymentController`
- **Endpoint:** `POST /api/admin/payments/{paymentId}/refund` — PreAuthorize `ADMIN`.
  - **Request:** `RefundRequest(BigDecimal amount, String reason)`
  - **Response:** `RefundResponse(refundId, paymentId, orderId, orderNumber, refundAmount, refundStatus, paymentStatus, reason, refundedAt)`

### Domain 19: Product Reviews & Star Ratings
- **Controller:** `com.cosmetics.ecommerce.controller.ReviewController`
- **Service:** `com.cosmetics.ecommerce.service.ReviewService`
- **Endpoints:**
  - `GET /api/products/{productId}/reviews` — Public.
  - `POST /api/products/{productId}/reviews` — Authenticated. Request: `ReviewRequest(rating, comment, title)`.
  - `PUT /api/reviews/{reviewId}` — Authenticated.
  - `DELETE /api/reviews/{reviewId}` — Authenticated.
  - `GET /api/admin/reviews` — PreAuthorize `ADMIN`.
  - `PATCH /api/admin/reviews/{id}/approve` — PreAuthorize `ADMIN`.
  - `PATCH /api/admin/reviews/{id}/reject` — PreAuthorize `ADMIN`.

### Domain 20: Customer Referral & Rewards Program
- **Controller:** `com.cosmetics.ecommerce.controller.ReferralController`
- **Service:** `com.cosmetics.ecommerce.service.ReferralService`
- **Endpoints:**
  - `GET /api/referrals/me` — Authenticated. Response: `ApiResponse<ReferralSummaryResponse>`.
  - `GET /api/referrals/validate` — Public. Query: `code`. Response: `ApiResponse<ValidateReferralResponse>`.

### Domain 21: Enterprise Audit Logging & Compliance
- **Controller:** `com.cosmetics.ecommerce.controller.AuditLogController`
- **Service:** `com.cosmetics.ecommerce.service.AuditLogService`
- **Endpoints:**
  - `GET /api/admin/audit-logs` — PreAuthorize `ADMIN`. Query filters: `action`, `result`, `entityType`, `pageable`.
  - `GET /api/admin/audit-logs/{id}` — PreAuthorize `ADMIN`.

### Domain 22: Image File Storage & Serving System
- **Controller:** `com.cosmetics.ecommerce.controller.FileUploadController`
- **Service:** `com.cosmetics.ecommerce.service.FileStorageService`
- **Endpoints:**
  - `GET /api/uploads/{fileName:.+}` — Public. MIME type probe with cache control header `public, max-age=86400`.
  - `POST /api/uploads/image` — PreAuthorize `ADMIN, STAFF`. Multipart upload (`file` or `image`). Validates MIME and 5MB size limit.

### Domain 23: Admin Dashboard KPIs & Inventory Operations
- **Controller:** `com.cosmetics.ecommerce.controller.AdminController`
- **Endpoints:**
  - `GET /api/admin/dashboard` — PreAuthorize `ADMIN`. Returns revenue, order counts, product counts, customer metrics.
  - `GET /api/admin/orders` — PreAuthorize `ADMIN, STAFF`. Paginated orders.
  - `PATCH /api/admin/orders/{id}/status` — PreAuthorize `ADMIN, STAFF`. Query: `status` (`OrderStatus`).
  - `GET /api/admin/inventory` — PreAuthorize `ADMIN, STAFF`.
  - `GET /api/admin/inventory/low-stock` — PreAuthorize `ADMIN, STAFF`.
  - `GET /api/admin/inventory/out-of-stock` — PreAuthorize `ADMIN, STAFF`.
  - `GET /api/admin/users` — PreAuthorize `ADMIN`.
  - `PATCH /api/admin/users/{id}` — PreAuthorize `ADMIN`. Request: `AdminUserUpdateRequest`.
