package com.cosmetics.ecommerce.controller;

import com.cosmetics.ecommerce.dto.ApiResponse;
import com.cosmetics.ecommerce.dto.CartDtos.CartResponse;
import com.cosmetics.ecommerce.dto.CatalogDtos.ProductResponse;
import com.cosmetics.ecommerce.dto.ComboDtos.*;
import com.cosmetics.ecommerce.dto.PageResponse;
import com.cosmetics.ecommerce.entity.User;
import com.cosmetics.ecommerce.service.ComboService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/combos")
public class ComboController {

    private final ComboService comboService;

    public ComboController(ComboService comboService) {
        this.comboService = comboService;
    }

    // --- 1. Predefined Combos (Public Listings) ---

    @GetMapping
    public ApiResponse<PageResponse<ComboResponse>> list(@PageableDefault(size = 12) Pageable pageable) {
        return ApiResponse.ok("Combos fetched successfully.", comboService.getActiveCombos(pageable));
    }

    @GetMapping("/featured")
    public ApiResponse<List<ComboResponse>> featured() {
        return ApiResponse.ok("Featured combos fetched successfully.", comboService.getFeaturedCombos());
    }

    @GetMapping("/id/{id}")
    public ApiResponse<ComboResponse> byId(@PathVariable Long id) {
        return ApiResponse.ok("Combo fetched successfully.", comboService.getComboById(id));
    }

    // --- 2. Build Your Own Combo (Public Endpoints - Must stay above dynamic routes) ---

    // Dono URL match honge: "/combos/custom/products" aur "/combos/products"
    @GetMapping({"/custom/products", "/products"})
    public ApiResponse<PageResponse<ProductResponse>> eligibleProducts(@PageableDefault(size = 24) Pageable pageable) {
        return ApiResponse.ok("Custom combo eligible products fetched successfully.",
                comboService.getCustomComboEligibleProducts(pageable));
    }

    @PostMapping("/custom/preview")
    public ApiResponse<CustomComboPreviewResponse> previewCustomCombo(
            @Valid @RequestBody CustomComboPreviewRequest request) {
        return ApiResponse.ok("Custom combo preview calculated successfully.",
                comboService.previewCustomCombo(request));
    }

    // --- 3. Dynamic Slug Route (Hamesha static routes ke neeche) ---

    @GetMapping("/{slug}")
    public ApiResponse<ComboResponse> bySlug(@PathVariable String slug) {
        return ApiResponse.ok("Combo fetched successfully.", comboService.getComboBySlug(slug));
    }

    // --- 4. Cart Additions (Authenticated) ---

    @PostMapping("/{id}/cart")
    public ApiResponse<CartResponse> addPredefinedToCart(
            @AuthenticationPrincipal User user,
            @PathVariable Long id,
            @RequestBody(required = false) AddPredefinedComboToCartRequest request) {
        int quantity = (request != null && request.quantity() > 0) ? request.quantity() : 1;
        return ApiResponse.ok("Combo added to cart successfully.",
                comboService.addPredefinedComboToCart(user, id, quantity));
    }

    @PostMapping("/custom/cart")
    public ApiResponse<CartResponse> addCustomToCart(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody AddCustomComboToCartRequest request) {
        return ApiResponse.ok("Custom combo added to cart successfully.",
                comboService.addCustomComboToCart(user, request));
    }

    @DeleteMapping("/cart/groups/{comboGroupId}")
    public ApiResponse<CartResponse> removeComboGroup(
            @AuthenticationPrincipal User user,
            @PathVariable String comboGroupId) {
        return ApiResponse.ok("Combo removed from cart successfully.",
                comboService.removeComboGroupFromCart(user, comboGroupId));
    }
}