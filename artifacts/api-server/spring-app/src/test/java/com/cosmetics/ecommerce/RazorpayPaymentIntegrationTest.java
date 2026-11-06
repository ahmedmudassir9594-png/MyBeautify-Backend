package com.cosmetics.ecommerce;

import com.cosmetics.ecommerce.dto.OrderDtos.AddressRequest;
import com.cosmetics.ecommerce.dto.OrderDtos.OrderRequest;
import com.cosmetics.ecommerce.dto.PaymentDtos.*;
import com.cosmetics.ecommerce.entity.*;
import com.cosmetics.ecommerce.enums.*;
import com.cosmetics.ecommerce.payment.RazorpayProperties;
import com.cosmetics.ecommerce.payment.RazorpaySignatureUtil;
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

    private User customer1;
    private String token1;
    private User customer2;
    private String token2;
    private User adminUser;
    private String adminToken;

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

        address1 = addressRepository.save(new Address(
                customer1, "Amina Begum", "+919876543210", "12 Royal Avenue", "Mumbai", "400001", "IN"
        ));
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        try { webhookEventRepository.deleteAll(); } catch (Exception ignored) {}
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
    @DisplayName("Dynamic UPI QR Code generation creates order and returns QR payload")
    void testDynamicUpiQrCodeGeneration() throws Exception {
        cartService.add(customer1, new com.cosmetics.ecommerce.dto.CartDtos.CartItemRequest(emeraldNecklace.getId(), 1));

        RazorpayQrRequest qrReq = new RazorpayQrRequest(address1.getId(), null);

        mockMvc.perform(post("/payments/razorpay/qr-code")
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(qrReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.qrCodeId").isNotEmpty())
                .andExpect(jsonPath("$.data.paymentUrl").isNotEmpty())
                .andExpect(jsonPath("$.data.qrImageUrl").isNotEmpty())
                .andExpect(jsonPath("$.data.status").value("active"));
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
                                "amount": 149900,
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
                """, rzpPaymentId, rzpOrderId, eventId);

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
}
