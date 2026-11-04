package com.cosmetics.ecommerce.entity;

import com.cosmetics.ecommerce.enums.ComboType;
import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "order_items", indexes = {
        @Index(name = "idx_order_items_order", columnList = "order_id"),
        @Index(name = "idx_order_items_combo_group", columnList = "combo_group_id")
})
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false)
    private String productName;

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "combo_id")
    private Long comboId;

    @Column(name = "combo_group_id", length = 64)
    private String comboGroupId;

    @Enumerated(EnumType.STRING)
    @Column(name = "combo_type", length = 32)
    private ComboType comboType;

    @Column(name = "combo_name", length = 180)
    private String comboName;

    @Column(name = "original_price", precision = 12, scale = 2)
    private BigDecimal originalPrice;

    @Column(name = "discount_amount", precision = 12, scale = 2)
    private BigDecimal discountAmount;

    protected OrderItem() {}

    public OrderItem(Order order, Product product, int quantity, BigDecimal price) {
        this.order = order;
        this.product = product;
        this.productName = product != null ? product.getName() : "";
        this.quantity = quantity;
        this.price = price;
        this.subtotal = price.multiply(BigDecimal.valueOf(quantity));
        this.originalPrice = price;
        this.discountAmount = BigDecimal.ZERO;
    }

    public OrderItem(Order order, Product product, int quantity, BigDecimal price,
                     Long comboId, String comboGroupId, ComboType comboType,
                     String comboName, BigDecimal originalPrice, BigDecimal discountAmount) {
        this.order = order;
        this.product = product;
        this.productName = product != null ? product.getName() : "";
        this.quantity = quantity;
        this.price = price;
        this.subtotal = price.multiply(BigDecimal.valueOf(quantity));
        this.comboId = comboId;
        this.comboGroupId = comboGroupId;
        this.comboType = comboType;
        this.comboName = comboName;
        this.originalPrice = originalPrice;
        this.discountAmount = discountAmount;
    }

    public Long getId() {
        return id;
    }

    public Order getOrder() {
        return order;
    }

    public void setOrder(Order order) {
        this.order = order;
    }

    public Product getProduct() {
        return product;
    }

    public void setProduct(Product product) {
        this.product = product;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
        this.subtotal = this.price != null ? this.price.multiply(BigDecimal.valueOf(quantity)) : BigDecimal.ZERO;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
        this.subtotal = price != null ? price.multiply(BigDecimal.valueOf(quantity)) : BigDecimal.ZERO;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public Long getComboId() {
        return comboId;
    }

    public void setComboId(Long comboId) {
        this.comboId = comboId;
    }

    public String getComboGroupId() {
        return comboGroupId;
    }

    public void setComboGroupId(String comboGroupId) {
        this.comboGroupId = comboGroupId;
    }

    public ComboType getComboType() {
        return comboType;
    }

    public void setComboType(ComboType comboType) {
        this.comboType = comboType;
    }

    public String getComboName() {
        return comboName;
    }

    public void setComboName(String comboName) {
        this.comboName = comboName;
    }

    public BigDecimal getOriginalPrice() {
        return originalPrice;
    }

    public void setOriginalPrice(BigDecimal originalPrice) {
        this.originalPrice = originalPrice;
    }

    public BigDecimal getDiscountAmount() {
        return discountAmount;
    }

    public void setDiscountAmount(BigDecimal discountAmount) {
        this.discountAmount = discountAmount;
    }
}
