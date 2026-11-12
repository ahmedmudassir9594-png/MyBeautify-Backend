package com.cosmetics.ecommerce.service;

import com.cosmetics.ecommerce.dto.*;
import com.cosmetics.ecommerce.dto.OrderDtos.*;
import com.cosmetics.ecommerce.dto.PaymentDtos.*;
import com.cosmetics.ecommerce.entity.*;
import com.cosmetics.ecommerce.enums.*;
import com.cosmetics.ecommerce.exception.*;
import com.cosmetics.ecommerce.payment.PaymentGateway;
import com.cosmetics.ecommerce.payment.RazorpayService;
import com.cosmetics.ecommerce.payment.RazorpaySignatureUtil;
import com.cosmetics.ecommerce.repository.*;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.Year;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import com.cosmetics.ecommerce.email.EmailService;

@Service
public class OrderService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(OrderService.class);
    private final OrderRepository orders;
    private final CartService cartService;
    private final AddressRepository addresses;
    private final CouponRepository coupons;
    private final PaymentRepository payments;
    private final PaymentGateway paymentGateway;
    private final ProductRepository products;
    private final EmailService emailService;
    private final ReferralService referralService;
    private final AuditLogService auditLogService;
    private final RazorpayService razorpayService;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private UserRepository userRepository;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private org.springframework.transaction.PlatformTransactionManager transactionManager;
    private static final ConcurrentHashMap<Long, Object> USER_CHECKOUT_LOCKS = new ConcurrentHashMap<>();

    private <T> T executeInTransaction(java.util.function.Supplier<T> action) {
        if (transactionManager != null) {
            org.springframework.transaction.support.TransactionTemplate tt =
                    new org.springframework.transaction.support.TransactionTemplate(transactionManager);
            tt.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_READ_COMMITTED);
            return tt.execute(status -> action.get());
        }
        return action.get();
    }

    private void runInTransaction(Runnable action) {
        if (transactionManager != null) {
            org.springframework.transaction.support.TransactionTemplate tt =
                    new org.springframework.transaction.support.TransactionTemplate(transactionManager);
            tt.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_READ_COMMITTED);
            tt.executeWithoutResult(status -> action.run());
        } else {
            action.run();
        }
    }

    private record PreparedCheckout(
            RazorpayOrderResponse reusedResponse,
            Order savedOrder,
            Long paymentId,
            BigDecimal totalAmount,
            long amountInPaise,
            String phoneNumber,
            String customerName
    ) {}

    @org.springframework.beans.factory.annotation.Autowired
    public OrderService(OrderRepository orders, CartService cartService, AddressRepository addresses, CouponRepository coupons,
                        PaymentRepository payments, PaymentGateway paymentGateway, ProductRepository products, EmailService emailService,
                        ReferralService referralService, AuditLogService auditLogService, RazorpayService razorpayService) {
        this.orders = orders; this.cartService = cartService; this.addresses = addresses; this.coupons = coupons;
        this.payments = payments; this.paymentGateway = paymentGateway; this.products = products; this.emailService = emailService;
        this.referralService = referralService; this.auditLogService = auditLogService; this.razorpayService = razorpayService;
    }

    public OrderService(OrderRepository orders, CartService cartService, AddressRepository addresses, CouponRepository coupons,
                        PaymentRepository payments, PaymentGateway paymentGateway, ProductRepository products, EmailService emailService,
                        ReferralService referralService, AuditLogService auditLogService) {
        this(orders, cartService, addresses, coupons, payments, paymentGateway, products, emailService, referralService, auditLogService, null);
    }

    public OrderService(OrderRepository orders, CartService cartService, AddressRepository addresses, CouponRepository coupons,
                        PaymentRepository payments, PaymentGateway paymentGateway, ProductRepository products, EmailService emailService,
                        ReferralService referralService) {
        this(orders, cartService, addresses, coupons, payments, paymentGateway, products, emailService, referralService, null, null);
    }

    private record PricingDetails(
            Cart cart,
            Address address,
            BigDecimal subtotal,
            Coupon coupon,
            BigDecimal discount,
            BigDecimal shipping,
            BigDecimal tax,
            BigDecimal total
    ) {}

    private PricingDetails calculatePricing(User user, Long shippingAddressId, String couponCode) {
        if (shippingAddressId == null) {
            throw new BadRequestException("A valid delivery address is required.");
        }
        Cart cart = cartService.entity(user);
        if (cart.getItems().isEmpty()) throw new BadRequestException("Your cart is empty.");

        Address address = addresses.findByIdAndUserId(shippingAddressId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Selected shipping address was not found."));

        Map<Long, Integer> totalProductQuantities = new HashMap<>();
        for (CartItem item : cart.getItems()) {
            totalProductQuantities.merge(item.getProduct().getId(), item.getQuantity(), Integer::sum);
        }

        BigDecimal subtotal = BigDecimal.ZERO;
        for (CartItem item : cart.getItems()) {
            Product product = item.getProduct();
            if (!product.isActive()) throw new BadRequestException("Product " + product.getName() + " is no longer available.");
            int totalReq = totalProductQuantities.get(product.getId());
            if (product.getStockQuantity() < totalReq) {
                throw new InsufficientStockException("Insufficient stock for " + product.getName() + ".");
            }

            if (item.getCombo() != null && !item.getCombo().isValidNow(Instant.now())) {
                throw new BadRequestException("Combo '" + item.getComboName() + "' is no longer valid or has expired.");
            }

            BigDecimal effectivePrice = (item.getComboGroupId() != null && item.getPrice() != null)
                    ? item.getPrice()
                    : product.sellingPrice();
            subtotal = subtotal.add(effectivePrice.multiply(BigDecimal.valueOf(item.getQuantity())));
        }

        Coupon coupon = null;
        BigDecimal discount = BigDecimal.ZERO;
        if (couponCode != null && !couponCode.isBlank()) {
            String code = couponCode.trim();
            var couponOpt = coupons.findByCodeIgnoreCase(code);
            if (couponOpt.isPresent()) {
                coupon = couponOpt.get();
                if (!coupon.availableAt(Instant.now()) || subtotal.compareTo(coupon.getMinimumOrderAmount()) < 0)
                    throw new InvalidCouponException("Coupon is expired, unavailable, or does not meet the minimum order amount.");
                discount = calculateDiscount(coupon, subtotal);
            } else {
                var validation = referralService.validateReferralCode(code, user);
                if (validation.valid()) {
                    if (subtotal.compareTo(referralService.getMinimumOrderAmount()) < 0) {
                        throw new InvalidCouponException("Order does not meet the referral minimum order amount of " + referralService.getMinimumOrderAmount() + ".");
                    }
                    if (orders.countByUserId(user.getId()) > 0) {
                        throw new BadRequestException("Friend referral discount is only valid for your first order.");
                    }
                    discount = subtotal.multiply(referralService.getDiscountPercent()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                    referralService.linkReferralOnRegister(user, code);
                } else {
                    throw new InvalidCouponException(validation.message());
                }
            }
        } else if (user.getReferredBy() != null && orders.countByUserId(user.getId()) == 0 && subtotal.compareTo(referralService.getMinimumOrderAmount()) >= 0) {
            discount = subtotal.multiply(referralService.getDiscountPercent()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        }

        BigDecimal shipping = new BigDecimal(System.getenv().getOrDefault("CHECKOUT_SHIPPING", "8.99"));
        BigDecimal taxRate = new BigDecimal(System.getenv().getOrDefault("CHECKOUT_TAX_RATE", "0.08"));
        BigDecimal taxable = subtotal.subtract(discount).max(BigDecimal.ZERO);
        BigDecimal tax = taxable.multiply(taxRate).setScale(2, RoundingMode.HALF_UP);
        BigDecimal total = taxable.add(shipping).add(tax).setScale(2, RoundingMode.HALF_UP);

        return new PricingDetails(cart, address, subtotal, coupon, discount, shipping, tax, total);
    }

    /**
     * Standard Checkout Endpoint (Used for Cash on Delivery)
     */
    @Transactional
    public OrderResponse checkout(User user, OrderRequest request) {
        if (request == null) throw new BadRequestException("Order request is required.");
        if (request.shippingAddressId() == null) throw new BadRequestException("A valid delivery address is required.");
        if (request.paymentMethod() == null) throw new BadRequestException("Payment method is required.");

        // Multi-instance distributed mutual exclusion for customer checkout creation
        if (userRepository != null && user != null && user.getId() != null) {
            userRepository.findByIdForUpdate(user.getId());
        }

        PricingDetails pricing = calculatePricing(user, request.shippingAddressId(), request.couponCode());

        PaymentGateway.PaymentResult result = paymentGateway.charge(pricing.total(), request.paymentMethod());
        if (!result.successful()) {
            if (auditLogService != null) {
                auditLogService.logEvent(AuditAction.PAYMENT_FAILURE, AuditResult.FAILURE, "PAYMENT", null,
                        "Payment failed via " + request.paymentMethod() + " for user " + user.getEmail(), null,
                        "{\"amount\":" + pricing.total() + ",\"paymentMethod\":\"" + request.paymentMethod() + "\"}");
            }
            throw new BadRequestException("Payment could not be completed via " + request.paymentMethod() + ".");
        }

        PaymentStatus initialPaymentStatus = request.paymentMethod().isCod() ? PaymentStatus.PENDING : PaymentStatus.SUCCESS;
        Order order = new Order(nextOrderNumber(), user, pricing.address(), pricing.subtotal(), pricing.discount(), pricing.shipping(), pricing.tax(), pricing.total(), request.paymentMethod());
        order.setCoupon(pricing.coupon());
        order.setPaymentStatus(initialPaymentStatus);
        order.setOrderStatus(OrderStatus.CONFIRMED);

        for (CartItem item : pricing.cart().getItems()) {
            Product product = item.getProduct();
            product.setStockQuantity(product.getStockQuantity() - item.getQuantity());

            BigDecimal itemPrice = (item.getComboGroupId() != null && item.getPrice() != null)
                    ? item.getPrice()
                    : product.sellingPrice();

            OrderItem orderItem = new OrderItem(
                    order,
                    product,
                    item.getQuantity(),
                    itemPrice,
                    item.getCombo() != null ? item.getCombo().getId() : null,
                    item.getComboGroupId(),
                    item.getComboType(),
                    item.getComboName(),
                    item.getOriginalPrice() != null ? item.getOriginalPrice() : product.sellingPrice(),
                    item.getDiscountAmount() != null ? item.getDiscountAmount() : BigDecimal.ZERO
            );
            order.getItems().add(orderItem);
        }

        if (pricing.coupon() != null) pricing.coupon().incrementUsedCount();
        Order saved = orders.save(order);
        payments.save(new Payment(saved, result.transactionId(), result.gateway(), pricing.total(), request.paymentMethod(), initialPaymentStatus));
        referralService.recordQualifyingPurchase(saved);
        pricing.cart().getItems().clear();
        pricing.cart().touch();
        emailService.sendOrderConfirmation(user.getEmail(), saved.getOrderNumber(), saved.getTotalAmount());

        if (auditLogService != null) {
            String orderState = String.format("{\"orderNumber\":\"%s\",\"total\":%s,\"itemsCount\":%d,\"paymentMethod\":\"%s\",\"shipping\":%s,\"tax\":%s}",
                    saved.getOrderNumber(), saved.getTotalAmount(), saved.getItems().size(), saved.getPaymentMethod(), saved.getShippingCharge(), saved.getTaxAmount());
            auditLogService.logEvent(AuditAction.ORDER_CREATE, AuditResult.SUCCESS, "ORDER", String.valueOf(saved.getId()),
                    "Order created: " + saved.getOrderNumber() + " with total " + saved.getTotalAmount(), null, orderState);

            String paymentState = String.format("{\"orderNumber\":\"%s\",\"transactionId\":\"%s\",\"gateway\":\"%s\",\"amount\":%s,\"method\":\"%s\"}",
                    saved.getOrderNumber(), result.transactionId(), result.gateway(), pricing.total(), request.paymentMethod());
            auditLogService.logEvent(AuditAction.PAYMENT_SUCCESS, AuditResult.SUCCESS, "PAYMENT", result.transactionId(),
                    "Payment charged successfully for order " + saved.getOrderNumber(), null, paymentState);
        }

        return OrderResponse.from(saved);
    }

    /**
     * Phase A: Create or Recover Razorpay Order
     * Refactored into safe transactional stages to prevent connection pool exhaustion:
     * - Stage 1 (Transaction A): Acquire lock, validate cart & inventory, reuse or prepare durable pending order, commit.
     * - Stage 2 (Outside Transaction): Invoke external Razorpay Orders API without holding DB lock or connection.
     * - Stage 3 (Transaction B): Reacquire lock, attach Razorpay Order ID, finalize and commit.
     */
    public RazorpayOrderResponse createRazorpayOrder(User user, RazorpayOrderRequest request) {
        if (request == null) {
            throw new BadRequestException("Razorpay order request is required.");
        }
        if (request.shippingAddressId() == null) {
            throw new BadRequestException("A valid delivery address is required.");
        }
        if (request.paymentMethod() == null || !request.paymentMethod().isOnline()) {
            throw new BadRequestException("Razorpay checkout supports only online methods (UPI, UPI_QR, NET_BANKING). For COD, use standard checkout.");
        }
        if (razorpayService == null) {
            throw new BadRequestException("Razorpay payment service is not configured.");
        }

        Object userLock = USER_CHECKOUT_LOCKS.computeIfAbsent(user.getId(), k -> new Object());
        synchronized (userLock) {
            // Stage 1 (Transaction A): Validate, check pending reuse, create durable pending records, commit
            PreparedCheckout prep = executeInTransaction(() -> {
                // Multi-instance distributed mutual exclusion
                if (userRepository != null && user != null && user.getId() != null) {
                    userRepository.findByIdForUpdate(user.getId());
                }

                PricingDetails pricing = calculatePricing(user, request.shippingAddressId(), request.couponCode());

                // 1. Inspect existing active PENDING orders for safe reuse
                List<Order> pendingOrders = orders.findPendingOrdersForRecovery(user.getId(), OrderStatus.PENDING, PaymentStatus.PENDING);
                for (Order existingOrder : pendingOrders) {
                    Optional<Payment> paymentOpt = payments.findByOrderId(existingOrder.getId());
                    if (paymentOpt.isEmpty()) continue;
                    Payment payment = paymentOpt.get();

                    if (payment.getPaymentStatus() != PaymentStatus.PENDING ||
                        payment.getRazorpayOrderId() == null ||
                        !"RAZORPAY".equals(payment.getPaymentGateway()) ||
                        payment.getPaidAt() != null) {
                        continue;
                    }

                    if (existingOrder.getCreatedAt() != null &&
                        existingOrder.getCreatedAt().isBefore(Instant.now().minus(java.time.Duration.ofMinutes(30)))) {
                        continue;
                    }

                    if (existingOrder.getShippingAddress() == null ||
                        !existingOrder.getShippingAddress().getId().equals(pricing.address().getId())) {
                        continue;
                    }

                    boolean couponMatches = (existingOrder.getCoupon() == null && pricing.coupon() == null) ||
                            (existingOrder.getCoupon() != null && pricing.coupon() != null &&
                             existingOrder.getCoupon().getId().equals(pricing.coupon().getId()));
                    if (!couponMatches) {
                        continue;
                    }

                    if (existingOrder.getTotalAmount().compareTo(pricing.total()) != 0 ||
                        existingOrder.getSubtotal().compareTo(pricing.subtotal()) != 0 ||
                        existingOrder.getDiscountAmount().compareTo(pricing.discount()) != 0 ||
                        existingOrder.getShippingCharge().compareTo(pricing.shipping()) != 0 ||
                        existingOrder.getTaxAmount().compareTo(pricing.tax()) != 0) {
                        continue;
                    }

                    List<CartItem> cartItems = pricing.cart().getItems();
                    List<OrderItem> orderItems = existingOrder.getItems();
                    if (orderItems.size() != cartItems.size()) {
                        continue;
                    }

                    boolean itemsMatch = true;
                    for (CartItem ci : cartItems) {
                        BigDecimal effectivePrice = (ci.getComboGroupId() != null && ci.getPrice() != null)
                                ? ci.getPrice()
                                : ci.getProduct().sellingPrice();
                        boolean foundMatchingItem = false;
                        for (OrderItem oi : orderItems) {
                            boolean sameProduct = oi.getProduct() != null && oi.getProduct().getId().equals(ci.getProduct().getId());
                            boolean sameQty = oi.getQuantity() == ci.getQuantity();
                            boolean samePrice = oi.getPrice() != null && oi.getPrice().compareTo(effectivePrice) == 0;
                            boolean sameCombo = (oi.getComboGroupId() == null && ci.getComboGroupId() == null) ||
                                    (oi.getComboGroupId() != null && oi.getComboGroupId().equals(ci.getComboGroupId()));
                            if (sameProduct && sameQty && samePrice && sameCombo) {
                                foundMatchingItem = true;
                                break;
                            }
                        }
                        if (!foundMatchingItem) {
                            itemsMatch = false;
                            break;
                        }
                    }

                    if (!itemsMatch) {
                        continue;
                    }

                    // Reusing existing order and Razorpay order ID
                    if (existingOrder.getPaymentMethod() != request.paymentMethod()) {
                        existingOrder.setPaymentMethod(request.paymentMethod());
                        payment.setPaymentMethod(request.paymentMethod());
                        orders.save(existingOrder);
                        payments.save(payment);
                    }

                    long amountInPaise = pricing.total().multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).longValue();
                    String customerName = (user.getFirstName() != null ? user.getFirstName() : "") + " " + (user.getLastName() != null ? user.getLastName() : "");

                    if (auditLogService != null) {
                        auditLogService.logEvent(AuditAction.ORDER_CREATE, AuditResult.SUCCESS, "ORDER", String.valueOf(existingOrder.getId()),
                                "Reusing existing pending order for Razorpay checkout: " + existingOrder.getOrderNumber() + " with total " + existingOrder.getTotalAmount(), null,
                                "{\"orderNumber\":\"" + existingOrder.getOrderNumber() + "\",\"razorpayOrderId\":\"" + payment.getRazorpayOrderId() + "\",\"reused\":true}");
                    }

                    RazorpayOrderResponse response = new RazorpayOrderResponse(
                            existingOrder.getId(),
                            existingOrder.getOrderNumber(),
                            payment.getRazorpayOrderId(),
                            pricing.total(),
                            amountInPaise,
                            payment.getCurrency(),
                            razorpayService.getProperties().getKeyId(),
                            customerName.trim(),
                            user.getEmail(),
                            pricing.address().getPhoneNumber(),
                            razorpayService.getProperties().getCompanyName(),
                            razorpayService.getProperties().getThemeColor(),
                            request.paymentMethod()
                    );
                    // Cancel any other stale pending orders that were not reused
                    for (Order otherPending : pendingOrders) {
                        if (!otherPending.getId().equals(existingOrder.getId())) {
                            Optional<Payment> pOpt = payments.findByOrderId(otherPending.getId());
                            if (pOpt.isPresent()) {
                                Payment p = pOpt.get();
                                if (p.getPaymentStatus() == PaymentStatus.PENDING && p.getPaidAt() == null) {
                                    otherPending.setOrderStatus(OrderStatus.CANCELLED);
                                    p.setPaymentStatus(PaymentStatus.FAILED);
                                    p.setFailureCode("SUPERSEDED");
                                    p.setFailureReason("Superseded by reuse of pending order #" + existingOrder.getOrderNumber());
                                    orders.save(otherPending);
                                    payments.save(p);
                                }
                            }
                        }
                    }

                    return new PreparedCheckout(response, null, null, null, 0, null, null);
                }

                // 2. Cancel any stale/superseded pending orders that were never paid
                for (Order oldOrder : pendingOrders) {
                    Optional<Payment> pOpt = payments.findByOrderId(oldOrder.getId());
                    if (pOpt.isPresent()) {
                        Payment p = pOpt.get();
                        if (p.getPaymentStatus() == PaymentStatus.PENDING && p.getPaidAt() == null) {
                            oldOrder.setOrderStatus(OrderStatus.CANCELLED);
                            p.setPaymentStatus(PaymentStatus.FAILED);
                            p.setFailureCode("SUPERSEDED");
                            p.setFailureReason("New checkout initiated with modified cart details.");
                            orders.save(oldOrder);
                            payments.save(p);
                        }
                    }
                }

                // 3. Create fresh durable pending Order & Payment attempt
                Order order = new Order(nextOrderNumber(), user, pricing.address(), pricing.subtotal(), pricing.discount(), pricing.shipping(), pricing.tax(), pricing.total(), request.paymentMethod());
                order.setCoupon(pricing.coupon());
                order.setPaymentStatus(PaymentStatus.PENDING);
                order.setOrderStatus(OrderStatus.PENDING);

                for (CartItem item : pricing.cart().getItems()) {
                    Product product = item.getProduct();
                    BigDecimal itemPrice = (item.getComboGroupId() != null && item.getPrice() != null)
                            ? item.getPrice()
                            : product.sellingPrice();

                    OrderItem orderItem = new OrderItem(
                            order,
                            product,
                            item.getQuantity(),
                            itemPrice,
                            item.getCombo() != null ? item.getCombo().getId() : null,
                            item.getComboGroupId(),
                            item.getComboType(),
                            item.getComboName(),
                            item.getOriginalPrice() != null ? item.getOriginalPrice() : product.sellingPrice(),
                            item.getDiscountAmount() != null ? item.getDiscountAmount() : BigDecimal.ZERO
                    );
                    order.getItems().add(orderItem);
                }

                Order savedOrder = orders.save(order);

                String internalTxn = "TXN-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
                Payment payment = new Payment(savedOrder, internalTxn, "RAZORPAY", pricing.total(), request.paymentMethod(), PaymentStatus.PENDING);
                Payment savedPayment = payments.save(payment);

                long amountInPaise = pricing.total().multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).longValue();
                String customerName = (user.getFirstName() != null ? user.getFirstName() : "") + " " + (user.getLastName() != null ? user.getLastName() : "");

                return new PreparedCheckout(
                        null,
                        savedOrder,
                        savedPayment.getId(),
                        pricing.total(),
                        amountInPaise,
                        pricing.address().getPhoneNumber(),
                        customerName.trim()
                );
            });

            // If an existing pending order was eligible for reuse, return immediately!
            if (prep.reusedResponse() != null) {
                return prep.reusedResponse();
            }

            // Stage 2 (Outside Transaction): Invoke external Razorpay Orders API
            String razorpayOrderId = null;
            try {
                razorpayOrderId = razorpayService.createRazorpayOrder(prep.savedOrder());
            } catch (Exception e) {
                log.error("Failed to create Razorpay order for internal order {}: {}", prep.savedOrder().getOrderNumber(), e.getMessage(), e);
                // Record failure in a separate short transaction
                runInTransaction(() -> {
                    payments.findById(prep.paymentId()).ifPresent(p -> {
                        p.setPaymentStatus(PaymentStatus.FAILED);
                        p.setFailureCode("GATEWAY_ERROR");
                        p.setFailureReason("Failed to create Razorpay order: " + e.getMessage());
                        payments.save(p);
                    });
                });
                throw new BadRequestException("Unable to create Razorpay payment order: " + e.getMessage());
            }

            // Stage 3 (Transaction B): Reacquire lock, verify attempt still eligible, persist Razorpay Order ID
            final String finalRazorpayOrderId = razorpayOrderId;
            return executeInTransaction(() -> {
                if (userRepository != null && user != null && user.getId() != null) {
                    userRepository.findByIdForUpdate(user.getId());
                }

                Order currentOrder = orders.findById(prep.savedOrder().getId())
                        .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + prep.savedOrder().getId()));
                Payment currentPayment = payments.findById(prep.paymentId())
                        .orElseThrow(() -> new ResourceNotFoundException("Payment not found: " + prep.paymentId()));

                if (currentOrder.getOrderStatus() != OrderStatus.PENDING) {
                    currentPayment.setRazorpayOrderId(finalRazorpayOrderId);
                    currentPayment.setPaymentStatus(PaymentStatus.FAILED);
                    currentPayment.setFailureCode("SUPERSEDED_DURING_CREATION");
                    currentPayment.setFailureReason("Checkout attempt was superseded while contacting payment gateway.");
                    payments.save(currentPayment);
                    throw new BadRequestException("Checkout was superseded by a newer request. Please retry.");
                }

                currentPayment.setRazorpayOrderId(finalRazorpayOrderId);
                payments.save(currentPayment);

                if (auditLogService != null) {
                    auditLogService.logEvent(AuditAction.ORDER_CREATE, AuditResult.SUCCESS, "ORDER", String.valueOf(currentOrder.getId()),
                            "Pending order initiated for Razorpay checkout: " + currentOrder.getOrderNumber() + " with total " + currentOrder.getTotalAmount(), null,
                            "{\"orderNumber\":\"" + currentOrder.getOrderNumber() + "\",\"razorpayOrderId\":\"" + finalRazorpayOrderId + "\"}");
                }

                return new RazorpayOrderResponse(
                        currentOrder.getId(),
                        currentOrder.getOrderNumber(),
                        finalRazorpayOrderId,
                        prep.totalAmount(),
                        prep.amountInPaise(),
                        currentPayment.getCurrency(),
                        razorpayService.getProperties().getKeyId(),
                        prep.customerName(),
                        user.getEmail(),
                        prep.phoneNumber(),
                        razorpayService.getProperties().getCompanyName(),
                        razorpayService.getProperties().getThemeColor(),
                        request.paymentMethod()
                );
            });
        }
    }

    /**
     * Dynamic UPI QR Generation
     */
    @Transactional
    public RazorpayQrResponse createRazorpayQrCode(User user, RazorpayQrRequest request) {
        RazorpayOrderRequest orderReq = new RazorpayOrderRequest(request.shippingAddressId(), PaymentMethod.UPI_QR, request.couponCode());
        RazorpayOrderResponse orderResp = createRazorpayOrder(user, orderReq);
        Order order = orders.findById(orderResp.orderId())
                .orElseThrow(() -> new ResourceNotFoundException("Order not found."));

        return razorpayService.createDynamicQrCode(order, orderResp.razorpayOrderId());
    }

    /**
     * Phase B: Verify Razorpay Payment Signature and Finalize Order
     */
    public RazorpayVerifyResponse verifyRazorpayPayment(User user, RazorpayVerifyRequest request) {
        Order order = orders.findById(request.orderId())
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with ID: " + request.orderId()));

        // IDOR protection: Verify order belongs to the authenticated user
        if (!order.getUser().getId().equals(user.getId()) && user.getRole() != Role.ADMIN && user.getRole() != Role.STAFF) {
            throw new UnauthorizedException("You are not authorized to verify payment for this order.");
        }

        Payment payment = payments.findByOrderId(order.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Payment record not found for order: " + order.getOrderNumber()));

        // Idempotency: If already confirmed and success, return previous confirmation
        if (order.getOrderStatus() == OrderStatus.CONFIRMED && payment.getPaymentStatus() == PaymentStatus.SUCCESS) {
            return new RazorpayVerifyResponse(
                    order.getId(),
                    order.getOrderNumber(),
                    payment.getPaymentStatus(),
                    payment.getTransactionId(),
                    payment.getRazorpayPaymentId(),
                    payment.getAmount(),
                    "Payment already verified and order confirmed."
            );
        }

        // 1. Verify HMAC SHA-256 signature
        boolean validSignature = RazorpaySignatureUtil.verifyPaymentSignature(
                request.razorpayOrderId(), request.razorpayPaymentId(), request.razorpaySignature(), razorpayService.getProperties().getKeySecret()
        );
        if (!validSignature) {
            payment.setFailureCode("INVALID_SIGNATURE");
            payment.setFailureReason("Razorpay HMAC SHA-256 signature verification failed.");
            payment.setPaymentStatus(PaymentStatus.FAILED);
            payments.save(payment);
            throw new BadRequestException("Payment verification failed: Invalid Razorpay signature.");
        }

        // 2. Perform live gateway verification checks (reject cards, check amount)
        razorpayService.verifyPaymentSignatureAndDetails(payment, request.razorpayOrderId(), request.razorpayPaymentId(), request.razorpaySignature());

        // 3. Atomically finalize order, deduct inventory, and clear cart
        return finalizeSuccessfulOrder(order.getId(), request.razorpayPaymentId(), request.razorpaySignature(), user);
    }

    public RazorpayVerifyResponse finalizeSuccessfulOrder(Long orderId, String razorpayPaymentId, String razorpaySignature, User user) {
        return executeInTransaction(() -> {
            Order order = orders.findByIdForUpdate(orderId)
                    .orElseThrow(() -> new ResourceNotFoundException("Order not found with ID: " + orderId));

            Payment payment = payments.findByOrderIdForUpdate(order.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Payment record not found for order: " + order.getOrderNumber()));

            // Idempotency check under pessimistic row lock: If already confirmed, return without duplicate stock deduction
            if (order.getOrderStatus() == OrderStatus.CONFIRMED && payment.getPaymentStatus() == PaymentStatus.SUCCESS) {
                log.info("Order {} is already CONFIRMED and payment is SUCCESS. Idempotent return without duplicate stock deduction.", order.getOrderNumber());
                return new RazorpayVerifyResponse(
                        order.getId(),
                        order.getOrderNumber(),
                        payment.getPaymentStatus(),
                        payment.getTransactionId(),
                        payment.getRazorpayPaymentId() != null ? payment.getRazorpayPaymentId() : razorpayPaymentId,
                        payment.getAmount(),
                        "Payment already verified and order confirmed."
                );
            }

            // If order was cancelled/superseded, do NOT confirm or deduct stock!
            if (order.getOrderStatus() == OrderStatus.CANCELLED) {
                payment.setRazorpayPaymentId(razorpayPaymentId);
                payment.setPaidAt(Instant.now());
                payment.setPaymentStatus(PaymentStatus.REFUND_REVIEW_REQUIRED);
                payment.setFailureCode("ORPHAN_CAPTURE_ON_CANCELLED_ORDER");
                payment.setFailureReason("Payment verified on a CANCELLED order. Flagged for review/refund.");
                payments.save(payment);
                throw new BadRequestException("Order was cancelled or superseded. Payment flagged for review/refund.");
            }

            payment.setRazorpayPaymentId(razorpayPaymentId);
            if (razorpaySignature != null) {
                payment.setRazorpaySignature(razorpaySignature);
            }
            payment.setPaymentStatus(PaymentStatus.SUCCESS);
            payment.setPaidAt(Instant.now());
            payments.save(payment);

            order.setOrderStatus(OrderStatus.CONFIRMED);
            order.setPaymentStatus(PaymentStatus.SUCCESS);

            // Deduct inventory atomically under order row lock
            for (OrderItem item : order.getItems()) {
                Product product = item.getProduct();
                if (product != null) {
                    product.setStockQuantity(Math.max(0, product.getStockQuantity() - item.getQuantity()));
                    products.save(product);
                }
            }

            if (order.getCoupon() != null) {
                order.getCoupon().incrementUsedCount();
            }

            Order saved = orders.save(order);
            referralService.recordQualifyingPurchase(saved);

            // Clear customer cart
            User cartUser = user != null ? user : saved.getUser();
            if (cartUser != null) {
                Cart cart = cartService.entity(cartUser);
                cart.getItems().clear();
                cart.touch();
            }

            String customerEmail = user != null ? user.getEmail() : (saved.getUser() != null ? saved.getUser().getEmail() : null);
            if (customerEmail != null) {
                emailService.sendOrderConfirmation(customerEmail, saved.getOrderNumber(), saved.getTotalAmount());
            }

            if (auditLogService != null) {
                String paymentState = String.format("{\"orderNumber\":\"%s\",\"transactionId\":\"%s\",\"razorpayPaymentId\":\"%s\",\"amount\":%s}",
                        saved.getOrderNumber(), payment.getTransactionId(), razorpayPaymentId, payment.getAmount());
                auditLogService.logEvent(AuditAction.PAYMENT_SUCCESS, AuditResult.SUCCESS, "PAYMENT", razorpayPaymentId,
                        "Razorpay payment verified and order confirmed: " + saved.getOrderNumber(), null, paymentState);
            }

            return new RazorpayVerifyResponse(
                    saved.getId(),
                    saved.getOrderNumber(),
                    payment.getPaymentStatus(),
                    payment.getTransactionId(),
                    payment.getRazorpayPaymentId(),
                    payment.getAmount(),
                    "Payment verified successfully. Order confirmed."
            );
        });
    }

    public RazorpayVerifyResponse finalizeSuccessfulOrder(Order order, Payment payment, String razorpayPaymentId, String razorpaySignature, User user) {
        return finalizeSuccessfulOrder(order.getId(), razorpayPaymentId, razorpaySignature, user);
    }

    /**
     * Get Payment Status for an Order
     */
    @Transactional(readOnly = true)
    public PaymentStatusResponse getPaymentStatus(User user, Long orderId) {
        Order order = orders.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with ID: " + orderId));

        if (!order.getUser().getId().equals(user.getId()) && user.getRole() != Role.ADMIN && user.getRole() != Role.STAFF) {
            throw new UnauthorizedException("You are not authorized to view payment details for this order.");
        }

        Payment payment = payments.findByOrderId(order.getId())
                .orElseThrow(() -> new ResourceNotFoundException("No payment found for order: " + order.getOrderNumber()));

        return PaymentStatusResponse.from(payment);
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> list(User user, Pageable pageable) {
        return PageResponse.from(orders.findByUserIdOrderByCreatedAtDesc(user.getId(), pageable).map(OrderResponse::from));
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> all(Pageable pageable) {
        return PageResponse.from(orders.findAll(pageable).map(OrderResponse::from));
    }

    @Transactional(readOnly = true)
    public OrderResponse get(User user, Long id) {
        return OrderResponse.from(orders.findByIdAndUserId(id, user.getId()).orElseThrow(() -> new ResourceNotFoundException("Order not found.")));
    }

    @Transactional(readOnly = true)
    public OrderResponse byNumber(User user, String orderNumber) {
        return OrderResponse.from(orders.findByOrderNumberAndUserId(orderNumber, user.getId()).orElseThrow(() -> new ResourceNotFoundException("Order not found.")));
    }

    @Transactional
    public OrderResponse cancel(User user, Long id) {
        Order order = orders.findByIdAndUserId(id, user.getId()).orElseThrow(() -> new ResourceNotFoundException("Order not found."));
        if (!(order.getOrderStatus() == OrderStatus.PENDING || order.getOrderStatus() == OrderStatus.CONFIRMED))
            throw new OrderCancellationException("This order can no longer be cancelled.");
        OrderStatus previous = order.getOrderStatus();
        order.setOrderStatus(OrderStatus.CANCELLED);
        order.getItems().forEach(item -> item.getProduct().setStockQuantity(item.getProduct().getStockQuantity() + item.getQuantity()));

        if (auditLogService != null) {
            auditLogService.logEvent(AuditAction.ORDER_CANCEL, AuditResult.SUCCESS, "ORDER", String.valueOf(order.getId()),
                    "Customer cancelled order " + order.getOrderNumber() + ". Inventory restored.",
                    "{\"orderStatus\":\"" + previous + "\"}", "{\"orderStatus\":\"CANCELLED\"}");
        }

        return OrderResponse.from(order);
    }

    @Transactional
    public OrderResponse updateStatus(Long id, OrderStatus status) {
        Order order = orders.findById(id).orElseThrow(() -> new ResourceNotFoundException("Order not found."));
        if (!validTransition(order.getOrderStatus(), status)) throw new BadRequestException("Invalid order status transition.");
        OrderStatus previous = order.getOrderStatus();
        if (status == OrderStatus.CANCELLED && order.getOrderStatus() != OrderStatus.CANCELLED) {
            order.getItems().forEach(item -> item.getProduct().setStockQuantity(item.getProduct().getStockQuantity() + item.getQuantity()));
        }
        order.setOrderStatus(status);

        if (auditLogService != null) {
            String msg = "Order " + order.getOrderNumber() + " status updated from " + previous + " to " + status
                    + (status == OrderStatus.CANCELLED ? ". Inventory restored." : "");
            AuditAction action = (status == OrderStatus.CANCELLED) ? AuditAction.ORDER_CANCEL : AuditAction.ORDER_STATUS_UPDATE;
            auditLogService.logEvent(action, AuditResult.SUCCESS, "ORDER", String.valueOf(order.getId()),
                    msg, "{\"orderStatus\":\"" + previous + "\"}", "{\"orderStatus\":\"" + status + "\"}");
        }

        return OrderResponse.from(order);
    }

    private boolean validTransition(OrderStatus current, OrderStatus next) {
        if (next == OrderStatus.CANCELLED) return current == OrderStatus.PENDING || current == OrderStatus.CONFIRMED;
        Map<OrderStatus, OrderStatus> nextStates = new EnumMap<>(OrderStatus.class);
        nextStates.put(OrderStatus.PENDING, OrderStatus.CONFIRMED); nextStates.put(OrderStatus.CONFIRMED, OrderStatus.PROCESSING);
        nextStates.put(OrderStatus.PROCESSING, OrderStatus.PACKED); nextStates.put(OrderStatus.PACKED, OrderStatus.SHIPPED);
        nextStates.put(OrderStatus.SHIPPED, OrderStatus.OUT_FOR_DELIVERY); nextStates.put(OrderStatus.OUT_FOR_DELIVERY, OrderStatus.DELIVERED);
        return nextStates.get(current) == next;
    }

    private BigDecimal calculateDiscount(Coupon coupon, BigDecimal subtotal) {
        BigDecimal discount = coupon.getDiscountType() == DiscountType.PERCENTAGE
                ? subtotal.multiply(coupon.getDiscountValue()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                : coupon.getDiscountValue();
        if (coupon.getMaximumDiscount() != null) discount = discount.min(coupon.getMaximumDiscount());
        return discount.min(subtotal);
    }

    private String nextOrderNumber() {
        String randomSuffix = java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        return "COS-" + Year.now().getValue() + "-" + randomSuffix;
    }
}
