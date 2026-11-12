# MALKAZ — Frontend & Backend Feature Integration Audit & Verification Report

**Audit Date:** November 2026  
**Auditor:** Senior Java Spring Boot Architect & Application Security Auditor  
**Audit Type:** Strict Read-Only Source Code Audit & Contract Reconciliation  
**Audit Scope:** 100% verification of actual Java Spring Boot backend and React/TypeScript frontend source code  

---

## 1. Authoritative Project Directories & Architecture

### 1.1 Project Directory Identification
- **Authoritative Backend Directory:**  
  `c:\Users\HP\OneDrive\Desktop\MALKAZ\cosmeticszainab\Cosmetics-E-Commerce-Backend\artifacts\api-server\spring-app`  
  *Proof:* Contains the authoritative Java 21 Spring Boot 3.4.3 project with `pom.xml`, 20 `@RestController` classes, 23 JPA entities, and Flyway database migrations.
- **Authoritative Frontend Directory:**  
  `c:\Users\HP\OneDrive\Desktop\MALKAZ\masterim\MyBeautify-cosmetics-store\artifacts\mybeautify-store`  
  *Proof:* Contains the active React 18, Vite, and Tailwind CSS application with `src/routes/AppRoutes.tsx`, pages, and services.
- **Legacy & Outdated Workspace Copies Identified:**
  1. `.../cosmeticszainab/Cosmetics-E-Commerce-Backend/artifacts/api-server/src`: Legacy Node.js/Express server (deprecated; obsolete).
  2. `.../cosmeticszainab/Cosmetics-E-Commerce-Backend/artifacts/mockup-sandbox`: Replit mockup sandbox (deprecated; obsolete).
  3. `.../masterim/MyBeautify-cosmetics-store/artifacts/mockup-sandbox`: Legacy frontend mockup sandbox (deprecated; obsolete).
- **Frontend API Client Routing:**  
  In [`client.ts`](file:///c:/Users/HP/OneDrive/Desktop/MALKAZ/masterim/MyBeautify-cosmetics-store/artifacts/mybeautify-store/src/services/api/client.ts), `DEFAULT_API_BASE_URL` is set to `https://api.fashionmalkaz.com/api` (overridable via `VITE_API_URL` or `VITE_API_BASE_URL`). In local development, `vite.config.ts` proxies `/api` to the backend running at port 8080 with `server.servlet.context-path: /api`.

---

## 2. Reconciled Feature Counts & Integration Statistics

### 2.1 Counting Methodology
- **Backend Functional Domains (Denominator = 23):** Only end-to-end business capabilities are counted once. Individual endpoints, DTOs, entities, and infrastructure probes (`/healthz`) are excluded.
- **Frontend Modules / Views (Total = 22):** Distinct customer-facing pages, admin tabs, and views.
- **Mathematical Precision:** Percentages are calculated strictly against the 23 active backend domains.

### 2.2 Integration Statistics Summary

| Category | Count | Percentage of Backend (Denominator = 23) | Description |
| :--- | :--- | :--- | :--- |
| **Fully Integrated** | **12** | **52.2%** | Endpoints and UI contracts match; operational end-to-end |
| **Partially Integrated** | **6** | **26.1%** | Feature partially works; missing admin views or client calculations |
| **Broken Integrations** | **2** | **8.7%** | Both sides exist but fail due to contract/endpoint mismatch |
| **Not Integrated** | **3** | **13.0%** | Implemented on backend; zero implementation in frontend |
| **Total Backend Domains** | **23** | **100.0%** | Complete active backend architecture |
| **Frontend-Only (Dead Code)** | **1** | — | Brands module (`BrandsPage.tsx` calling deleted `/api/brands`) |
| **Critical Blocker** | **1** | — | Online Checkout fails (sends `MOCK`, backend throws 400) |

---

## 3. Feature-by-Feature Integration Matrix

| # | Backend Functional Domain | Backend Controller & Scope | Frontend Service & Component | Status | Detailed Finding |
|---|---|---|---|---|---|
| 1 | **User Auth & Stateless JWT** | `AuthController.java` | `auth.service.ts` / `AuthPage.tsx` | **Fully Integrated** | Login, register, token refresh, and password reset operational. |
| 2 | **Google OAuth Federation** | `AuthController.java` | `auth.service.ts` / `GoogleLoginButton.tsx` | **Fully Integrated** | Post to `/api/auth/google` with ID token; auto-links account. |
| 3 | **Customer Profile** | `AuthController.java` | `auth.service.ts` / `CustomerDashboard.tsx` | **Fully Integrated** | `/api/auth/me` fetch and update profile operational. |
| 4 | **Customer Address Book** | `OrderController.java` (`/addresses`) | `addresses.service.ts` / `AddressModal.tsx` | **Fully Integrated** | Full address CRUD with default selection operational. |
| 5 | **Product Catalog & Search** | `ProductController.java` | `products.service.ts` / `CustomerDashboard.tsx` | **Fully Integrated** | Catalog browsing, multi-filter, search, and pagination operational. |
| 6 | **Product Relationship Graph** | `ProductController.java` | `products.service.ts` / `ProductDetailPage.tsx` | **Fully Integrated** | Displays "Complete the Look" recommendations; admin view active. |
| 7 | **Category Management** | `CatalogController.java` | `categories.service.ts` / `AdminPage.tsx` | **Fully Integrated** | Admin category CRUD with multipart image upload operational. |
| 8 | **Subcategory Management** | `SubcategoryController.java`, `AdminSubcategoryController.java` | *None* | **Not Integrated** | **11 backend endpoints unimplemented.** Frontend has zero subcategory types, services, or UI views. |
| 9 | **Predefined Combos & Bundles** | `ComboController.java`, `AdminComboController.java` | `combos.service.ts` / `CombosOffersPage.tsx` | **Partially Integrated** | Customer view and cart addition work. Admin view uses text URL input rather than `FileStorageService`. |
| 10 | **Custom Combo Builder (BYOC)** | `ComboController.java` | `combos.service.ts` / `CombosOffersPage.tsx` | **Fully Integrated** | Dynamic slot selection, real-time preview, tiered bundle discounts operational. |
| 11 | **Shopping Cart Management** | `CartController.java`, `ComboController.java` | `cart.service.ts`, `combos.service.ts` | **Broken Integration** | Standard items work, but **combo removal calls `/api/cart/combos/{id}` (404)** instead of `/api/combos/cart/groups/{id}`. |
| 12 | **Customer Wishlist** | `WishlistController.java` | `wishlist.service.ts` / `CustomerDashboard.tsx` | **Fully Integrated** | Wishlist add, remove, and move to cart operational. |
| 13 | **Promotional Coupons Engine** | `CouponController.java` | `coupons.service.ts` / `CheckoutPage.tsx` | **Partially Integrated** | Customer coupon validation works; **Admin coupon CRUD is completely missing from AdminPage.tsx**. |
| 14 | **Customer Checkout (COD)** | `OrderController.java` | `orders.service.ts` / `CheckoutPage.tsx` | **Fully Integrated** | Cash on Delivery order placement, stock deduction, and order history operational. |
| 15 | **Razorpay Online Payment** | `RazorpayPaymentController.java` | `CheckoutPage.tsx` | **Broken Integration (Blocker)** | **Frontend sends `paymentMethod: 'MOCK'`, never loads Razorpay SDK**, and never calls `/api/payments/razorpay/*`. |
| 16 | **Dynamic UPI QR Code Payment** | `RazorpayPaymentController.java` | *None* | **Not Integrated** | `POST /api/payments/razorpay/qr-code` exists in backend; zero frontend UI. |
| 17 | **Razorpay Webhooks & Idempotency**| `RazorpayWebhookController.java` | *Server-Side Only* | **Partially Integrated** | Webhook verification, deduplication, and stock deduction work in backend, but whole lifecycle blocked by frontend checkout. |
| 18 | **Administrative Refunds** | `AdminPaymentController.java` | *None* | **Not Integrated** | `POST /api/admin/payments/{id}/refund` exists in backend; no UI in `AdminPage.tsx`. |
| 19 | **Product Reviews & Ratings** | `ReviewController.java` | `ProductDetailPage.tsx` | **Partially Integrated** | Customer review submission works; **Admin review moderation/approval is missing from AdminPage.tsx**. |
| 20 | **Referral & Rewards Program** | `ReferralController.java` | `referral.service.ts` / `ShareReferralPage.tsx` | **Fully Integrated** | Referral code generation, validation, and auto-discounts operational. |
| 21 | **Enterprise Audit Logging** | `AuditLogController.java` | `audit.service.ts` / `AuditLogsView.tsx` | **Fully Integrated** | Event timeline, actor/action filtering, and audit trail operational. |
| 22 | **Image File Storage & Serving** | `FileUploadController.java` | `uploads.service.ts` / `AdminPage.tsx` | **Partially Integrated** | Works for Categories and Products; missing for Combos & Offers admin. |
| 23 | **Admin Dashboard & KPIs** | `AdminController.java` | `AdminPage.tsx` | **Partially Integrated** | Overview stat cards present in UI, but **derive counts client-side from the current page of products** instead of calling `/api/admin/dashboard`. |
| — | **Brand Management (Dead Code)** | *None (Deleted in backend)* | `brands.service.ts` / `BrandsPage.tsx` | **Frontend-Only (Broken)** | Frontend routes `/brands` and service call deleted `/api/brands` (returns HTTP 404). |

---

## 4. Complete Backend API Endpoint Cross-Check Table

*All endpoints are prefixed with the servlet context path `/api`.*

| HTTP Method | Actual Endpoint URL | Controller Class | Java Method | Request DTO / Payload | Response DTO | Auth / Security | Frontend Caller | Status |
|---|---|---|---|---|---|---|---|---|
| **POST** | `/api/auth/register` | `AuthController` | `register` | `RegisterRequest` | `ApiResponse<AuthResponse>` | Public | `auth.service.ts` (`register`) | Full |
| **POST** | `/api/auth/login` | `AuthController` | `login` | `LoginRequest` | `ApiResponse<AuthResponse>` | Public | `auth.service.ts` (`login`) | Full |
| **POST** | `/api/auth/google` | `AuthController` | `googleLogin` | `GoogleAuthRequest` | `ApiResponse<AuthResponse>` | Public | `auth.service.ts` (`googleLogin`) | Full |
| **POST** | `/api/auth/refresh-token` | `AuthController` | `refresh` | `RefreshTokenRequest` | `ApiResponse<AuthResponse>` | Public | `client.ts` (`attemptTokenRefresh`) | Full |
| **GET** | `/api/auth/me` | `AuthController` | `me` | *None* | `ApiResponse<UserResponse>` | Authenticated | `auth.service.ts` (`fetchCurrentUser`) | Full |
| **PUT** | `/api/auth/me` | `AuthController` | `update` | `UpdateProfileRequest` | `ApiResponse<UserResponse>` | Authenticated | `auth.service.ts` (`updateUserProfile`) | Full |
| **POST** | `/api/auth/forgot-password` | `AuthController` | `forgot` | `ForgotPasswordRequest` | `ApiResponse<Void>` | Public | `auth.service.ts` (`forgotPassword`) | Full |
| **POST** | `/api/auth/reset-password` | `AuthController` | `reset` | `ResetPasswordRequest` | `ApiResponse<Void>` | Public | `auth.service.ts` (`resetPassword`) | Full |
| **GET** | `/api/addresses` | `OrderController` | `addresses` | *None* | `ApiResponse<List<AddressResponse>>` | Authenticated | `addresses.service.ts` (`fetchAddresses`) | Full |
| **POST** | `/api/addresses` | `OrderController` | `addAddress` | `AddressRequest` | `ApiResponse<AddressResponse>` | Authenticated | `addresses.service.ts` (`createAddress`) | Full |
| **PUT** | `/api/addresses/{id}` | `OrderController` | `updateAddress` | `AddressRequest` | `ApiResponse<AddressResponse>` | Authenticated | `addresses.service.ts` (`updateAddress`) | Full |
| **DELETE** | `/api/addresses/{id}` | `OrderController` | `deleteAddress` | *None* | `ApiResponse<Void>` | Authenticated | `addresses.service.ts` (`deleteAddress`) | Full |
| **GET** | `/api/products` | `ProductController` | `search` | Query Params | `ApiResponse<PageResponse<ProductResponse>>` | Public | `products.service.ts` (`fetchProducts`) | Full |
| **GET** | `/api/products/{id}` | `ProductController` | `get` | *None* | `ApiResponse<ProductResponse>` | Public | `products.service.ts` (`fetchProductById`) | Full |
| **GET** | `/api/products/slug/{slug}` | `ProductController` | `slug` | *None* | `ApiResponse<ProductResponse>` | Public | `products.service.ts` (`fetchProductBySlug`) | Full |
| **GET** | `/api/products/{id}/similar` | `ProductController` | `getSimilar` | Query Params | `ApiResponse<PageResponse<SimilarProductResponse>>` | Public | `products.service.ts` (`fetchSimilarProducts`) | Full |
| **POST** | `/api/admin/products` | `ProductController` | `create` | `ProductRequest` (or Multipart) | `ApiResponse<ProductResponse>` | PreAuthorize `ADMIN` | `AdminPage.tsx` (`CrudPanel`) | Full |
| **PUT** | `/api/admin/products/{id}` | `ProductController` | `update` | `ProductRequest` (or Multipart) | `ApiResponse<ProductResponse>` | PreAuthorize `ADMIN` | `AdminPage.tsx` (`CrudPanel`) | Full |
| **DELETE** | `/api/admin/products/{id}` | `ProductController` | `delete` | *None* | `ApiResponse<Void>` | PreAuthorize `ADMIN` | `AdminPage.tsx` (`CrudPanel`) | Full |
| **PATCH** | `/api/admin/products/{id}/stock` | `ProductController` | `updateStock` | Query Param `quantity` | `ApiResponse<ProductResponse>` | PreAuthorize `ADMIN, STAFF` | *None* | Missing |
| **GET** | `/api/categories` | `CatalogController` | `categories` | *None* | `ApiResponse<List<CategoryResponse>>` | Public | `categories.service.ts` (`fetchCategories`) | Full |
| **POST** | `/api/admin/categories` | `CatalogController` | `createCategory` | `CategoryRequest` (or Multipart) | `ApiResponse<CategoryResponse>` | PreAuthorize `ADMIN` | `AdminPage.tsx` (`CrudPanel`) | Full |
| **PUT** | `/api/admin/categories/{id}` | `CatalogController` | `updateCategory` | `CategoryRequest` (or Multipart) | `ApiResponse<CategoryResponse>` | PreAuthorize `ADMIN` | `AdminPage.tsx` (`CrudPanel`) | Full |
| **DELETE** | `/api/admin/categories/{id}` | `CatalogController` | `deleteCategory` | *None* | `ApiResponse<Void>` | PreAuthorize `ADMIN` | `AdminPage.tsx` (`CrudPanel`) | Full |
| **GET** | `/api/subcategories` | `SubcategoryController` | `list` | Query Param `categoryId` | `ApiResponse<List<SubcategoryResponse>>` | Public | *None* | Missing |
| **GET** | `/api/subcategories/{id}` | `SubcategoryController` | `getById` | *None* | `ApiResponse<SubcategoryResponse>` | Public | *None* | Missing |
| **GET** | `/api/subcategories/slug/{slug}` | `SubcategoryController` | `getBySlug` | *None* | `ApiResponse<SubcategoryResponse>` | Public | *None* | Missing |
| **GET** | `/api/admin/subcategories` | `AdminSubcategoryController` | `list` | Query Params, Pageable | `ApiResponse<PageResponse<SubcategoryResponse>>` | PreAuthorize `ADMIN, STAFF` | *None* | Missing |
| **POST** | `/api/admin/subcategories` | `AdminSubcategoryController` | `create` | `SubcategoryRequest` (or Multipart) | `ApiResponse<SubcategoryResponse>` | PreAuthorize `ADMIN, STAFF` | *None* | Missing |
| **PUT** | `/api/admin/subcategories/{id}` | `AdminSubcategoryController` | `update` | `SubcategoryRequest` (or Multipart) | `ApiResponse<SubcategoryResponse>` | PreAuthorize `ADMIN, STAFF` | *None* | Missing |
| **PATCH** | `/api/admin/subcategories/{id}/status` | `AdminSubcategoryController` | `updateStatus` | Param / `SubcategoryStatusUpdateRequest` | `ApiResponse<SubcategoryResponse>` | PreAuthorize `ADMIN, STAFF` | *None* | Missing |
| **DELETE** | `/api/admin/subcategories/{id}` | `AdminSubcategoryController` | `delete` | *None* | `ApiResponse<Void>` | PreAuthorize `ADMIN, STAFF` | *None* | Missing |
| **GET** | `/api/combos` | `ComboController` | `list` | Pageable | `ApiResponse<PageResponse<ComboResponse>>` | Public | `combos.service.ts` (`fetchCombos`) | Full |
| **GET** | `/api/combos/products` | `ComboController` | `eligibleProducts` | Pageable | `ApiResponse<PageResponse<ProductResponse>>` | Public | `combos.service.ts` (`fetchCustomComboEligibleProducts`) | Full |
| **POST** | `/api/combos/custom/preview` | `ComboController` | `previewCustomCombo` | `CustomComboPreviewRequest` | `ApiResponse<CustomComboPreviewResponse>` | Public | `combos.service.ts` (`previewCustomCombo`) | Full |
| **POST** | `/api/combos/custom/cart` | `ComboController` | `addCustomToCart` | `AddCustomComboToCartRequest` | `ApiResponse<CartResponse>` | Authenticated | `combos.service.ts` (`addCustomComboToCart`) | Full |
| **DELETE** | `/api/combos/cart/groups/{comboGroupId}` | `ComboController` | `removeComboGroup` | *None* | `ApiResponse<CartResponse>` | Authenticated | `combos.service.ts` line 71 calls `/api/cart/combos/{id}` | **Broken (404)** |
| **POST** | `/api/admin/combos` | `AdminComboController` | `create` | `ComboRequest` (or Multipart) | `ApiResponse<ComboResponse>` | PreAuthorize `ADMIN, STAFF` | `combos.service.ts` (`createAdminCombo`) | Partial |
| **GET** | `/api/cart` | `CartController` | `get` | *None* | `ApiResponse<CartResponse>` | Authenticated | `cart.service.ts` (`fetchCart`) | Full |
| **POST** | `/api/cart/items` | `CartController` | `add` | `CartItemRequest` | `ApiResponse<CartResponse>` | Authenticated | `cart.service.ts` (`addToCart`) | Full |
| **PUT** | `/api/cart/items/{itemId}` | `CartController` | `update` | Query Param `quantity` | `ApiResponse<CartResponse>` | Authenticated | `cart.service.ts` (`updateCartQuantity`) | Full |
| **DELETE** | `/api/cart/items/{itemId}` | `CartController` | `remove` | *None* | `ApiResponse<Void>` | Authenticated | `cart.service.ts` (`removeFromCart`) | Full |
| **DELETE** | `/api/cart/clear` | `CartController` | `clear` | *None* | `ApiResponse<Void>` | Authenticated | `cart.service.ts` (`clearCart`) | Full |
| **GET** | `/api/wishlist` | `WishlistController` | `list` | *None* | `ApiResponse<List<ProductResponse>>` | Authenticated | `wishlist.service.ts` (`fetchWishlist`) | Full |
| **POST** | `/api/wishlist/{productId}` | `WishlistController` | `add` | *None* | `ApiResponse<List<ProductResponse>>` | Authenticated | `wishlist.service.ts` (`addToWishlist`) | Full |
| **DELETE** | `/api/wishlist/{productId}` | `WishlistController` | `remove` | *None* | `ApiResponse<Void>` | Authenticated | `wishlist.service.ts` (`removeFromWishlist`) | Full |
| **POST** | `/api/coupons/apply` | `CouponController` | `apply` | `ApplyCouponRequest` | `ApiResponse<CouponResponse>` | Public | `coupons.service.ts` (`applyCoupon`) | Full |
| **GET** | `/api/admin/coupons` | `CouponController` | `list` | Pageable | `ApiResponse<PageResponse<CouponResponse>>` | PreAuthorize `ADMIN` | *None* | Missing |
| **POST** | `/api/admin/coupons` | `CouponController` | `create` | `CouponRequest` | `ApiResponse<CouponResponse>` | PreAuthorize `ADMIN` | *None* | Missing |
| **POST** | `/api/orders` | `OrderController` | `checkout` | `OrderRequest` | `ApiResponse<OrderResponse>` | Authenticated | `orders.service.ts` (`createOrder`) | Full (COD) |
| **GET** | `/api/orders` | `OrderController` | `list` | Pageable | `ApiResponse<PageResponse<OrderResponse>>` | Authenticated | `orders.service.ts` (`fetchOrders`) | Full |
| **POST** | `/api/orders/{id}/cancel` | `OrderController` | `cancel` | *None* | `ApiResponse<OrderResponse>` | Authenticated | `orders.service.ts` (`cancelOrder`) | Full |
| **POST** | `/api/payments/razorpay/create-order` | `RazorpayPaymentController` | `createRazorpayOrder` | `RazorpayOrderRequest` | `ApiResponse<RazorpayOrderResponse>` | Authenticated | *None* (Frontend sends MOCK to `/orders`) | **Broken (Blocker)** |
| **POST** | `/api/payments/razorpay/verify` | `RazorpayPaymentController` | `verifyPayment` | `RazorpayVerifyRequest` | `ApiResponse<RazorpayVerifyResponse>` | Authenticated | *None* | Missing |
| **POST** | `/api/payments/razorpay/qr-code` | `RazorpayPaymentController` | `createQrCode` | `RazorpayQrRequest` | `ApiResponse<RazorpayQrResponse>` | Authenticated | *None* | Missing |
| **POST** | `/api/payments/razorpay/webhook` | `RazorpayWebhookController` | `handleWebhook` | Raw String Payload | `ResponseEntity<Map>` | Public (Header verified) | External Razorpay Gateway | Full |
| **POST** | `/api/admin/payments/{id}/refund` | `AdminPaymentController` | `processRefund` | `RefundRequest` | `ApiResponse<RefundResponse>` | PreAuthorize `ADMIN` | *None* | Missing |
| **GET** | `/api/products/{id}/reviews` | `ReviewController` | `list` | Pageable | `ApiResponse<PageResponse<ReviewResponse>>` | Public | `ProductDetailPage.tsx` | Full |
| **POST** | `/api/products/{id}/reviews` | `ReviewController` | `create` | `ReviewRequest` | `ApiResponse<ReviewResponse>` | Authenticated | `ProductDetailPage.tsx` | Full |
| **GET** | `/api/admin/reviews` | `ReviewController` | `all` | Pageable | `ApiResponse<PageResponse<ReviewResponse>>` | PreAuthorize `ADMIN` | *None* | Missing |
| **PATCH** | `/api/admin/reviews/{id}/approve` | `ReviewController` | `approve` | *None* | `ApiResponse<ReviewResponse>` | PreAuthorize `ADMIN` | *None* | Missing |
| **GET** | `/api/referrals/me` | `ReferralController` | `getMyReferralSummary` | *None* | `ApiResponse<ReferralSummaryResponse>` | Authenticated | `referral.service.ts` | Full |
| **GET** | `/api/referrals/validate` | `ReferralController` | `validateReferral` | Query Param `code` | `ApiResponse<ValidateReferralResponse>` | Public | `referral.service.ts` | Full |
| **GET** | `/api/admin/audit-logs` | `AuditLogController` | `list` | Query Params, Pageable | `ApiResponse<PageResponse<AuditLogResponse>>` | PreAuthorize `ADMIN` | `audit.service.ts` / `AuditLogsView.tsx` | Full |
| **POST** | `/api/uploads/image` | `FileUploadController` | `uploadImage` | Multipart `file` or `image` | `ApiResponse<ImageUploadResponse>` | PreAuthorize `ADMIN, STAFF` | `uploads.service.ts` (`uploadImage`) | Full |
| **GET** | `/api/uploads/{fileName:.+}` | `FileUploadController` | `serveImage` | *None* | `ResponseEntity<Resource>` | Public | `<img>` tags across storefront | Full |
| **GET** | `/api/admin/dashboard` | `AdminController` | `dashboard` | *None* | `ApiResponse<DashboardResponse>` | PreAuthorize `ADMIN` | *None* (Derived client-side) | Missing |
| **GET** | `/api/admin/orders` | `AdminController` | `allOrders` | Pageable | `ApiResponse<PageResponse<OrderResponse>>` | PreAuthorize `ADMIN, STAFF` | *None* | Missing |
| **PATCH** | `/api/admin/orders/{id}/status` | `AdminController` | `status` | Query Param `status` | `ApiResponse<OrderResponse>` | PreAuthorize `ADMIN, STAFF` | *None* | Missing |

---

## 5. Dedicated API & DTO Data Contract Mismatch Table

| Feature / Contract | Frontend TypeScript Interface | Backend Java DTO / Entity | Discrepancy / Severity | Exact Root Cause & Consequence |
|---|---|---|---|---|
| **Online Payment Initiation** | In `CheckoutPage.tsx`: passes `{ shippingAddressId, paymentMethod: 'MOCK' }` to `createOrder()` | Backend `RazorpayPaymentController.java`: `POST /api/payments/razorpay/create-order` expects `RazorpayOrderRequest(Long shippingAddressId, PaymentMethod paymentMethod, String couponCode)` | **CRITICAL BLOCKER** | Frontend bypasses Razorpay order creation entirely and posts `MOCK` to `/api/orders`. Backend throws `IllegalArgumentException: MOCK payment method is strictly forbidden in production`. |
| **Payment Verification** | *None* (Frontend attempts simulated confirmation) | Backend `POST /api/payments/razorpay/verify` expects `RazorpayVerifyRequest(Long orderId, String razorpayOrderId, String razorpayPaymentId, String razorpaySignature)` | **CRITICAL BLOCKER** | Frontend has no verification service or payload; backend signature verification cannot execute. |
| **Cart Combo Group Deletion** | In `combos.service.ts` line 71: `DELETE /api/cart/combos/${comboGroupId}` | Backend `ComboController.java` lines 88-94: `@DeleteMapping("/cart/groups/{comboGroupId}")` under `@RequestMapping("/combos")` -> `DELETE /api/combos/cart/groups/{comboGroupId}` | **BROKEN INTEGRATION (HIGH)** | Calling `removeComboGroupFromCart` results in HTTP 404 Not Found; customer cannot remove custom bundles from cart. |
| **Admin Order Status Transition** | Previous documentation suggested `PUT /api/admin/orders/{id}/status` with body `{"status":"PROCESSING"}` | Backend `AdminController.java`: `@PatchMapping("/orders/{id}/status")` with `@RequestParam OrderStatus status` | **CONTRACT MISMATCH (HIGH)** | Endpoint is HTTP `PATCH` with query parameter `?status=...`. Sending a `PUT` with JSON body will produce HTTP 405 or 400. |
| **Combo Admin Image Field** | `CombosOffersAdminView.tsx`: `comboDraft.imageUrl` (string input `<input type="url">`) | Backend `Combo.java` entity: stores `imageFileName` (string) backed by `FileStorageService` | **PARTIAL MISMATCH (MEDIUM)** | External URL strings are saved as filenames; images fail to serve via `/api/uploads/{fileName}`. |
| **Order Item Identifier** | Frontend `OrderItemResponse` in `cart.types.ts`: expects `id: number` | Backend `OrderDtos.OrderItemResponse`: contains `productId`, `productName`, `quantity`, `price`, `subtotal`, `comboId`, `comboGroupId`, `comboType`, `comboName`, `originalPrice`, `discountAmount`. Does NOT include `id`. | **FIELD MISMATCH (LOW)** | If frontend renders table keys using `item.id`, it evaluates to `undefined`. Table keys must use `item.productId` or index. |
| **Shipping Field in Order** | Frontend `OrderResponse` in `cart.types.ts`: expects optional `shippingAmount` | Backend `OrderDtos.OrderResponse`: field name is `shippingCharge` | **NAMING MISMATCH (LOW)** | Frontend reading `order.shippingAmount` gets undefined; should read `order.shippingCharge`. |
| **Dynamic UPI QR Request** | Previous report suggested `{ orderId: 12, amount: 14999.00 }` | Backend `PaymentDtos.RazorpayQrRequest`: `record RazorpayQrRequest(Long shippingAddressId, String couponCode)` | **SPECIFICATION ERROR (MEDIUM)** | Dynamic QR order creation is calculated server-side from cart and `shippingAddressId`, not passed as arbitrary client amount. |

---

## 6. Dedicated Razorpay Live Production Audit

### 6.1 Check A: Payment Initiation
- **Frontend Defect:** In `masterim/.../src/pages/checkout/CheckoutPage.tsx`:
  - When user selects "Online Payment", `handleProceedOrder()` displays `showOnlinePaymentModal(true)` (lines 364–367).
  - The modal collects fake card/UPI details and triggers `executeOrderPlacement('MOCK')` (line 1461).
  - This calls `ordersService.createOrder()` with `paymentMethod: 'MOCK'`.
  - The backend payment creation endpoint `POST /api/payments/razorpay/create-order` is **never called**.
- **Backend Architecture:**
  - `OrderService.createRazorpayOrder(User user, RazorpayOrderRequest request)` correctly creates an internal `Order` in `PENDING` status.
  - The amount is calculated strictly server-side from active Cart items (paise conversion via `BigDecimal.multiply(100)`).
  - Razorpay order ID is generated via `RazorpayClient.orders.create()` and recorded in a `Payment` entity.
  - Inventory is NOT deducted until payment verification succeeds.

### 6.2 Check B: Razorpay Integration
- **SDK Dependency:** `com.razorpay:razorpay-java:1.4.7` is present in `pom.xml`.
- **Client Configuration:** `RazorpayConfig.java` initializes `RazorpayClient` bean using credentials from `RazorpayProperties`.
- **Order Creation:** `RazorpayService.createRazorpayOrder(order)` sends JSON payload with `amount`, `currency: INR`, `receipt: orderNumber`, and metadata notes.
- **Verification:** `RazorpaySignatureUtil.verifyPaymentSignature()` verifies HMAC-SHA256 signature using `keySecret`.
- **Live Verification Fetch:** `client.payments.fetch(razorpayPaymentId)` fetches the captured payment directly from Razorpay's API to ensure amount and status match.
- **Webhook Endpoint:** `POST /api/payments/razorpay/webhook` receives raw payloads and verifies `X-Razorpay-Signature`.

### 6.3 Check C: Payment Verification & Security
- **Untrusted Client Callbacks:** The backend does **not** rely on client success callbacks. An order cannot transition to `CONFIRMED` or `PAID` without passing HMAC SHA-256 signature verification or webhook signature verification.
- **Amount Tampering Protection:** In `RazorpayService.verifyPaymentSignatureAndDetails()` (lines 195–207), the backend verifies `actualPaise >= expectedPaise`. If amount tampering occurs, payment status is set to `FAILED` with failure code `AMOUNT_MISMATCH`.
- **Idempotency Protection:** Both `verifyRazorpayPayment()` and `processWebhook()` include deduplication checks:
  - `OrderService.java` (lines 318–328) checks if order is already `CONFIRMED` and payment `SUCCESS`.
  - `RazorpayWebhookService.java` (lines 98–101) checks `PaymentWebhookEventRepository.existsByRazorpayEventId(eventId)`.

### 6.4 Check D: Production Configuration Security
- **Configuration Variables:** Credentials are bound via environment variables:
  - `RAZORPAY_KEY_ID`
  - `RAZORPAY_KEY_SECRET`
  - `RAZORPAY_WEBHOOK_SECRET`
  - `RAZORPAY_CURRENCY` (default: `INR`)
- **Secret Protection:** No hardcoded production API secrets or webhook secrets exist in repository source code or logs.
- **Safe Fallback:** If `RAZORPAY_KEY_ID` is empty or test-placeholder, test fallbacks are used in unit test runs without throwing uncaught startup errors.

### 6.5 Check E: Checkout Frontend Reality
- **Razorpay SDK:** Razorpay Checkout script (`checkout.js`) is **not loaded** in `index.html` or dynamically in `CheckoutPage.tsx`.
- **Payload:** Frontend hardcodes `paymentMethod: 'MOCK'`, triggering HTTP 400.
- **Conclusion:** Online payment is 100% blocked on the client side.

### 6.6 Check F: Production Verification Limitation
- **Status:** **NOT VERIFIED (External Gateway)**
- *Audit Boundary:* Source code inspection confirms that backend Razorpay integration logic, signature verification, amount validation, and webhook processing are implemented in Java. However, access to the live production Razorpay Dashboard, live webhook URL delivery, and live banking transactions cannot be executed from source code inspection alone.

---

## 7. Cards Removal Compliance Audit

### Policy Requirement
> **MALKAZ Payment Method Policy:**  
> 1. Allowed Online Methods: **UPI** (Google Pay, PhonePe, Paytm, BHIM via Razorpay), **Razorpay Dynamic UPI QR Code**, and **Net Banking**.  
> 2. Allowed Offline Method: **Cash on Delivery (COD)**.  
> 3. **Cards MUST NOT be offered or supported as a selectable MALKAZ checkout payment method.**

### 7.1 Backend Compliance: PASS
- `PaymentMethod.java` (lines 41–43):
  ```java
  if ("CREDIT_CARD".equals(normalized) || "DEBIT_CARD".equals(normalized) || "CARD".equals(normalized)) {
      throw new IllegalArgumentException("Card payments (Credit/Debit cards) are not accepted. MALKAZ exclusively supports UPI, Net Banking, and Cash on Delivery.");
  }
  ```
- `RazorpayService.java` (lines 184–192):
  ```java
  if (method != null && (method.toLowerCase().contains("card") || method.equalsIgnoreCase("credit_card") || method.equalsIgnoreCase("debit_card"))) {
      payment.setFailureCode("CARD_PAYMENT_DISALLOWED");
      payment.setFailureReason("Card payments are strictly disallowed. Only UPI, Net Banking, and COD are permitted.");
      payment.setPaymentStatus(PaymentStatus.FAILED);
      paymentRepository.save(payment);
      throw new BadRequestException("Card payments are not accepted. Only UPI, Net Banking, and Cash on Delivery are supported.");
  }
  ```
- `RazorpayWebhookService.java` (lines 140–143): Explicitly logs and rejects webhook capture events if payment method is a card.
- `RazorpayPaymentIntegrationTest.java` (lines 312–329): Test case verifies that `CREDIT_CARD` payloads are strictly rejected.

### 7.2 Frontend Compliance: VIOLATION (MUST BE FIXED)
- **Violation 1 (`CheckoutPage.tsx` lines 1358–1371):** Renders a dedicated button tab labeled **"Cards"** (`selectedOnlineChannel === 'card'`).
- **Violation 2 (`CheckoutPage.tsx` lines 1408–1430):** Displays card input fields: "Card Number", "Expiry", and "CVV" with default value `"4242 •••• •••• 4242"`.
- **Violation 3 (`CheckoutPage.tsx` line 6, 555, 688, 726):** Imports and renders `<CreditCard />` Lucide icon for online payment options.
- **Action Required:** Remove the "Cards" tab, remove credit card input fields, and update payment icons/labels to showcase UPI and Net Banking.
