package com.cosmetics.ecommerce.service;

import com.cosmetics.ecommerce.dto.CartDtos.*;
import com.cosmetics.ecommerce.entity.*;
import com.cosmetics.ecommerce.exception.*;
import com.cosmetics.ecommerce.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CartService {
    private final CartRepository carts;
    private final CartItemRepository items;
    private final ProductRepository products;

    public CartService(CartRepository carts, CartItemRepository items, ProductRepository products) {
        this.carts = carts;
        this.items = items;
        this.products = products;
    }

    @Transactional
    public CartResponse get(User user) {
        return CartResponse.from(cart(user));
    }

    @Transactional
    public Cart entity(User user) {
        return cart(user);
    }

    @Transactional
    public CartResponse add(User user, CartItemRequest request) {
        Cart cart = cart(user);
        Product product = products.findById(request.productId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found."));

        int currentQtyInCart = cart.getItems().stream()
                .filter(existing -> existing.getProduct().getId().equals(product.getId()))
                .mapToInt(CartItem::getQuantity)
                .sum();
        validateQuantity(product, currentQtyInCart + request.quantity());

        // Only merge with standalone (non-combo) cart items
        CartItem item = cart.getItems().stream()
                .filter(existing -> existing.getComboGroupId() == null && existing.getProduct().getId().equals(product.getId()))
                .findFirst()
                .orElse(null);

        if (item == null) {
            item = new CartItem(cart, product, request.quantity());
            cart.getItems().add(item);
        } else {
            item.setQuantity(item.getQuantity() + request.quantity());
        }

        cart.touch();
        return CartResponse.from(carts.save(cart));
    }

    @Transactional
    public CartResponse update(User user, Long itemId, int quantity) {
        CartItem item = items.findByIdAndCartUserId(itemId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Cart item not found."));

        if (item.getComboGroupId() != null) {
            throw new BadRequestException("Individual combo items cannot be updated directly. Please manage the combo set.");
        }

        validateQuantity(item.getProduct(), quantity);
        item.setQuantity(quantity);
        item.getCart().touch();
        return CartResponse.from(item.getCart());
    }

    @Transactional
    public void remove(User user, Long itemId) {
        CartItem item = items.findByIdAndCartUserId(itemId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Cart item not found."));
        Cart cart = item.getCart();

        if (item.getComboGroupId() != null) {
            String groupId = item.getComboGroupId();
            List<CartItem> groupItems = cart.getItems().stream()
                    .filter(i -> groupId.equals(i.getComboGroupId()))
                    .toList();
            cart.getItems().removeAll(groupItems);
            items.deleteAll(groupItems);
        } else {
            cart.getItems().remove(item);
            items.delete(item);
        }
        cart.touch();
    }

    @Transactional
    public CartResponse removeCombo(User user, String comboGroupId) {
        Cart cart = cart(user);
        List<CartItem> groupItems = cart.getItems().stream()
                .filter(i -> comboGroupId.equals(i.getComboGroupId()))
                .toList();
        if (groupItems.isEmpty()) {
            throw new ResourceNotFoundException("Combo not found in cart.");
        }
        cart.getItems().removeAll(groupItems);
        items.deleteAll(groupItems);
        cart.touch();
        return CartResponse.from(carts.save(cart));
    }

    @Transactional
    public void clear(User user) {
        Cart cart = cart(user);
        cart.getItems().clear();
        cart.touch();
    }

    private Cart cart(User user) {
        return carts.findByUserId(user.getId()).orElseGet(() -> carts.save(new Cart(user)));
    }

    private void validateQuantity(Product p, int quantity) {
        if (!p.isActive()) throw new BadRequestException("This product is no longer available.");
        if (quantity < 1) throw new BadRequestException("Quantity must be at least 1.");
        if (quantity > p.getStockQuantity()) throw new InsufficientStockException("Only " + p.getStockQuantity() + " units are available.");
    }
}
