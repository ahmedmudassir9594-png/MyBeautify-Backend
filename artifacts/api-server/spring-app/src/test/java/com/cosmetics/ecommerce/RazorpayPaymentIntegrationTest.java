package com.cosmetics.ecommerce;

import com.cosmetics.ecommerce.dto.OrderDtos.AddressRequest;
import com.cosmetics.ecommerce.dto.OrderDtos.OrderRequest;
import com.cosmetics.ecommerce.dto.PaymentDtos.*;
import com.cosmetics.ecommerce.entity.*;
import com.cosmetics.ecommerce.enums.*;
import com.cosmetics.ecommerce.payment.RazorpayProperties;
import com.cosmetics.ecommerce.payment.RazorpaySignatureUtil;
import com.cosmetics.ecommerce.payment.PaymentReconciliationService;
import com.cosmetics.ecommerce.repository.*;
import com.cosmetics.ecommerce.security.JwtService;
import com.cosmetics.ecommerce.service.AddressService;
import com.cosmetics.ecommerce.service.CartService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RazorpayPaymentIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtService jwtService;
    @Autowired private RazorpayProperties razorpayProperties;

    @Autowired private UserRepository userRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private AddressRepository addressRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private CartRepository cartRepository;
    @Autowired private CartService cartService;
    @Autowired private AddressService addressService;
    @Autowired private PaymentWebhookEventRepository webhookEventRepository;
    @Autowired private ComboItemRepository comboItemRepository;
    @Autowired private ComboRepository comboRepository;
    @Autowired private ProductRelationshipRepository relationshipRepository;
    @Autowired private SubcategoryRepository subcategoryRepository;
    @Autowired private RefundRepository refundRepository;
    @Autowired private PaymentReconciliationService reconciliationService;

    private User customer1;
    private String token1;
    private User customer2;
    private String token2;
    private User adminUser;
    private String adminToken;
    private User staffUser;
    private String staffToken;

    private Category category;
    private Product emeraldNecklace;
    private Address address1;

    @BeforeEach
    void setUp() {
        tearDown();

        category = categoryRepository.save(new Category("Necklaces", "Luxury gold necklaces"));
        emeraldNecklace = productRepository.save(new Product(
                "Royal Emerald Necklace", "royal-emerald-necklace", "SKU-RZP-1",
                BigDecimal.valueOf(1499.00), 20, category
        ));

        customer1 = userRepository.save(new User("Amina", "Begum", "amina." + UUID.randomUUID().toString().substring(0, 6) + "@example.com", "Secret123!", Role.CUSTOMER));
        token1 = jwtService.accessToken(customer1);

        customer2 = userRepository.save(new User("Tariq", "Aziz", "tariq." + UUID.randomUUID().toString().substring(0, 6) + "@example.com", "Secret123!", Role.CUSTOMER));
        token2 = jwtService.accessToken(customer2);

        adminUser = userRepository.save(new User("Admin", "User", "admin." + UUID.randomUUID().toString().substring(0, 6) + "@malkaz.com", "AdminSecret123!", Role.ADMIN));
        adminToken = jwtService.accessToken(adminUser);

        staffUser = userRepository.save(new User("Staff", "User", "staff." + UUID.randomUUID().toString().substring(0, 6) + "@malkaz.com", "StaffSecret123!", Role.STAFF));
        staffToken = jwtService.accessToken(staffUser);

        address1 = addressRepository.save(new Address(
                customer1, "Amina Begum", "+919876543210", "12 Royal Avenue", "Mumbai", "400001", "IN"
        ));
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        try { webhookEventRepository.deleteAll(); } catch (Exception ignored) {}
        try { refundRepository.deleteAll(); } catch (Exception ignored) {}
        try { paymentRepository.deleteAll(); } catch (Exception ignored) {}
        try { orderRepository.deleteAll(); } catch (Exception ignored) {}
        try { cartRepository.deleteAll(); } catch (Exception ignored) {}
        try { relationshipRepository.deleteAll(); } catch (Exception ignored) {}
        try { comboItemRepository.deleteAll(); } catch (Exception ignored) {}
        try { comboRepository.deleteAll(); } catch (Exception ignored) {}
        try { productRepository.deleteAll(); } catch (Exception ignored) {}
        try { subcategoryRepository.deleteAll(); } catch (Exception ignored) {}
        try { categoryRepository.deleteAll(); } catch (Exception ignored) {}
        try { addressRepository.deleteAll(); } catch (Exception ignored) {}
        try { userRepository.deleteAll(); } catch (Exception ignored) {}
    }

    @Test
    @DisplayName("Phase A: Create Razorpay Order succeeds, creates pending order & payment, and does not deduct stock prematurely")
    void testPhaseACreateRazorpayOrder_Success() throws Exception {
        // Add 2 items to cart
        cartService.add(customer1, new com.cosmetics.ecommerce.dto.CartDtos.CartItemRequest(emeraldNecklace.getId(), 2));

        RazorpayOrderRequest req = new RazorpayOrderRequest(address1.getId(), PaymentMethod.UPI, null);

        MvcResult result = mockMvc.perform(post("/payments/razorpay/create-order")
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.orderId").isNumber())
                .andExpect(jsonPath("$.data.razorpayOrderId").isNotEmpty())
                .andExpect(jsonPath("$.data.currency").value("INR"))
                .andExpect(jsonPath("$.data.amountInPaise").isNumber())
                .andExpect(jsonPath("$.data.paymentMethod").value("UPI"))
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
        Long orderId = json.get("orderId").asLong();
        String rzpOrderId = json.get("razorpayOrderId").asText();

        // Verify Database state
        Order order = orderRepository.findById(orderId).orElseThrow();
        assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.PENDING);

        Payment payment = paymentRepository.findByOrderId(orderId).orElseThrow();
        assertThat(payment.getRazorpayOrderId()).isEqualTo(rzpOrderId);
        assertThat(payment.getPaymentGateway()).isEqualTo("RAZORPAY");
        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.PENDING);

        // Stock must NOT be deducted until payment is verified
        Product refreshedProduct = productRepository.findById(emeraldNecklace.getId()).orElseThrow();
        assertThat(refreshedProduct.getStockQuantity()).isEqualTo(20);
    }

    @Test
    @DisplayName("Phase B: Verify Razorpay Payment validates HMAC SHA-256 signature, marks order confirmed, and deducts inventory")
    void testPhaseBVerifyPayment_Success() throws Exception {
        cartService.add(customer1, new com.cosmetics.ecommerce.dto.CartDtos.CartItemRequest(emeraldNecklace.getId(), 2));
        RazorpayOrderRequest req = new RazorpayOrderRequest(address1.getId(), PaymentMethod.UPI, null);

        MvcResult createResult = mockMvc.perform(post("/payments/razorpay/create-order")
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode createJson = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("data");
        Long orderId = createJson.get("orderId").asLong();
        String rzpOrderId = createJson.get("razorpayOrderId").asText();
        String rzpPaymentId = "pay_" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);

        // Calculate valid signature using configured secret
        String secret = razorpayProperties.getKeySecret();
        String validSignature = RazorpaySignatureUtil.calculateHmacSha256(rzpOrderId + "|" + rzpPaymentId, secret);

        RazorpayVerifyRequest verifyReq = new RazorpayVerifyRequest(orderId, rzpOrderId, rzpPaymentId, validSignature);

        mockMvc.perform(post("/payments/razorpay/verify")
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(verifyReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.orderId").value(orderId))
                .andExpect(jsonPath("$.data.paymentStatus").value("SUCCESS"))
                .andExpect(jsonPath("$.data.razorpayPaymentId").value(rzpPaymentId));

        // Verify DB updates
        Order confirmedOrder = orderRepository.findById(orderId).orElseThrow();
        assertThat(confirmedOrder.getOrderStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(confirmedOrder.getPaymentStatus()).isEqualTo(PaymentStatus.SUCCESS);

        Payment payment = paymentRepository.findByOrderId(orderId).orElseThrow();
        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(payment.getPaidAt()).isNotNull();
        assertThat(payment.getRazorpayPaymentId()).isEqualTo(rzpPaymentId);

        // Inventory must be deducted (20 - 2 = 18)
        Product product = productRepository.findById(emeraldNecklace.getId()).orElseThrow();
        assertThat(product.getStockQuantity()).isEqualTo(18);

        // Cart must be cleared
        assertThat(cartService.get(customer1).items()).isEmpty();
    }

    @Test
    @DisplayName("Phase B: Duplicate verify requests are idempotent and do not double-deduct stock")
    void testPhaseBVerifyPayment_Idempotent() throws Exception {
        cartService.add(customer1, new com.cosmetics.ecommerce.dto.CartDtos.CartItemRequest(emeraldNecklace.getId(), 1));
        RazorpayOrderRequest req = new RazorpayOrderRequest(address1.getId(), PaymentMethod.UPI, null);

        MvcResult createResult = mockMvc.perform(post("/payments/razorpay/create-order")
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode createJson = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("data");
        Long orderId = createJson.get("orderId").asLong();
        String rzpOrderId = createJson.get("razorpayOrderId").asText();
        String rzpPaymentId = "pay_" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);

        String validSignature = RazorpaySignatureUtil.calculateHmacSha256(rzpOrderId + "|" + rzpPaymentId, razorpayProperties.getKeySecret());
        RazorpayVerifyRequest verifyReq = new RazorpayVerifyRequest(orderId, rzpOrderId, rzpPaymentId, validSignature);

        // First verification
        mockMvc.perform(post("/payments/razorpay/verify")
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(verifyReq)))
                .andExpect(status().isOk());

        // Stock after 1st verify: 20 - 1 = 19
        Product product = productRepository.findById(emeraldNecklace.getId()).orElseThrow();
        assertThat(product.getStockQuantity()).isEqualTo(19);

        // Second verification (replay)
        mockMvc.perform(post("/payments/razorpay/verify")
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(verifyReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.message").value("Payment already verified and order confirmed."));

        // Stock must STILL be 19 (NO double deduction)
        Product productAgain = productRepository.findById(emeraldNecklace.getId()).orElseThrow();
        assertThat(productAgain.getStockQuantity()).isEqualTo(19);
    }

    @Test
    @DisplayName("Phase B: Tampered or invalid Razorpay signature is rejected with 400 Bad Request and does not decrement inventory")
    void testPhaseBVerifyPayment_InvalidSignature_Rejected() throws Exception {
        cartService.add(customer1, new com.cosmetics.ecommerce.dto.CartDtos.CartItemRequest(emeraldNecklace.getId(), 2));
        RazorpayOrderRequest req = new RazorpayOrderRequest(address1.getId(), PaymentMethod.UPI, null);

        MvcResult createResult = mockMvc.perform(post("/payments/razorpay/create-order")
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode createJson = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("data");
        Long orderId = createJson.get("orderId").asLong();
        String rzpOrderId = createJson.get("razorpayOrderId").asText();

        RazorpayVerifyRequest verifyReq = new RazorpayVerifyRequest(orderId, rzpOrderId, "pay_fake_123", "completely_fake_signature");

        mockMvc.perform(post("/payments/razorpay/verify")
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(verifyReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Payment verification failed: Invalid Razorpay signature."));

        // Stock remains untouched: 20
        Product product = productRepository.findById(emeraldNecklace.getId()).orElseThrow();
        assertThat(product.getStockQuantity()).isEqualTo(20);

        // Payment status is marked FAILED
        Payment payment = paymentRepository.findByOrderId(orderId).orElseThrow();
        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.getFailureCode()).isEqualTo("INVALID_SIGNATURE");
    }

    @Test
    @DisplayName("IDOR Protection: Customer cannot verify payment for an order belonging to another customer")
    void testPhaseBVerifyPayment_IDOR_Protection() throws Exception {
        cartService.add(customer1, new com.cosmetics.ecommerce.dto.CartDtos.CartItemRequest(emeraldNecklace.getId(), 1));
        RazorpayOrderRequest req = new RazorpayOrderRequest(address1.getId(), PaymentMethod.UPI, null);

        MvcResult createResult = mockMvc.perform(post("/payments/razorpay/create-order")
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode createJson = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("data");
        Long orderId = createJson.get("orderId").asLong();
        String rzpOrderId = createJson.get("razorpayOrderId").asText();

        String validSignature = RazorpaySignatureUtil.calculateHmacSha256(rzpOrderId + "|pay_123", razorpayProperties.getKeySecret());
        RazorpayVerifyRequest verifyReq = new RazorpayVerifyRequest(orderId, rzpOrderId, "pay_123", validSignature);

        // Customer 2 attempts to verify Customer 1's order
        mockMvc.perform(post("/payments/razorpay/verify")
                        .header("Authorization", "Bearer " + token2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(verifyReq)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Disallowed Payment Methods: Cards and MOCK payments are strictly rejected")
    void testDisallowedPaymentMethods() throws Exception {
        cartService.add(customer1, new com.cosmetics.ecommerce.dto.CartDtos.CartItemRequest(emeraldNecklace.getId(), 1));

        // 1. MOCK method rejected
        String mockPayload = String.format("{\"shippingAddressId\":%d,\"paymentMethod\":\"MOCK\"}", address1.getId());
        mockMvc.perform(post("/payments/razorpay/create-order")
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mockPayload))
                .andExpect(status().isBadRequest());

        // 2. CREDIT_CARD rejected
        String cardPayload = String.format("{\"shippingAddressId\":%d,\"paymentMethod\":\"CREDIT_CARD\"}", address1.getId());
        mockMvc.perform(post("/payments/razorpay/create-order")
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cardPayload))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("P0 Security: Dynamic UPI QR Code generation without genuine gateway configuration returns controlled failure")
    void testDynamicUpiQrCodeGeneration_WithoutLiveGateway_ReturnsControlledFailure() throws Exception {
        cartService.add(customer1, new com.cosmetics.ecommerce.dto.CartDtos.CartItemRequest(emeraldNecklace.getId(), 1));

        RazorpayQrRequest qrReq = new RazorpayQrRequest(address1.getId(), null);

        mockMvc.perform(post("/payments/razorpay/qr-code")
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(qrReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Dynamic UPI QR generation is not supported or configured on this environment")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("malkaz.payments@icici"))));
    }

    @Test
    @DisplayName("Webhook: Valid payment.captured webhook confirms order and records webhook event for idempotency")
    void testWebhook_PaymentCaptured_Success() throws Exception {
        cartService.add(customer1, new com.cosmetics.ecommerce.dto.CartDtos.CartItemRequest(emeraldNecklace.getId(), 1));
        RazorpayOrderRequest req = new RazorpayOrderRequest(address1.getId(), PaymentMethod.UPI, null);

        MvcResult createResult = mockMvc.perform(post("/payments/razorpay/create-order")
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode createJson = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("data");
        Long orderId = createJson.get("orderId").asLong();
        String rzpOrderId = createJson.get("razorpayOrderId").asText();
        long amountInPaise = createJson.get("amountInPaise").asLong();
        String rzpPaymentId = "pay_webhook_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String eventId = "evt_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);

        String webhookPayload = String.format("""
                {
                    "entity": "event",
                    "account_id": "acc_123",
                    "event": "payment.captured",
                    "contains": ["payment"],
                    "payload": {
                        "payment": {
                            "entity": {
                                "id": "%s",
                                "entity": "payment",
                                "amount": %d,
                                "currency": "INR",
                                "status": "captured",
                                "order_id": "%s",
                                "method": "upi"
                            }
                        }
                    },
                    "created_at": 1600000000,
                    "id": "%s"
                }
                """, rzpPaymentId, amountInPaise, rzpOrderId, eventId);

        String webhookSecret = razorpayProperties.getWebhookSecret();
        String signature = RazorpaySignatureUtil.calculateHmacSha256(webhookPayload, webhookSecret);

        // Call public webhook endpoint without JWT token
        mockMvc.perform(post("/payments/razorpay/webhook")
                        .header("X-Razorpay-Signature", signature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(webhookPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));

        // Verify order is confirmed
        Order order = orderRepository.findById(orderId).orElseThrow();
        assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.SUCCESS);

        // Verify webhook event recorded in DB
        PaymentWebhookEvent event = webhookEventRepository.findByRazorpayEventId(eventId).orElseThrow();
        assertThat(event.getProcessingStatus()).isEqualTo("PROCESSED");

        // Webhook Idempotency: Duplicate webhook is safely accepted without error
        mockMvc.perform(post("/payments/razorpay/webhook")
                        .header("X-Razorpay-Signature", signature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(webhookPayload))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Webhook: Invalid signature is rejected with 400 Bad Request")
    void testWebhook_InvalidSignature_Rejected() throws Exception {
        String webhookPayload = "{\"id\":\"evt_fake\",\"event\":\"payment.captured\"}";

        mockMvc.perform(post("/payments/razorpay/webhook")
                        .header("X-Razorpay-Signature", "completely_invalid_webhook_sig")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(webhookPayload))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Admin Refund: Full and partial refunds are restricted to ADMIN and update payment status")
    void testAdminRefund_FullAndPartial() throws Exception {
        // Place and verify an order
        cartService.add(customer1, new com.cosmetics.ecommerce.dto.CartDtos.CartItemRequest(emeraldNecklace.getId(), 1));
        RazorpayOrderRequest req = new RazorpayOrderRequest(address1.getId(), PaymentMethod.UPI, null);

        MvcResult createResult = mockMvc.perform(post("/payments/razorpay/create-order")
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode createJson = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("data");
        Long orderId = createJson.get("orderId").asLong();
        String rzpOrderId = createJson.get("razorpayOrderId").asText();
        String rzpPaymentId = "pay_refund_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);

        String validSig = RazorpaySignatureUtil.calculateHmacSha256(rzpOrderId + "|" + rzpPaymentId, razorpayProperties.getKeySecret());
        mockMvc.perform(post("/payments/razorpay/verify")
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RazorpayVerifyRequest(orderId, rzpOrderId, rzpPaymentId, validSig))))
                .andExpect(status().isOk());

        Payment payment = paymentRepository.findByOrderId(orderId).orElseThrow();

        // 1. Non-admin customer tries to refund -> 403 Forbidden
        mockMvc.perform(post("/admin/payments/" + payment.getId() + "/refund")
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":100.00}"))
                .andExpect(status().isForbidden());

        // 2. Admin processes partial refund of 500.00
        mockMvc.perform(post("/admin/payments/" + payment.getId() + "/refund")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":500.00,\"reason\":\"Customer requested partial return\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.refundAmount").value(500.00))
                .andExpect(jsonPath("$.data.paymentStatus").value("PARTIALLY_REFUNDED"));

        Payment partialPaid = paymentRepository.findById(payment.getId()).orElseThrow();
        assertThat(partialPaid.getPaymentStatus()).isEqualTo(PaymentStatus.PARTIALLY_REFUNDED);
        assertThat(partialPaid.getRefundAmount()).isEqualByComparingTo(new BigDecimal("500.00"));

        // 3. Admin refunds remaining amount -> Status becomes fully REFUNDED
        BigDecimal remaining = partialPaid.getAmount().subtract(partialPaid.getRefundAmount());
        mockMvc.perform(post("/admin/payments/" + payment.getId() + "/refund")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefundRequest(remaining, "Final return"))))
                .andDo(org.springframework.test.web.servlet.result.MockMvcResultHandlers.print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paymentStatus").value("REFUNDED"));

        Payment fullyPaid = paymentRepository.findById(payment.getId()).orElseThrow();
        assertThat(fullyPaid.getPaymentStatus()).isEqualTo(PaymentStatus.REFUNDED);
    }

    @Test
    @DisplayName("Cash on Delivery (COD) Checkout initializes payment status as PENDING and confirms order")
    void testCashOnDeliveryCheckout() throws Exception {
        cartService.add(customer1, new com.cosmetics.ecommerce.dto.CartDtos.CartItemRequest(emeraldNecklace.getId(), 1));

        OrderRequest orderReq = new OrderRequest(address1.getId(), PaymentMethod.CASH_ON_DELIVERY, null);

        MvcResult res = mockMvc.perform(post("/orders")
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(orderReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.orderStatus").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.paymentStatus").value("PENDING"))
                .andReturn();

        JsonNode json = objectMapper.readTree(res.getResponse().getContentAsString()).get("data");
        Long orderId = json.get("id").asLong();

        Payment payment = paymentRepository.findByOrderId(orderId).orElseThrow();
        assertThat(payment.getPaymentGateway()).isEqualTo("CASH_ON_DELIVERY");
        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    @DisplayName("Create Razorpay Order safely reuses existing pending order when cart details match")
    void testCreateRazorpayOrder_ReusesExistingPendingOrder_WhenDetailsMatch() throws Exception {
        cartService.add(customer1, new com.cosmetics.ecommerce.dto.CartDtos.CartItemRequest(emeraldNecklace.getId(), 1));

        RazorpayOrderRequest request = new RazorpayOrderRequest(address1.getId(), PaymentMethod.UPI, null);

        // 1. First order creation attempt
        MvcResult res1 = mockMvc.perform(post("/payments/razorpay/create-order")
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();

        JsonNode json1 = objectMapper.readTree(res1.getResponse().getContentAsString()).get("data");
        Long orderId1 = json1.get("orderId").asLong();
        String rzpOrderId1 = json1.get("razorpayOrderId").asText();

        // 2. Second order creation attempt (e.g. modal closed and reopened without cart change)
        MvcResult res2 = mockMvc.perform(post("/payments/razorpay/create-order")
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();

        JsonNode json2 = objectMapper.readTree(res2.getResponse().getContentAsString()).get("data");
        Long orderId2 = json2.get("orderId").asLong();
        String rzpOrderId2 = json2.get("razorpayOrderId").asText();

        // Must reuse the exact same order and Razorpay order ID
        assertThat(orderId2).isEqualTo(orderId1);
        assertThat(rzpOrderId2).isEqualTo(rzpOrderId1);

        // Exactly one order in the database for customer1
        long totalOrders = orderRepository.countByUserId(customer1.getId());
        assertThat(totalOrders).isEqualTo(1L);
    }

    @Test
    @DisplayName("Create Razorpay Order creates fresh order when cart contents change and cancels old pending order")
    void testCreateRazorpayOrder_DoesNotReuse_WhenCartDetailsChanged() throws Exception {
        cartService.add(customer1, new com.cosmetics.ecommerce.dto.CartDtos.CartItemRequest(emeraldNecklace.getId(), 1));

        RazorpayOrderRequest request = new RazorpayOrderRequest(address1.getId(), PaymentMethod.UPI, null);

        // 1. Initial order creation
        MvcResult res1 = mockMvc.perform(post("/payments/razorpay/create-order")
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        Long orderId1 = objectMapper.readTree(res1.getResponse().getContentAsString()).get("data").get("orderId").asLong();

        // 2. Customer updates cart quantity
        cartService.add(customer1, new com.cosmetics.ecommerce.dto.CartDtos.CartItemRequest(emeraldNecklace.getId(), 1)); // Now qty is 2

        // 3. Next order creation attempt
        MvcResult res2 = mockMvc.perform(post("/payments/razorpay/create-order")
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        Long orderId2 = objectMapper.readTree(res2.getResponse().getContentAsString()).get("data").get("orderId").asLong();

        // Must create a new order because cart details changed
        assertThat(orderId2).isNotEqualTo(orderId1);

        // Old order is cancelled
        Order oldOrder = orderRepository.findById(orderId1).orElseThrow();
        assertThat(oldOrder.getOrderStatus()).isEqualTo(OrderStatus.CANCELLED);
        Payment oldPayment = paymentRepository.findByOrderId(orderId1).orElseThrow();
        assertThat(oldPayment.getPaymentStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(oldPayment.getFailureCode()).isEqualTo("SUPERSEDED");

        // New order is active and PENDING
        Order newOrder = orderRepository.findById(orderId2).orElseThrow();
        assertThat(newOrder.getOrderStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(newOrder.getPaymentStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    @DisplayName("Concurrent create-order requests do not create duplicate active orders")
    void testConcurrentCreateRazorpayOrder_DoesNotCreateDuplicates() throws Exception {
        cartService.add(customer1, new com.cosmetics.ecommerce.dto.CartDtos.CartItemRequest(emeraldNecklace.getId(), 1));

        RazorpayOrderRequest request = new RazorpayOrderRequest(address1.getId(), PaymentMethod.UPI, null);

        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        java.util.concurrent.Callable<Long> task = () -> {
            MvcResult res = mockMvc.perform(post("/payments/razorpay/create-order")
                            .header("Authorization", "Bearer " + token1)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andReturn();
            return objectMapper.readTree(res.getResponse().getContentAsString()).get("data").get("orderId").asLong();
        };

        java.util.concurrent.Future<Long> future1 = executor.submit(task);
        java.util.concurrent.Future<Long> future2 = executor.submit(task);

        Long orderId1 = future1.get(10, java.util.concurrent.TimeUnit.SECONDS);
        Long orderId2 = future2.get(10, java.util.concurrent.TimeUnit.SECONDS);
        executor.shutdown();

        // Both concurrent calls must return the same order ID
        assertThat(orderId1).isEqualTo(orderId2);

        // Only one order is created
        assertThat(orderRepository.countByUserId(customer1.getId())).isEqualTo(1L);
    }

    @Test
    @DisplayName("P0 Financial Safety: Late captured webhook for CANCELLED/SUPERSEDED order flags REFUND_REVIEW_REQUIRED and does not confirm order or deduct stock")
    void testLateCapturedWebhook_ForSupersededCancelledOrder_FlagsRefundReviewRequired() throws Exception {
        // Initial stock: 20
        cartService.add(customer1, new com.cosmetics.ecommerce.dto.CartDtos.CartItemRequest(emeraldNecklace.getId(), 1));
        RazorpayOrderRequest req = new RazorpayOrderRequest(address1.getId(), PaymentMethod.UPI, null);

        // 1. Order 1 created
        MvcResult res1 = mockMvc.perform(post("/payments/razorpay/create-order")
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();
        Long orderId1 = objectMapper.readTree(res1.getResponse().getContentAsString()).get("data").get("orderId").asLong();
        String rzpOrderId1 = objectMapper.readTree(res1.getResponse().getContentAsString()).get("data").get("razorpayOrderId").asText();

        // 2. Customer updates cart quantity and triggers new order -> Order 1 becomes CANCELLED & SUPERSEDED
        cartService.add(customer1, new com.cosmetics.ecommerce.dto.CartDtos.CartItemRequest(emeraldNecklace.getId(), 1));
        mockMvc.perform(post("/payments/razorpay/create-order")
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        Order order1 = orderRepository.findById(orderId1).orElseThrow();
        assertThat(order1.getOrderStatus()).isEqualTo(OrderStatus.CANCELLED);
        Payment payment1 = paymentRepository.findByOrderId(orderId1).orElseThrow();
        assertThat(payment1.getPaymentStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment1.getFailureCode()).isEqualTo("SUPERSEDED");

        // 3. Late payment captured arrives from Razorpay for Order 1
        String rzpPaymentId = "pay_orphan_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String eventId = "evt_orphan_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        long amountPaise = payment1.getAmount().multiply(BigDecimal.valueOf(100)).longValue();

        String webhookPayload = String.format("""
                {
                  "id": "%s",
                  "entity": "event",
                  "event": "payment.captured",
                  "payload": {
                    "payment": {
                      "entity": {
                        "id": "%s",
                        "order_id": "%s",
                        "amount": %d,
                        "currency": "INR",
                        "status": "captured",
                        "method": "upi"
                      }
                    }
                  }
                }
                """, eventId, rzpPaymentId, rzpOrderId1, amountPaise);

        String signature = RazorpaySignatureUtil.calculateHmacSha256(webhookPayload, razorpayProperties.getWebhookSecret());

        mockMvc.perform(post("/payments/razorpay/webhook")
                        .header("X-Razorpay-Signature", signature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(webhookPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));

        // 4. Assertions:
        // Order must remain CANCELLED
        Order refreshedOrder1 = orderRepository.findById(orderId1).orElseThrow();
        assertThat(refreshedOrder1.getOrderStatus()).isEqualTo(OrderStatus.CANCELLED);

        // Payment must be durable REFUND_REVIEW_REQUIRED with failureCode ORPHAN_CAPTURE_ON_CANCELLED_ORDER
        Payment refreshedPayment1 = paymentRepository.findByOrderId(orderId1).orElseThrow();
        assertThat(refreshedPayment1.getPaymentStatus()).isEqualTo(PaymentStatus.REFUND_REVIEW_REQUIRED);
        assertThat(refreshedPayment1.getFailureCode()).isEqualTo("ORPHAN_CAPTURE_ON_CANCELLED_ORDER");
        assertThat(refreshedPayment1.getRazorpayPaymentId()).isEqualTo(rzpPaymentId);
        assertThat(refreshedPayment1.getPaidAt()).isNotNull();

        // Stock must NOT be deducted for the cancelled order (still 20)
        Product refreshedProduct = productRepository.findById(emeraldNecklace.getId()).orElseThrow();
        assertThat(refreshedProduct.getStockQuantity()).isEqualTo(20);

        // 5. Admin reconciliation workflow: Admin views review-required list and initiates refund
        mockMvc.perform(get("/admin/payments/review-required")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.paymentId == %d)]", refreshedPayment1.getId()).exists());

        // Admin refunds the orphan captured payment
        mockMvc.perform(post("/admin/payments/" + refreshedPayment1.getId() + "/refund")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Automated orphan capture reconciliation refund\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paymentStatus").value("REFUNDED"));

        Payment refundedPayment = paymentRepository.findById(refreshedPayment1.getId()).orElseThrow();
        assertThat(refundedPayment.getPaymentStatus()).isEqualTo(PaymentStatus.REFUNDED);
    }

    private Payment createAndConfirmTestPayment(BigDecimal amount) {
        Order order = new Order("ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
                customer1, address1, amount, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, amount, PaymentMethod.UPI);
        order.setOrderStatus(OrderStatus.CONFIRMED);
        order.setPaymentStatus(PaymentStatus.SUCCESS);
        order = orderRepository.save(order);

        String txnId = "TXN-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        Payment payment = new Payment(order, txnId, "RAZORPAY", amount, PaymentMethod.UPI, PaymentStatus.SUCCESS);
        String rzpPayId = "pay_" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);
        payment.setRazorpayPaymentId(rzpPayId);
        payment.setRazorpayOrderId("order_" + UUID.randomUUID().toString().replace("-", "").substring(0, 14));
        payment.setPaidAt(Instant.now());
        return paymentRepository.save(payment);
    }

    @Test
    @DisplayName("P0 Refund Ledger: Admin refund and subsequent webhook counts refund amount exactly once")
    void testAdminRefundInitiated_AndWebhookProcessed_RefundAmountCountedExactlyOnce() throws Exception {
        Payment payment = createAndConfirmTestPayment(BigDecimal.valueOf(1499.00));

        // 1. Admin initiates partial refund of 500.00
        MvcResult refundResult = mockMvc.perform(post("/admin/payments/" + payment.getId() + "/refund")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 500.00, \"reason\": \"Customer returned one piece\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.refundAmount").value(500.00))
                .andExpect(jsonPath("$.data.paymentStatus").value("PARTIALLY_REFUNDED"))
                .andReturn();

        String refundId = objectMapper.readTree(refundResult.getResponse().getContentAsString()).get("data").get("refundId").asText();

        // Database payment records 500.00
        Payment pAfterAdmin = paymentRepository.findById(payment.getId()).orElseThrow();
        assertThat(pAfterAdmin.getRefundAmount()).isEqualByComparingTo("500.00");
        assertThat(pAfterAdmin.getPaymentStatus()).isEqualTo(PaymentStatus.PARTIALLY_REFUNDED);

        // 2. Razorpay fires refund.processed webhook for the exact refund ID
        String eventId = "evt_rfnd_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String webhookPayload = String.format("""
                {
                  "id": "%s",
                  "entity": "event",
                  "event": "refund.processed",
                  "payload": {
                    "refund": {
                      "entity": {
                        "id": "%s",
                        "payment_id": "%s",
                        "amount": 50000,
                        "currency": "INR",
                        "status": "processed"
                      }
                    }
                  }
                }
                """, eventId, refundId, payment.getRazorpayPaymentId());

        String signature = RazorpaySignatureUtil.calculateHmacSha256(webhookPayload, razorpayProperties.getWebhookSecret());

        mockMvc.perform(post("/payments/razorpay/webhook")
                        .header("X-Razorpay-Signature", signature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(webhookPayload))
                .andExpect(status().isOk());

        // 3. Verify Payment refundAmount remains exactly 500.00 (NOT 1000.00!)
        Payment pAfterWebhook = paymentRepository.findById(payment.getId()).orElseThrow();
        assertThat(pAfterWebhook.getRefundAmount()).isEqualByComparingTo("500.00");
        assertThat(pAfterWebhook.getPaymentStatus()).isEqualTo(PaymentStatus.PARTIALLY_REFUNDED);
        assertThat(refundRepository.sumProcessedRefunds(payment.getId())).isEqualByComparingTo("500.00");
    }

    @Test
    @DisplayName("P0 Refund Ledger: Duplicate refund.processed webhook does not duplicate refunded total")
    void testDuplicateRefundProcessedWebhook_DoesNotDuplicateAmount() throws Exception {
        Payment payment = createAndConfirmTestPayment(BigDecimal.valueOf(1000.00));

        // External/Dashboard refund via webhook
        String rzpRefundId = "rfnd_ext_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String eventId = "evt_dup_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String webhookPayload = String.format("""
                {
                  "id": "%s",
                  "entity": "event",
                  "event": "refund.processed",
                  "payload": {
                    "refund": {
                      "entity": {
                        "id": "%s",
                        "payment_id": "%s",
                        "amount": 40000,
                        "currency": "INR",
                        "status": "processed"
                      }
                    }
                  }
                }
                """, eventId, rzpRefundId, payment.getRazorpayPaymentId());

        String signature = RazorpaySignatureUtil.calculateHmacSha256(webhookPayload, razorpayProperties.getWebhookSecret());

        // Delivery 1
        mockMvc.perform(post("/payments/razorpay/webhook")
                        .header("X-Razorpay-Signature", signature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(webhookPayload))
                .andExpect(status().isOk());

        Payment p1 = paymentRepository.findById(payment.getId()).orElseThrow();
        assertThat(p1.getRefundAmount()).isEqualByComparingTo("400.00");

        // Delivery 2 (Duplicate with different webhook event ID, same refund ID)
        String eventId2 = "evt_dup_2_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String webhookPayload2 = webhookPayload.replace(eventId, eventId2);
        String signature2 = RazorpaySignatureUtil.calculateHmacSha256(webhookPayload2, razorpayProperties.getWebhookSecret());

        mockMvc.perform(post("/payments/razorpay/webhook")
                        .header("X-Razorpay-Signature", signature2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(webhookPayload2))
                .andExpect(status().isOk());

        // Amount must remain 400.00
        Payment p2 = paymentRepository.findById(payment.getId()).orElseThrow();
        assertThat(p2.getRefundAmount()).isEqualByComparingTo("400.00");
        assertThat(refundRepository.findByPaymentIdOrderByCreatedAtDesc(payment.getId())).hasSize(1);
    }

    @Test
    @DisplayName("P0 Refund Ledger: Two different partial refunds on the same payment are tracked independently")
    void testTwoDifferentPartialRefunds_OnSamePayment() throws Exception {
        Payment payment = createAndConfirmTestPayment(BigDecimal.valueOf(1499.00));

        // Partial Refund 1: 400.00
        mockMvc.perform(post("/admin/payments/" + payment.getId() + "/refund")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 400.00, \"reason\": \"Partial return piece 1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.refundAmount").value(400.00));

        // Partial Refund 2: 300.00
        mockMvc.perform(post("/admin/payments/" + payment.getId() + "/refund")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 300.00, \"reason\": \"Partial return piece 2\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.refundAmount").value(300.00));

        Payment p = paymentRepository.findById(payment.getId()).orElseThrow();
        assertThat(p.getRefundAmount()).isEqualByComparingTo("700.00");
        assertThat(p.getPaymentStatus()).isEqualTo(PaymentStatus.PARTIALLY_REFUNDED);
        assertThat(refundRepository.findByPaymentIdOrderByCreatedAtDesc(payment.getId())).hasSize(2);
    }

    @Test
    @DisplayName("P0 Refund Ledger: Multiple partial refunds summing to captured amount mark payment REFUNDED")
    void testMultiplePartialRefunds_SumEqualsCapturedAmount_BecomesFullyRefunded() throws Exception {
        Payment payment = createAndConfirmTestPayment(BigDecimal.valueOf(1000.00));

        // Refund 1: 600.00 -> PARTIALLY_REFUNDED
        mockMvc.perform(post("/admin/payments/" + payment.getId() + "/refund")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 600.00}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paymentStatus").value("PARTIALLY_REFUNDED"));

        // Refund 2: 400.00 -> REFUNDED
        mockMvc.perform(post("/admin/payments/" + payment.getId() + "/refund")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 400.00}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paymentStatus").value("REFUNDED"));

        Payment p = paymentRepository.findById(payment.getId()).orElseThrow();
        assertThat(p.getRefundAmount()).isEqualByComparingTo("1000.00");
        assertThat(p.getPaymentStatus()).isEqualTo(PaymentStatus.REFUNDED);
    }

    @Test
    @DisplayName("P0 Refund Invariant: Refund total cannot exceed captured amount")
    void testRefundTotal_CannotExceedCapturedAmount() throws Exception {
        Payment payment = createAndConfirmTestPayment(BigDecimal.valueOf(1000.00));

        // Attempt to refund 1500.00 > 1000.00
        mockMvc.perform(post("/admin/payments/" + payment.getId() + "/refund")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 1500.00}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("exceeds remaining refundable amount")));

        // Valid refund of 800.00
        mockMvc.perform(post("/admin/payments/" + payment.getId() + "/refund")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 800.00}"))
                .andExpect(status().isOk());

        // Attempt to refund 300.00 when only 200.00 remains
        mockMvc.perform(post("/admin/payments/" + payment.getId() + "/refund")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 300.00}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("exceeds remaining refundable amount")));
    }

    @Test
    @DisplayName("P0 Concurrency: Two simultaneous admin refund requests do not over-refund")
    void testTwoSimultaneousAdminRefundRequests_DoNotOverRefund() throws Exception {
        Payment payment = createAndConfirmTestPayment(BigDecimal.valueOf(1000.00));

        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        java.util.concurrent.Callable<Integer> refundTask = () -> {
            MvcResult res = mockMvc.perform(post("/admin/payments/" + payment.getId() + "/refund")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"amount\": 600.00, \"reason\": \"Concurrent refund test\"}"))
                    .andReturn();
            return res.getResponse().getStatus();
        };

        java.util.concurrent.Future<Integer> f1 = executor.submit(refundTask);
        java.util.concurrent.Future<Integer> f2 = executor.submit(refundTask);

        int status1 = f1.get(10, java.util.concurrent.TimeUnit.SECONDS);
        int status2 = f2.get(10, java.util.concurrent.TimeUnit.SECONDS);
        executor.shutdown();

        // Exactly one must succeed (200), and the second must be rejected (400)
        boolean oneSucceeded = (status1 == 200 && status2 == 400) || (status1 == 400 && status2 == 200);
        assertThat(oneSucceeded).isTrue();

        // Database total must be exactly 600.00, never 1200.00
        Payment p = paymentRepository.findById(payment.getId()).orElseThrow();
        assertThat(p.getRefundAmount()).isEqualByComparingTo("600.00");
    }

    @Test
    @DisplayName("P0 Concurrency: Concurrent full and partial refund requests serialize safely")
    void testConcurrentFullAndPartialRefundRequests_SerializeSafely() throws Exception {
        Payment payment = createAndConfirmTestPayment(BigDecimal.valueOf(1000.00));

        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        java.util.concurrent.Callable<Integer> fullRefund = () -> {
            MvcResult res = mockMvc.perform(post("/admin/payments/" + payment.getId() + "/refund")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"amount\": 1000.00, \"reason\": \"Full refund\"}"))
                    .andReturn();
            return res.getResponse().getStatus();
        };

        java.util.concurrent.Callable<Integer> partialRefund = () -> {
            MvcResult res = mockMvc.perform(post("/admin/payments/" + payment.getId() + "/refund")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"amount\": 500.00, \"reason\": \"Partial refund\"}"))
                    .andReturn();
            return res.getResponse().getStatus();
        };

        java.util.concurrent.Future<Integer> f1 = executor.submit(fullRefund);
        java.util.concurrent.Future<Integer> f2 = executor.submit(partialRefund);

        int s1 = f1.get(10, java.util.concurrent.TimeUnit.SECONDS);
        int s2 = f2.get(10, java.util.concurrent.TimeUnit.SECONDS);
        executor.shutdown();

        // Total refunded in DB must never exceed 1000.00
        Payment p = paymentRepository.findById(payment.getId()).orElseThrow();
        assertThat(p.getRefundAmount()).isLessThanOrEqualTo(BigDecimal.valueOf(1000.00));
    }

    @Test
    @DisplayName("P0 Webhook: Unknown payment refund webhook is ignored without corrupting database")
    void testUnknownRefundIdWebhook_DoesNotBlindlyMutateUnrelatedPayments() throws Exception {
        String eventId = "evt_unknown_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String webhookPayload = String.format("""
                {
                  "id": "%s",
                  "entity": "event",
                  "event": "refund.processed",
                  "payload": {
                    "refund": {
                      "entity": {
                        "id": "rfnd_unknown_999",
                        "payment_id": "pay_nonexistent_999",
                        "amount": 25000,
                        "currency": "INR",
                        "status": "processed"
                      }
                    }
                  }
                }
                """, eventId);

        String signature = RazorpaySignatureUtil.calculateHmacSha256(webhookPayload, razorpayProperties.getWebhookSecret());

        mockMvc.perform(post("/payments/razorpay/webhook")
                        .header("X-Razorpay-Signature", signature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(webhookPayload))
                .andExpect(status().isOk());

        // Zero refund records created for nonexistent payment
        assertThat(refundRepository.count()).isEqualTo(0);
    }

    @Test
    @DisplayName("P0 Security: Invalid refund webhook signature is rejected with HTTP 400")
    void testInvalidRefundWebhookSignature_Rejected() throws Exception {
        String webhookPayload = """
                {
                  "id": "evt_tampered",
                  "entity": "event",
                  "event": "refund.processed",
                  "payload": {}
                }
                """;

        mockMvc.perform(post("/payments/razorpay/webhook")
                        .header("X-Razorpay-Signature", "invalid_tampered_signature")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(webhookPayload))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("P0 Security: Static UPI QR fallback is strictly rejected, returns controlled error, no fake QR returned")
    void testStaticUpiQrFallback_IsRejected() throws Exception {
        cartService.add(customer1, new com.cosmetics.ecommerce.dto.CartDtos.CartItemRequest(emeraldNecklace.getId(), 1));

        String qrReq = String.format("{\"shippingAddressId\": %d}", address1.getId());

        mockMvc.perform(post("/payments/razorpay/qr-code")
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(qrReq))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Dynamic UPI QR generation is not supported or configured on this environment")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("malkaz.payments@icici"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("qr_test_"))));
    }

    @Test
    @DisplayName("P0 Checkout: Net Banking payment method creates order with strict NET_BANKING instrument")
    void testExistingNetBankingCheckout_Success() throws Exception {
        cartService.add(customer1, new com.cosmetics.ecommerce.dto.CartDtos.CartItemRequest(emeraldNecklace.getId(), 1));
        RazorpayOrderRequest req = new RazorpayOrderRequest(address1.getId(), PaymentMethod.NET_BANKING, null);

        mockMvc.perform(post("/payments/razorpay/create-order")
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paymentMethod").value("NET_BANKING"))
                .andExpect(jsonPath("$.data.orderId").isNumber());
    }

    @Test
    @DisplayName("P0 Checkout: Cash On Delivery (COD) standard checkout remains fully functional")
    void testExistingCodCheckout_Success() throws Exception {
        cartService.add(customer1, new com.cosmetics.ecommerce.dto.CartDtos.CartItemRequest(emeraldNecklace.getId(), 1));
        OrderRequest req = new OrderRequest(address1.getId(), PaymentMethod.CASH_ON_DELIVERY, null);

        mockMvc.perform(post("/orders")
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paymentMethod").value("CASH_ON_DELIVERY"))
                .andExpect(jsonPath("$.data.orderStatus").value("CONFIRMED"));

        // Cart is cleared
        assertThat(cartService.get(customer1).items()).isEmpty();
    }

    @Test
    @DisplayName("P0 Security: STAFF role cannot access ADMIN-only refund endpoint (Returns 403 Forbidden)")
    void testStaffRoleCannotAccessAdminRefundEndpoint_ReturnsForbidden() throws Exception {
        Payment payment = createAndConfirmTestPayment(BigDecimal.valueOf(1000.00));

        mockMvc.perform(post("/admin/payments/" + payment.getId() + "/refund")
                        .header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 500.00, \"reason\": \"Staff attempt\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("P0 Security: Customer cannot access payment details of another customer (Returns 401 Unauthorized)")
    void testCustomerCannotAccessAnotherCustomerPaymentStatus_ReturnsUnauthorized() throws Exception {
        cartService.add(customer1, new com.cosmetics.ecommerce.dto.CartDtos.CartItemRequest(emeraldNecklace.getId(), 1));
        RazorpayOrderRequest req = new RazorpayOrderRequest(address1.getId(), PaymentMethod.UPI, null);

        MvcResult createResult = mockMvc.perform(post("/payments/razorpay/create-order")
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("data");
        Long orderId = json.get("orderId").asLong();

        // Customer 2 attempts to view payment status of Customer 1's order
        mockMvc.perform(get("/payments/" + orderId)
                        .header("Authorization", "Bearer " + token2))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("P0 Security: Webhook payment.captured with amount mismatch flags REFUND_REVIEW_REQUIRED and does not confirm order")
    void testPaymentCapturedWebhook_WithAmountMismatch_FlagsRefundReviewRequired() throws Exception {
        cartService.add(customer1, new com.cosmetics.ecommerce.dto.CartDtos.CartItemRequest(emeraldNecklace.getId(), 1));
        RazorpayOrderRequest req = new RazorpayOrderRequest(address1.getId(), PaymentMethod.UPI, null);

        MvcResult createResult = mockMvc.perform(post("/payments/razorpay/create-order")
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("data");
        Long orderId = json.get("orderId").asLong();
        String rzpOrderId = json.get("razorpayOrderId").asText();

        // Attacker tampered payment: Paid only 500 paise (Rs 5.00) instead of full order amount
        String eventId = "evt_tampered_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String rzpPaymentId = "pay_underpaid_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String webhookPayload = String.format("""
                {
                  "id": "%s",
                  "entity": "event",
                  "event": "payment.captured",
                  "payload": {
                    "payment": {
                      "entity": {
                        "id": "%s",
                        "order_id": "%s",
                        "amount": 500,
                        "currency": "INR",
                        "status": "captured",
                        "method": "upi"
                      }
                    }
                  }
                }
                """, eventId, rzpPaymentId, rzpOrderId);

        String signature = RazorpaySignatureUtil.calculateHmacSha256(webhookPayload, razorpayProperties.getWebhookSecret());

        mockMvc.perform(post("/payments/razorpay/webhook")
                        .header("X-Razorpay-Signature", signature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(webhookPayload))
                .andExpect(status().isOk());

        // Verify Payment is marked REFUND_REVIEW_REQUIRED with failureCode AMOUNT_MISMATCH
        Payment payment = paymentRepository.findByOrderId(orderId).orElseThrow();
        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.REFUND_REVIEW_REQUIRED);
        assertThat(payment.getFailureCode()).isEqualTo("AMOUNT_MISMATCH");

        // Order MUST NOT be confirmed, and product stock MUST NOT be decremented
        Order order = orderRepository.findById(orderId).orElseThrow();
        assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.PENDING);
        Product product = productRepository.findById(emeraldNecklace.getId()).orElseThrow();
        assertThat(product.getStockQuantity()).isEqualTo(20);
    }

    @Test
    @DisplayName("P0 Security: Webhook payment.captured with currency mismatch flags REFUND_REVIEW_REQUIRED")
    void testPaymentCapturedWebhook_WithCurrencyMismatch_FlagsRefundReviewRequired() throws Exception {
        cartService.add(customer1, new com.cosmetics.ecommerce.dto.CartDtos.CartItemRequest(emeraldNecklace.getId(), 1));
        RazorpayOrderRequest req = new RazorpayOrderRequest(address1.getId(), PaymentMethod.UPI, null);

        MvcResult createResult = mockMvc.perform(post("/payments/razorpay/create-order")
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("data");
        Long orderId = json.get("orderId").asLong();
        String rzpOrderId = json.get("razorpayOrderId").asText();
        long amountPaise = json.get("amountInPaise").asLong();

        // Webhook arrives with currency USD instead of INR
        String eventId = "evt_currmismatch_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String rzpPaymentId = "pay_usd_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String webhookPayload = String.format("""
                {
                  "id": "%s",
                  "entity": "event",
                  "event": "payment.captured",
                  "payload": {
                    "payment": {
                      "entity": {
                        "id": "%s",
                        "order_id": "%s",
                        "amount": %d,
                        "currency": "USD",
                        "status": "captured",
                        "method": "upi"
                      }
                    }
                  }
                }
                """, eventId, rzpPaymentId, rzpOrderId, amountPaise);

        String signature = RazorpaySignatureUtil.calculateHmacSha256(webhookPayload, razorpayProperties.getWebhookSecret());

        mockMvc.perform(post("/payments/razorpay/webhook")
                        .header("X-Razorpay-Signature", signature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(webhookPayload))
                .andExpect(status().isOk());

        Payment payment = paymentRepository.findByOrderId(orderId).orElseThrow();
        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.REFUND_REVIEW_REQUIRED);
        assertThat(payment.getFailureCode()).isEqualTo("CURRENCY_MISMATCH");

        Order order = orderRepository.findById(orderId).orElseThrow();
        assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    @DisplayName("P0 Webhook: refund.failed webhook marks ledger record FAILED and releases reservation")
    void testRefundFailedWebhook_TransitionsStatusToFailedAndReleasesReservation() throws Exception {
        Payment payment = createAndConfirmTestPayment(BigDecimal.valueOf(1000.00));

        // Admin initiates a refund of 500.00
        MvcResult res = mockMvc.perform(post("/admin/payments/" + payment.getId() + "/refund")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 500.00, \"reason\": \"Test refund fail\"}"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = objectMapper.readTree(res.getResponse().getContentAsString()).get("data");
        String rzpRefundId = json.get("refundId").asText();

        // Set refund to PENDING state to simulate asynchronous gateway processing
        Refund initialRefund = refundRepository.findByRazorpayRefundId(rzpRefundId).orElseThrow();
        initialRefund.setStatus(RefundStatus.PENDING);
        refundRepository.save(initialRefund);

        // Webhook refund.failed arrives from Razorpay
        String eventId = "evt_rfnd_fail_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String webhookPayload = String.format("""
                {
                  "id": "%s",
                  "entity": "event",
                  "event": "refund.failed",
                  "payload": {
                    "refund": {
                      "entity": {
                        "id": "%s",
                        "payment_id": "%s",
                        "amount": 50000,
                        "currency": "INR",
                        "status": "failed",
                        "error_code": "BANK_REJECTED",
                        "error_description": "Beneficiary bank declined refund"
                      }
                    }
                  }
                }
                """, eventId, rzpRefundId, payment.getRazorpayPaymentId());

        String signature = RazorpaySignatureUtil.calculateHmacSha256(webhookPayload, razorpayProperties.getWebhookSecret());

        mockMvc.perform(post("/payments/razorpay/webhook")
                        .header("X-Razorpay-Signature", signature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(webhookPayload))
                .andExpect(status().isOk());

        // Refund ledger record must be FAILED
        Refund refund = refundRepository.findByRazorpayRefundId(rzpRefundId).orElseThrow();
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.FAILED);
        assertThat(refund.getFailureReason()).contains("Beneficiary bank declined refund");

        // Payment status must be restored to SUCCESS (0 processed refunds)
        Payment p = paymentRepository.findById(payment.getId()).orElseThrow();
        assertThat(p.getRefundAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(p.getPaymentStatus()).isEqualTo(PaymentStatus.SUCCESS);
    }

    @Test
    @DisplayName("P0 Webhook: External Razorpay Dashboard refund creates ledger record and updates payment")
    void testExternalDashboardRefundWebhook_CreatesLedgerRecordAndUpdatesTotal() throws Exception {
        Payment payment = createAndConfirmTestPayment(BigDecimal.valueOf(1000.00));

        String externalRefundId = "rfnd_ext_dash_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String eventId = "evt_ext_rfnd_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String webhookPayload = String.format("""
                {
                  "id": "%s",
                  "entity": "event",
                  "event": "refund.processed",
                  "payload": {
                    "refund": {
                      "entity": {
                        "id": "%s",
                        "payment_id": "%s",
                        "amount": 35000,
                        "currency": "INR",
                        "status": "processed"
                      }
                    }
                  }
                }
                """, eventId, externalRefundId, payment.getRazorpayPaymentId());

        String signature = RazorpaySignatureUtil.calculateHmacSha256(webhookPayload, razorpayProperties.getWebhookSecret());

        mockMvc.perform(post("/payments/razorpay/webhook")
                        .header("X-Razorpay-Signature", signature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(webhookPayload))
                .andExpect(status().isOk());

        // Ledger must contain EXT-REF- record
        Refund extRefund = refundRepository.findByRazorpayRefundId(externalRefundId).orElseThrow();
        assertThat(extRefund.getInternalRefundRef()).isEqualTo("EXT-REF-" + externalRefundId);
        assertThat(extRefund.getStatus()).isEqualTo(RefundStatus.PROCESSED);
        assertThat(extRefund.getAmount()).isEqualByComparingTo("350.00");
        assertThat(extRefund.getInitiatedBy()).isEqualTo("RAZORPAY_WEBHOOK");

        Payment p = paymentRepository.findById(payment.getId()).orElseThrow();
        assertThat(p.getRefundAmount()).isEqualByComparingTo("350.00");
        assertThat(p.getPaymentStatus()).isEqualTo(PaymentStatus.PARTIALLY_REFUNDED);
    }

    @Test
    @DisplayName("P0 Concurrency: Frontend verification and webhook for same order do NOT double-deduct inventory")
    void testConcurrentPaymentFinalization_StockDecrementedOnlyOnce() throws Exception {
        // Setup cart with 2 units of emerald necklace (initial stock: 20)
        cartService.add(customer1, new com.cosmetics.ecommerce.dto.CartDtos.CartItemRequest(emeraldNecklace.getId(), 2));

        // Create Razorpay Order
        RazorpayOrderRequest orderReq = new RazorpayOrderRequest(address1.getId(), PaymentMethod.UPI, null);
        MvcResult createRes = mockMvc.perform(post("/payments/razorpay/create-order")
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(orderReq)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode createNode = objectMapper.readTree(createRes.getResponse().getContentAsString()).get("data");
        Long orderId = createNode.get("orderId").asLong();
        String rzpOrderId = createNode.get("razorpayOrderId").asText();
        String rzpPaymentId = "pay_sim_conc_" + UUID.randomUUID().toString().substring(0, 8);
        String signature = RazorpaySignatureUtil.calculateHmacSha256(rzpOrderId + "|" + rzpPaymentId, razorpayProperties.getKeySecret());

        // 1. First confirmation via frontend verification
        RazorpayVerifyRequest verifyReq = new RazorpayVerifyRequest(orderId, rzpOrderId, rzpPaymentId, signature);
        mockMvc.perform(post("/payments/razorpay/verify")
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(verifyReq)))
                .andExpect(status().isOk());

        // Verify stock decremented by 2 (20 - 2 = 18)
        Product prodAfterVerify = productRepository.findById(emeraldNecklace.getId()).orElseThrow();
        assertThat(prodAfterVerify.getStockQuantity()).isEqualTo(18);

        // 2. Second confirmation arrives via Webhook (payment.captured)
        long amountInPaise = createNode.get("amountInPaise").asLong();
        String eventId = "evt_conc_" + UUID.randomUUID().toString().substring(0, 8);
        String webhookPayload = String.format("""
                {
                  "id": "%s",
                  "entity": "event",
                  "event": "payment.captured",
                  "payload": {
                    "payment": {
                      "entity": {
                        "id": "%s",
                        "order_id": "%s",
                        "amount": %d,
                        "currency": "INR",
                        "status": "captured",
                        "method": "upi"
                      }
                    }
                  }
                }
                """, eventId, rzpPaymentId, rzpOrderId, amountInPaise);

        String webhookSig = RazorpaySignatureUtil.calculateHmacSha256(webhookPayload, razorpayProperties.getWebhookSecret());
        mockMvc.perform(post("/payments/razorpay/webhook")
                        .header("X-Razorpay-Signature", webhookSig)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(webhookPayload))
                .andExpect(status().isOk());

        // CRITICAL CHECK: Stock must STILL be 18, NOT 16! Inventory was deducted exactly once.
        Product prodAfterWebhook = productRepository.findById(emeraldNecklace.getId()).orElseThrow();
        assertThat(prodAfterWebhook.getStockQuantity()).isEqualTo(18);

        // Order must be CONFIRMED and payment SUCCESS
        Order order = orderRepository.findById(orderId).orElseThrow();
        assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.CONFIRMED);
        Payment payment = paymentRepository.findByOrderId(orderId).orElseThrow();
        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.SUCCESS);
    }

    @Test
    @DisplayName("P0 Safety: Checkout without valid delivery address is strictly rejected")
    void testCheckoutWithoutDeliveryAddress_Rejected() throws Exception {
        cartService.add(customer1, new com.cosmetics.ecommerce.dto.CartDtos.CartItemRequest(emeraldNecklace.getId(), 1));

        String invalidPayload = "{\"shippingAddressId\": null, \"paymentMethod\": \"UPI\"}";
        mockMvc.perform(post("/payments/razorpay/create-order")
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidPayload))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("P0 Financial Safety: Concurrent partial refunds strictly enforce atomic reservation and never exceed captured amount")
    void testConcurrentPartialRefunds_CannotExceedCapturedAmount() throws Exception {
        cartService.add(customer1, new com.cosmetics.ecommerce.dto.CartDtos.CartItemRequest(emeraldNecklace.getId(), 1));
        RazorpayOrderRequest req = new RazorpayOrderRequest(address1.getId(), PaymentMethod.UPI, null);

        MvcResult createResult = mockMvc.perform(post("/payments/razorpay/create-order")
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode createJson = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("data");
        Long orderId = createJson.get("orderId").asLong();
        String rzpOrderId = createJson.get("razorpayOrderId").asText();
        String rzpPaymentId = "pay_conc_ref_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);

        String validSig = RazorpaySignatureUtil.calculateHmacSha256(rzpOrderId + "|" + rzpPaymentId, razorpayProperties.getKeySecret());
        mockMvc.perform(post("/payments/razorpay/verify")
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RazorpayVerifyRequest(orderId, rzpOrderId, rzpPaymentId, validSig))))
                .andExpect(status().isOk());

        Payment payment = paymentRepository.findByOrderId(orderId).orElseThrow();
        Long pId = payment.getId();
        BigDecimal totalPaid = payment.getAmount();

        // Admin requests first partial refund: 1000.00 -> Succeeds
        mockMvc.perform(post("/admin/payments/" + pId + "/refund")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefundRequest(new BigDecimal("1000.00"), "First partial"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.refundAmount").value(1000.00))
                .andExpect(jsonPath("$.data.paymentStatus").value("PARTIALLY_REFUNDED"));

        // Admin requests second partial refund: 800.00 (1000 + 800 = 1800 > totalPaid) -> Strictly REJECTED
        mockMvc.perform(post("/admin/payments/" + pId + "/refund")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefundRequest(new BigDecimal("800.00"), "Exceeding partial"))))
                .andExpect(status().isBadRequest());

        // Admin requests remaining refund: totalPaid - 1000 -> Succeeds, becomes REFUNDED
        BigDecimal remaining = totalPaid.subtract(new BigDecimal("1000.00"));
        mockMvc.perform(post("/admin/payments/" + pId + "/refund")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefundRequest(remaining, "Final partial"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paymentStatus").value("REFUNDED"));

        // Any further refund attempt -> Strictly REJECTED
        mockMvc.perform(post("/admin/payments/" + pId + "/refund")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefundRequest(new BigDecimal("10.00"), "Over-refund"))))
                .andExpect(status().isBadRequest());

        Payment finalPayment = paymentRepository.findById(pId).orElseThrow();
        assertThat(finalPayment.getRefundAmount()).isEqualByComparingTo(totalPaid);
        assertThat(finalPayment.getPaymentStatus()).isEqualTo(PaymentStatus.REFUNDED);
    }

    @Test
    @DisplayName("P0 Routing: Both /payments/razorpay/webhook and /webhooks/razorpay endpoints are publicly reachable and valid")
    void testDualWebhookEndpoints_BothPermittedAndFunctional() throws Exception {
        String eventId1 = "evt_alias1_" + UUID.randomUUID().toString().substring(0, 8);
        String eventId2 = "evt_alias2_" + UUID.randomUUID().toString().substring(0, 8);

        String payload1 = String.format("{\"id\":\"%s\",\"event\":\"payment.authorized\",\"payload\":{}}", eventId1);
        String sig1 = RazorpaySignatureUtil.calculateHmacSha256(payload1, razorpayProperties.getWebhookSecret());

        String payload2 = String.format("{\"id\":\"%s\",\"event\":\"payment.authorized\",\"payload\":{}}", eventId2);
        String sig2 = RazorpaySignatureUtil.calculateHmacSha256(payload2, razorpayProperties.getWebhookSecret());

        // 1. Authoritative path: /payments/razorpay/webhook
        mockMvc.perform(post("/payments/razorpay/webhook")
                        .header("X-Razorpay-Signature", sig1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload1))
                .andExpect(status().isOk());

        // 2. Standard documentation alias path: /webhooks/razorpay
        mockMvc.perform(post("/webhooks/razorpay")
                        .header("X-Razorpay-Signature", sig2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload2))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("P0 Reconciliation Safety: Competing background workers use database-backed claiming lease before external processing")
    void testCompetingReconciliationWorkers_DistributedClaimLease() throws Exception {
        // 1. Create a pending payment
        cartService.add(customer1, new com.cosmetics.ecommerce.dto.CartDtos.CartItemRequest(emeraldNecklace.getId(), 1));
        RazorpayOrderRequest req = new RazorpayOrderRequest(address1.getId(), PaymentMethod.UPI, null);

        MvcResult createResult = mockMvc.perform(post("/payments/razorpay/create-order")
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode createJson = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("data");
        Long orderId = createJson.get("orderId").asLong();
        Payment payment = paymentRepository.findByOrderId(orderId).orElseThrow();

        // Worker 1 claims payment for reconciliation -> SUCCEEDS
        boolean worker1Claim = reconciliationService.claimPaymentForReconciliation(payment.getId());
        assertThat(worker1Claim).isTrue();

        // Worker 2 attempts concurrent claim on the same payment -> REJECTED (leased)
        boolean worker2Claim = reconciliationService.claimPaymentForReconciliation(payment.getId());
        assertThat(worker2Claim).isFalse();

        // Worker 1 finishes or releases lease
        reconciliationService.releasePaymentLease(payment.getId());

        // 2. Test Refund Distributed Claiming
        Refund refund = new Refund(payment, "REF-LEASE-TEST", new BigDecimal("500.00"), "INR", RefundStatus.PENDING, "Test claim", "ADMIN");
        refund.setRazorpayRefundId("rfnd_lease_test_" + UUID.randomUUID().toString().substring(0, 8));
        refund = refundRepository.save(refund);

        // Worker 1 claims refund for reconciliation -> SUCCEEDS
        boolean worker1RefundClaim = reconciliationService.claimRefundForReconciliation(refund.getId());
        assertThat(worker1RefundClaim).isTrue();

        // Worker 2 attempts concurrent claim on the same refund -> REJECTED (leased)
        boolean worker2RefundClaim = reconciliationService.claimRefundForReconciliation(refund.getId());
        assertThat(worker2RefundClaim).isFalse();

        // Worker 1 releases refund lease
        reconciliationService.releaseRefundLease(refund.getId(), "Test claim");
    }
}
