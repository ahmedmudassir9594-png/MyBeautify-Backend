package com.cosmetics.ecommerce.entity;

import com.cosmetics.ecommerce.enums.ComboType;
import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "cart_items", indexes = {
        @Index(name = "idx_cart_items_cart", columnList = "cart_id"),
        @Index(name = "idx_cart_items_combo_group", columnList = "combo_group_id")
})
public class CartItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cart_id", nullable = false)
    private Cart cart;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "combo_id")
    private Combo combo;

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

    protected CartItem() {}

    public CartItem(Cart cart, Product product, int quantity) {
        this.cart = cart;
        this.product = product;
        this.quantity = quantity;
        this.price = product != null ? product.sellingPrice() : BigDecimal.ZERO;
        this.originalPrice = this.price;
        this.discountAmount = BigDecimal.ZERO;
    }

    public CartItem(Cart cart, Product product, int quantity, BigDecimal price,
                    Combo combo, String comboGroupId, ComboType comboType,
                    String comboName, BigDecimal originalPrice, BigDecimal discountAmount) {
        this.cart = cart;
        this.product = product;
        this.quantity = quantity;
        this.price = price;
        this.combo = combo;
        this.comboGroupId = comboGroupId;
        this.comboType = comboType;
        this.comboName = comboName;
        this.originalPrice = originalPrice;
        this.discountAmount = discountAmount;
    }

    public Long getId() {
        return id;
    }

    public Cart getCart() {
        return cart;
    }

    public void setCart(Cart cart) {
        this.cart = cart;
    }

    public Product getProduct() {
        return product;
    }

    public void setProduct(Product product) {
        this.product = product;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
        if (this.comboGroupId == null && this.product != null) {
            this.price = product.sellingPrice();
            this.originalPrice = product.sellingPrice();
        }
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public Combo getCombo() {
        return combo;
    }

    public void setCombo(Combo combo) {
        this.combo = combo;
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

    public BigDecimal subtotal() {
        return price.multiply(BigDecimal.valueOf(quantity));
    }
}
