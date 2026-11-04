package com.cosmetics.ecommerce.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "combo_items", indexes = {
        @Index(name = "idx_combo_items_combo", columnList = "combo_id"),
        @Index(name = "idx_combo_items_product", columnList = "product_id")
})
public class ComboItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "combo_id", nullable = false)
    private Combo combo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false)
    private int quantity = 1;

    @Column(precision = 12, scale = 2)
    private BigDecimal customPrice;

    protected ComboItem() {}

    public ComboItem(Combo combo, Product product, int quantity) {
        this.combo = combo;
        this.product = product;
        this.quantity = quantity;
    }

    public ComboItem(Combo combo, Product product, int quantity, BigDecimal customPrice) {
        this.combo = combo;
        this.product = product;
        this.quantity = quantity;
        this.customPrice = customPrice;
    }

    public Long getId() {
        return id;
    }

    public Combo getCombo() {
        return combo;
    }

    public void setCombo(Combo combo) {
        this.combo = combo;
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
    }

    public BigDecimal getCustomPrice() {
        return customPrice;
    }

    public void setCustomPrice(BigDecimal customPrice) {
        this.customPrice = customPrice;
    }

    public BigDecimal effectivePrice() {
        if (customPrice != null) {
            return customPrice;
        }
        return product != null ? product.sellingPrice() : BigDecimal.ZERO;
    }

    public BigDecimal subtotal() {
        return effectivePrice().multiply(BigDecimal.valueOf(quantity));
    }
}

