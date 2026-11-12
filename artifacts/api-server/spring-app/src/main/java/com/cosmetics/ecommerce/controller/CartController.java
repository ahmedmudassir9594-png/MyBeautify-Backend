package com.cosmetics.ecommerce.controller;

import com.cosmetics.ecommerce.dto.*;
import com.cosmetics.ecommerce.dto.CartDtos.*;
import com.cosmetics.ecommerce.entity.User;
import com.cosmetics.ecommerce.service.CartService;
import com.cosmetics.ecommerce.service.ComboService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/cart")
public class CartController {
    private final CartService cart;
    private final ComboService comboService;

    @Autowired
    public CartController(CartService cart, @Autowired(required = false) ComboService comboService) {
        this.cart = cart;
        this.comboService = comboService;
    }

    public CartController(CartService cart) {
        this(cart, null);
    }

    @GetMapping public ApiResponse<CartResponse> get(@AuthenticationPrincipal User user) { return ApiResponse.ok("Cart fetched successfully.", cart.get(user)); }
    @PostMapping("/items") public ApiResponse<CartResponse> add(@AuthenticationPrincipal User user, @Valid @RequestBody CartItemRequest request) { return ApiResponse.ok("Item added to cart.", cart.add(user, request)); }
    @PutMapping("/items/{itemId}") public ApiResponse<CartResponse> update(@AuthenticationPrincipal User user, @PathVariable Long itemId, @RequestParam @Min(1) int quantity) { return ApiResponse.ok("Cart updated successfully.", cart.update(user, itemId, quantity)); }
    @DeleteMapping("/items/{itemId}") public ApiResponse<Void> remove(@AuthenticationPrincipal User user, @PathVariable Long itemId) { cart.remove(user, itemId); return ApiResponse.ok("Item removed from cart.", null); }
    @DeleteMapping("/clear") public ApiResponse<Void> clear(@AuthenticationPrincipal User user) { cart.clear(user); return ApiResponse.ok("Cart cleared successfully.", null); }

    @DeleteMapping("/combos/{comboGroupId}")
    public ApiResponse<CartResponse> removeComboGroup(@AuthenticationPrincipal User user, @PathVariable String comboGroupId) {
        if (comboService == null) {
            throw new IllegalStateException("Combo service is not available.");
        }
        return ApiResponse.ok("Combo removed from cart successfully.", comboService.removeComboGroupFromCart(user, comboGroupId));
    }
}
