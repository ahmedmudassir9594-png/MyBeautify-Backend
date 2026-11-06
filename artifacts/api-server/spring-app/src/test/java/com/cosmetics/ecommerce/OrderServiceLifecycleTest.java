package com.cosmetics.ecommerce;

import com.cosmetics.ecommerce.dto.OrderDtos.OrderRequest;
import com.cosmetics.ecommerce.dto.OrderDtos.OrderResponse;
import com.cosmetics.ecommerce.entity.*;
import com.cosmetics.ecommerce.enums.OrderStatus;
import com.cosmetics.ecommerce.enums.PaymentMethod;
import com.cosmetics.ecommerce.enums.Role;
import com.cosmetics.ecommerce.exception.BadRequestException;
import com.cosmetics.ecommerce.exception.OrderCancellationException;
import com.cosmetics.ecommerce.exception.ResourceNotFoundException;
import com.cosmetics.ecommerce.payment.PaymentGateway;
import com.cosmetics.ecommerce.payment.PaymentGateway.PaymentResult;
import com.cosmetics.ecommerce.repository.*;
import com.cosmetics.ecommerce.service.CartService;
import com.cosmetics.ecommerce.service.OrderService;
import com.cosmetics.ecommerce.email.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceLifecycleTest {

    @Mock private OrderRepository orders;
    @Mock private CartService cartService;
    @Mock private AddressRepository addresses;
    @Mock private CouponRepository coupons;
    @Mock private PaymentRepository payments;
    @Mock private PaymentGateway paymentGateway;
    @Mock private ProductRepository products;
    @Mock private EmailService emailService;
    @Mock private com.cosmetics.ecommerce.service.ReferralService referralService;

    @InjectMocks
    private OrderService orderService;

    private User user1;
    private User user2;
    private Product product;
    private Category category;

    @BeforeEach
    void setUp() {
        user1 = new User("Maya", "Perera", "maya@example.com", "hash", Role.CUSTOMER);
        ReflectionTestUtils.setField(user1, "id", 10L);

        user2 = new User("John", "Doe", "john@example.com", "hash", Role.CUSTOMER);
        ReflectionTestUtils.setField(user2, "id", 20L);

        category = new Category("Skincare", "Care");
        product = new Product("Lipstick", "lipstick", "SKU-LIP", new BigDecimal("25.00"), 10, category);
        ReflectionTestUtils.setField(product, "id", 100L);
    }

    @Test
    @DisplayName("Checkout calculates total using live database product prices, not client values")
    void checkoutUsesLiveProductPrices() {
        Cart cart = new Cart(user1);
        CartItem cartItem = new CartItem(cart, product, 2);
        cart.getItems().add(cartItem);

        Address address = new Address(user1, "Maya Perera", "+9411", "123 Main St", "Colombo", "00100", "LK");
        ReflectionTestUtils.setField(address, "id", 5L);

        when(cartService.entity(user1)).thenReturn(cart);
        when(addresses.findByIdAndUserId(5L, 10L)).thenReturn(Optional.of(address));
        when(paymentGateway.charge(any(BigDecimal.class), any(PaymentMethod.class)))
                .thenReturn(new PaymentResult("TXN-123", "CASH_ON_DELIVERY", true));
        when(orders.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderRequest request = new OrderRequest(5L, PaymentMethod.CASH_ON_DELIVERY, null);
        OrderResponse response = orderService.checkout(user1, request);

        assertThat(response).isNotNull();
        // Product price is 25.00 x 2 = 50.00 subtotal
        assertThat(response.subtotal()).isEqualByComparingTo(new BigDecimal("50.00"));
        // Stock quantity should be deducted from 10 to 8
        assertThat(product.getStockQuantity()).isEqualTo(8);
        verify(emailService).sendOrderConfirmation(eq("maya@example.com"), anyString(), any(BigDecimal.class));
    }

    @Test
    @DisplayName("Checkout fails and preserves stock when payment gateway charge is unsuccessful")
    void checkoutFailsWhenPaymentUnsuccessful() {
        Cart cart = new Cart(user1);
        CartItem cartItem = new CartItem(cart, product, 2);
        cart.getItems().add(cartItem);

        Address address = new Address(user1, "Maya Perera", "+9411", "123 Main St", "Colombo", "00100", "LK");
        ReflectionTestUtils.setField(address, "id", 5L);

        when(cartService.entity(user1)).thenReturn(cart);
        when(addresses.findByIdAndUserId(5L, 10L)).thenReturn(Optional.of(address));
        when(paymentGateway.charge(any(BigDecimal.class), any(PaymentMethod.class)))
                .thenReturn(new PaymentResult(null, "CASH_ON_DELIVERY", false));

        OrderRequest request = new OrderRequest(5L, PaymentMethod.CASH_ON_DELIVERY, null);

        assertThatThrownBy(() -> orderService.checkout(user1, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Payment could not be completed");

        // Stock quantity must NOT be deducted
        assertThat(product.getStockQuantity()).isEqualTo(10);
        verify(orders, never()).save(any());
    }

    @Test
    @DisplayName("Customer cancellation restores product inventory stock")
    void customerCancellationRestoresStock() {
        Address address = new Address(user1, "Maya", "+9411", "123 Main", "Colombo", "00100", "LK");
        Order order = new Order("COS-2026-TEST1", user1, address, new BigDecimal("50.00"), BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("50.00"), PaymentMethod.CASH_ON_DELIVERY);
        order.setOrderStatus(OrderStatus.CONFIRMED);
        order.getItems().add(new OrderItem(order, product, 3, new BigDecimal("25.00")));
        product.setStockQuantity(7); // After ordering 3 units

        when(orders.findByIdAndUserId(1L, 10L)).thenReturn(Optional.of(order));

        OrderResponse cancelled = orderService.cancel(user1, 1L);

        assertThat(cancelled.orderStatus()).isEqualTo(OrderStatus.CANCELLED);
        // Stock must be restored: 7 + 3 = 10
        assertThat(product.getStockQuantity()).isEqualTo(10);
    }

    @Test
    @DisplayName("Customer cannot cancel an order belonging to another customer (IDOR Protection)")
    void customerCannotCancelAnotherCustomersOrder() {
        // user1 tries to cancel user2's order
        when(orders.findByIdAndUserId(99L, 10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.cancel(user1, 99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Order not found.");
    }

    @Test
    @DisplayName("Customer cannot cancel an already DELIVERED order")
    void customerCannotCancelDeliveredOrder() {
        Address address = new Address(user1, "Maya", "+9411", "123 Main", "Colombo", "00100", "LK");
        Order order = new Order("COS-2026-TEST2", user1, address, new BigDecimal("50.00"), BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("50.00"), PaymentMethod.CASH_ON_DELIVERY);
        order.setOrderStatus(OrderStatus.DELIVERED);

        when(orders.findByIdAndUserId(2L, 10L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancel(user1, 2L))
                .isInstanceOf(OrderCancellationException.class)
                .hasMessage("This order can no longer be cancelled.");
    }

    @Test
    @DisplayName("Admin status transition to CANCELLED restores product inventory stock")
    void adminStatusTransitionToCancelledRestoresStock() {
        Address address = new Address(user1, "Maya", "+9411", "123 Main", "Colombo", "00100", "LK");
        Order order = new Order("COS-2026-TEST3", user1, address, new BigDecimal("50.00"), BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("50.00"), PaymentMethod.CASH_ON_DELIVERY);
        order.setOrderStatus(OrderStatus.CONFIRMED);
        order.getItems().add(new OrderItem(order, product, 4, new BigDecimal("25.00")));
        product.setStockQuantity(6);

        when(orders.findById(3L)).thenReturn(Optional.of(order));

        OrderResponse result = orderService.updateStatus(3L, OrderStatus.CANCELLED);

        assertThat(result.orderStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(product.getStockQuantity()).isEqualTo(10);
    }

    @Test
    @DisplayName("Invalid status transition is rejected with BadRequestException")
    void invalidStatusTransitionIsRejected() {
        Address address = new Address(user1, "Maya", "+9411", "123 Main", "Colombo", "00100", "LK");
        Order order = new Order("COS-2026-TEST4", user1, address, new BigDecimal("50.00"), BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("50.00"), PaymentMethod.CASH_ON_DELIVERY);
        order.setOrderStatus(OrderStatus.CONFIRMED);

        when(orders.findById(4L)).thenReturn(Optional.of(order));

        // Cannot skip directly from CONFIRMED to DELIVERED
        assertThatThrownBy(() -> orderService.updateStatus(4L, OrderStatus.DELIVERED))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Invalid order status transition.");
    }
}
