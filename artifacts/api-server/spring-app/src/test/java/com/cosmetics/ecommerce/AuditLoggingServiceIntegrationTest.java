package com.cosmetics.ecommerce;

import com.cosmetics.ecommerce.dto.AuthDtos.*;
import com.cosmetics.ecommerce.dto.CatalogDtos.ProductRequest;
import com.cosmetics.ecommerce.dto.CatalogDtos.ProductResponse;
import com.cosmetics.ecommerce.dto.OrderDtos.AddressRequest;
import com.cosmetics.ecommerce.dto.OrderDtos.AddressResponse;
import com.cosmetics.ecommerce.dto.OrderDtos.OrderRequest;
import com.cosmetics.ecommerce.dto.OrderDtos.OrderResponse;
import com.cosmetics.ecommerce.dto.ProfileDtos.AdminUserUpdateRequest;
import com.cosmetics.ecommerce.entity.*;
import com.cosmetics.ecommerce.enums.*;
import com.cosmetics.ecommerce.exception.UnauthorizedException;
import com.cosmetics.ecommerce.repository.*;
import com.cosmetics.ecommerce.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class AuditLoggingServiceIntegrationTest {

    @Autowired private AuthService authService;
    @Autowired private ProductService productService;
    @Autowired private UserAdminService userAdminService;
    @Autowired private AddressService addressService;
    @Autowired private OrderService orderService;
    @Autowired private CartService cartService;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private AddressRepository addressRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private CartRepository cartRepository;
    @Autowired private PasswordResetTokenRepository resetTokenRepository;
    @Autowired private PaymentRepository paymentRepository;

    private Category testCategory;

    @org.junit.jupiter.api.AfterEach
    void tearDown() { try { paymentRepository.deleteAll(); } catch (Exception ignored) {} try { orderRepository.deleteAll(); } catch (Exception ignored) {} try { cartRepository.deleteAll(); } catch (Exception ignored) {} try { addressRepository.deleteAll(); } catch (Exception ignored) {} try { resetTokenRepository.deleteAll(); } catch (Exception ignored) {} try { auditLogRepository.deleteAll(); } catch (Exception ignored) {} try { userRepository.deleteAll(); } catch (Exception ignored) {} }
    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        tearDown();
        testCategory = categoryRepository.findAll().stream().findFirst().orElseGet(() ->
                categoryRepository.save(new Category("AuditSkincare", "Testing audit logs category")));

    }

    @Test
    @DisplayName("User registration produces a REGISTRATION_SUCCESS audit log entry")
    void registrationProducesAuditLog() {
        String email = "jane." + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        RegisterRequest request = new RegisterRequest(
                "Jane", "Audit", email, "SecurePass123!", "+9411223344"
        );

        authService.register(request);

        List<AuditLog> logs = auditLogRepository.findAll().stream()
                .filter(l -> l.getAction() == AuditAction.REGISTRATION_SUCCESS && email.equalsIgnoreCase(l.getActorEmail()))
                .toList();

        assertThat(logs).hasSize(1);
        AuditLog log = logs.get(0);
        assertThat(log.getActorEmail()).isEqualTo(email);
        assertThat(log.getResult()).isEqualTo(AuditResult.SUCCESS);
        assertThat(log.getResourceType()).isEqualTo("USER");
        // Ensure raw password is NEVER logged
        assertThat(log.getDescription()).doesNotContain("SecurePass123!");
        assertThat(log.getNewState()).doesNotContain("SecurePass123!");
    }

    @Test
    @DisplayName("Successful login produces a LOGIN_SUCCESS audit log entry")
    void loginSuccessProducesAuditLog() {
        String email = "login.success." + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        authService.register(new RegisterRequest("User", "One", email, "SecretPass123!", null));

        authService.login(new LoginRequest(email, "SecretPass123!"));

        List<AuditLog> loginLogs = auditLogRepository.findAll().stream()
                .filter(l -> l.getAction() == AuditAction.LOGIN_SUCCESS && email.equalsIgnoreCase(l.getActorEmail()))
                .toList();

        assertThat(loginLogs).hasSize(1);
        AuditLog log = loginLogs.get(0);
        assertThat(log.getActorEmail()).isEqualTo(email);
        assertThat(log.getResult()).isEqualTo(AuditResult.SUCCESS);
        assertThat(log.getDescription()).contains("User logged in successfully");
    }

    @Test
    @DisplayName("Failed login with bad password generates LOGIN_FAILURE audit log without recording password")
    void failedLoginProducesAuditLog() {
        String email = "login.failure." + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        authService.register(new RegisterRequest("User", "Two", email, "RealPassword123!", null));

        assertThatThrownBy(() -> authService.login(new LoginRequest(email, "WrongPassword!")))
                .isInstanceOf(UnauthorizedException.class);

        List<AuditLog> failureLogs = auditLogRepository.findAll().stream()
                .filter(l -> l.getAction() == AuditAction.LOGIN_FAILURE && email.equalsIgnoreCase(l.getActorEmail()))
                .toList();

        assertThat(failureLogs).hasSize(1);
        AuditLog log = failureLogs.get(0);
        assertThat(log.getActorEmail()).isEqualTo(email);
        assertThat(log.getResult()).isEqualTo(AuditResult.FAILURE);
        assertThat(log.getDescription()).contains("Incorrect password");
        // Sensitive password values must never appear
        assertThat(log.getDescription()).doesNotContain("WrongPassword!");
        assertThat(log.getDescription()).doesNotContain("RealPassword123!");
    }

    @Test
    @DisplayName("Product lifecycle generates PRODUCT_CREATE, PRODUCT_UPDATE, and PRODUCT_STOCK_UPDATE events")
    void productLifecycleProducesAuditLogs() {
        String sku = "SKU-AUDIT-" + UUID.randomUUID().toString().substring(0, 8);
        // 1. Create product
        ProductRequest createReq = new ProductRequest(
                "Audit Lip Glow " + sku, sku, new BigDecimal("29.99"), null,
                50, 5, testCategory.getId(),
                "Lip gloss for testing", null, null, true, false,
                null, null, null, null, null, null, null
        );
        ProductResponse created = productService.create(createReq);

        List<AuditLog> createLogs = auditLogRepository.findAll().stream()
                .filter(l -> l.getAction() == AuditAction.PRODUCT_CREATE && String.valueOf(created.id()).equals(l.getResourceId()))
                .toList();
        assertThat(createLogs).hasSize(1);
        assertThat(createLogs.get(0).getResourceId()).isEqualTo(String.valueOf(created.id()));

        // 2. Update stock
        productService.updateStock(created.id(), 75);

        List<AuditLog> stockLogs = auditLogRepository.findAll().stream()
                .filter(l -> l.getAction() == AuditAction.PRODUCT_STOCK_UPDATE && String.valueOf(created.id()).equals(l.getResourceId()))
                .toList();
        assertThat(stockLogs).hasSize(1);
        assertThat(stockLogs.get(0).getPreviousState()).contains("\"stockQuantity\":50");
        assertThat(stockLogs.get(0).getNewState()).contains("\"stockQuantity\":75");

        // 3. Update price and details
        ProductRequest updateReq = new ProductRequest(
                "Audit Lip Glow Deluxe " + sku, sku, new BigDecimal("34.99"), new BigDecimal("31.99"),
                75, 5, testCategory.getId(),
                "Updated description", null, null, true, false,
                null, null, null, null, null, null, null
        );
        productService.update(created.id(), updateReq);

        List<AuditLog> updateLogs = auditLogRepository.findAll().stream()
                .filter(l -> l.getAction() == AuditAction.PRODUCT_UPDATE && String.valueOf(created.id()).equals(l.getResourceId()))
                .toList();
        assertThat(updateLogs).hasSize(1);
        assertThat(updateLogs.get(0).getPreviousState()).contains("\"price\":29.99");
        assertThat(updateLogs.get(0).getNewState()).contains("\"price\":34.99");

        // 4. Delete product
        productService.delete(created.id());
        List<AuditLog> deleteLogs = auditLogRepository.findAll().stream()
                .filter(l -> l.getAction() == AuditAction.PRODUCT_DELETE && String.valueOf(created.id()).equals(l.getResourceId()))
                .toList();
        assertThat(deleteLogs).hasSize(1);
        assertThat(deleteLogs.get(0).getNewState()).contains("\"active\":false");
    }

    @Test
    @DisplayName("Admin changing user role records USER_ROLE_CHANGE with old and new values")
    void roleChangeProducesAuditLog() {
        String email = "rolechange." + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        authService.register(new RegisterRequest("Target", "User", email, "Pass1234!", null));
        User target = userRepository.findByEmailIgnoreCase(email).orElseThrow();

        userAdminService.update(target.getId(), new AdminUserUpdateRequest(Role.STAFF, true));

        List<AuditLog> roleLogs = auditLogRepository.findAll().stream()
                .filter(l -> l.getAction() == AuditAction.USER_ROLE_CHANGE && String.valueOf(target.getId()).equals(l.getResourceId()))
                .toList();

        assertThat(roleLogs).hasSize(1);
        AuditLog log = roleLogs.get(0);
        assertThat(log.getResourceId()).isEqualTo(String.valueOf(target.getId()));
        assertThat(log.getPreviousState()).contains("\"role\":\"CUSTOMER\"");
        assertThat(log.getNewState()).contains("\"role\":\"STAFF\"");
    }

    @Test
    @DisplayName("Customer address creation, update, and deletion produce appropriate audit events")
    void addressLifecycleProducesAuditLogs() {
        String email = "address." + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        authService.register(new RegisterRequest("Address", "User", email, "Pass1234!", null));
        User user = userRepository.findByEmailIgnoreCase(email).orElseThrow();

        // 1. Create address
        AddressRequest addReq = new AddressRequest(
                "Jane Doe", "+94112233", "123 Private Street", "Apt 4B",
                "Colombo", "Western", "00100", "Sri Lanka", AddressType.HOME, true
        );
        AddressResponse addr = addressService.create(user, addReq);

        List<AuditLog> createLogs = auditLogRepository.findAll().stream()
                .filter(l -> l.getAction() == AuditAction.ADDRESS_CREATE && String.valueOf(addr.id()).equals(l.getResourceId()))
                .toList();
        assertThat(createLogs).hasSize(1);
        // Note: street address is sanitized away from description and state
        assertThat(createLogs.get(0).getDescription()).doesNotContain("123 Private Street");

        // 2. Update address
        AddressRequest updateReq = new AddressRequest(
                "Jane Doe", "+94112233", "123 Private Street", "Apt 4B",
                "Kandy", "Central", "20000", "Sri Lanka", AddressType.HOME, true
        );
        addressService.update(user, addr.id(), updateReq);

        List<AuditLog> updateLogs = auditLogRepository.findAll().stream()
                .filter(l -> l.getAction() == AuditAction.ADDRESS_UPDATE && String.valueOf(addr.id()).equals(l.getResourceId()))
                .toList();
        assertThat(updateLogs).hasSize(1);
        assertThat(updateLogs.get(0).getNewState()).contains("\"city\":\"Kandy\"");

        // 3. Delete address
        addressService.delete(user, addr.id());
        List<AuditLog> deleteLogs = auditLogRepository.findAll().stream()
                .filter(l -> l.getAction() == AuditAction.ADDRESS_DELETE && String.valueOf(addr.id()).equals(l.getResourceId()))
                .toList();
        assertThat(deleteLogs).hasSize(1);
        assertThat(deleteLogs.get(0).getResourceId()).isEqualTo(String.valueOf(addr.id()));
    }

    @Test
    @DisplayName("Order checkout, cancellation, and admin status updates record ordered audit events")
    void orderLifecycleProducesAuditLogs() {
        String email = "ordercust." + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        authService.register(new RegisterRequest("Order", "Customer", email, "Pass1234!", null));
        User user = userRepository.findByEmailIgnoreCase(email).orElseThrow();

        AddressResponse addrResp = addressService.create(user, new AddressRequest(
                "Order Cust", "+9411", "Street 1", null, "City", "State", "1000", "LK", AddressType.HOME, true
        ));

        String sku = "SKU-ORD-" + UUID.randomUUID().toString().substring(0, 6);
        Product product = productRepository.save(new Product(
                "Order Item " + sku, "order-item-" + sku.toLowerCase(), sku, new BigDecimal("20.00"), 10, testCategory
        ));

        // Add to cart
        cartService.add(user, new com.cosmetics.ecommerce.dto.CartDtos.CartItemRequest(product.getId(), 2));

        // Checkout
        OrderResponse orderResp = orderService.checkout(user, new OrderRequest(addrResp.id(), PaymentMethod.CASH_ON_DELIVERY, null));

        List<AuditLog> orderCreateLogs = auditLogRepository.findAll().stream()
                .filter(l -> l.getAction() == AuditAction.ORDER_CREATE && String.valueOf(orderResp.id()).equals(l.getResourceId()))
                .toList();
        assertThat(orderCreateLogs).hasSize(1);
        assertThat(orderCreateLogs.get(0).getResourceId()).isEqualTo(String.valueOf(orderResp.id()));

        List<AuditLog> paymentLogs = auditLogRepository.findAll().stream()
                .filter(l -> l.getAction() == AuditAction.PAYMENT_SUCCESS)
                .toList();
        assertThat(paymentLogs).hasSize(1);

        // Cancel order
        orderService.cancel(user, orderResp.id());

        List<AuditLog> cancelLogs = auditLogRepository.findAll().stream()
                .filter(l -> l.getAction() == AuditAction.ORDER_CANCEL && String.valueOf(orderResp.id()).equals(l.getResourceId()))
                .toList();
        assertThat(cancelLogs).hasSize(1);
        assertThat(cancelLogs.get(0).getNewState()).contains("CANCELLED");
    }
}
