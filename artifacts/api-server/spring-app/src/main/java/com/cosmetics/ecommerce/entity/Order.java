package com.cosmetics.ecommerce.entity;

import com.cosmetics.ecommerce.enums.*;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders", indexes = {
        @Index(name = "idx_order_number", columnList = "orderNumber", unique = true),
        @Index(name = "idx_order_user", columnList = "user_id")
})
public class Order {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, unique = true, length = 40) private String orderNumber;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private User user;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private Address shippingAddress;
    @Column(nullable = false, precision = 12, scale = 2) private BigDecimal subtotal;
    @Column(nullable = false, precision = 12, scale = 2) private BigDecimal discountAmount = BigDecimal.ZERO;
    @Column(nullable = false, precision = 12, scale = 2) private BigDecimal shippingCharge = BigDecimal.ZERO;
    @Column(nullable = false, precision = 12, scale = 2) private BigDecimal taxAmount = BigDecimal.ZERO;
    @Column(nullable = false, precision = 12, scale = 2) private BigDecimal totalAmount;
    @ManyToOne(fetch = FetchType.LAZY) private Coupon coupon;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private PaymentStatus paymentStatus = PaymentStatus.PENDING;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private OrderStatus orderStatus = OrderStatus.PENDING;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private PaymentMethod paymentMethod;
    @CreationTimestamp @Column(nullable = false, updatable = false) private Instant createdAt;
    @UpdateTimestamp private Instant updatedAt;
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();
    protected Order() {}
    public Order(String orderNumber, User user, Address shippingAddress, BigDecimal subtotal, BigDecimal discountAmount,
                 BigDecimal shippingCharge, BigDecimal taxAmount, BigDecimal totalAmount, PaymentMethod paymentMethod) {
        this.orderNumber = orderNumber; this.user = user; this.shippingAddress = shippingAddress; this.subtotal = subtotal;
        this.discountAmount = discountAmount; this.shippingCharge = shippingCharge; this.taxAmount = taxAmount;
        this.totalAmount = totalAmount; this.paymentMethod = paymentMethod;
    }
    public Long getId() { return id; } public String getOrderNumber() { return orderNumber; } public User getUser() { return user; }
    public Address getShippingAddress() { return shippingAddress; } public BigDecimal getSubtotal() { return subtotal; }
    public BigDecimal getDiscountAmount() { return discountAmount; } public BigDecimal getShippingCharge() { return shippingCharge; }
    public BigDecimal getTaxAmount() { return taxAmount; } public BigDecimal getTotalAmount() { return totalAmount; }
    public Coupon getCoupon() { return coupon; } public PaymentStatus getPaymentStatus() { return paymentStatus; }
    public OrderStatus getOrderStatus() { return orderStatus; } public PaymentMethod getPaymentMethod() { return paymentMethod; }
    public Instant getCreatedAt() { return createdAt; } public Instant getUpdatedAt() { return updatedAt; } public List<OrderItem> getItems() { return items; }
    public void setCoupon(Coupon coupon) { this.coupon = coupon; }
    public void setPaymentStatus(PaymentStatus value) { paymentStatus = value; }
    public void setOrderStatus(OrderStatus value) { orderStatus = value; }
    public void setPaymentMethod(PaymentMethod value) { paymentMethod = value; }
}