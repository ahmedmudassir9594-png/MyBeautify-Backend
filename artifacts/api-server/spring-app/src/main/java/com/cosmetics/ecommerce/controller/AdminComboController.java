package com.cosmetics.ecommerce.controller;

import com.cosmetics.ecommerce.dto.ApiResponse;
import com.cosmetics.ecommerce.dto.ComboDtos.*;
import com.cosmetics.ecommerce.dto.PageResponse;
import com.cosmetics.ecommerce.service.ComboService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/combos")
@PreAuthorize("hasAnyRole('ADMIN','STAFF')")
public class AdminComboController {

    private final ComboService comboService;

    public AdminComboController(ComboService comboService) {
        this.comboService = comboService;
    }

    // --- Predefined Combos CRUD ---

    @GetMapping
    public ApiResponse<PageResponse<ComboResponse>> list(
            @RequestParam(required = false) String query,
            @PageableDefault(size = 15) Pageable pageable) {
        return ApiResponse.ok("Combos fetched successfully.", comboService.getAllCombos(query, pageable));
    }

    @GetMapping("/{id}")
    public ApiResponse<ComboResponse> getById(@PathVariable Long id) {
        return ApiResponse.ok("Combo fetched successfully.", comboService.getComboById(id));
    }

    @PostMapping
    public ApiResponse<ComboResponse> create(@Valid @RequestBody ComboRequest request) {
        return ApiResponse.ok("Combo created successfully.", comboService.createCombo(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<ComboResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody ComboRequest request) {
        return ApiResponse.ok("Combo updated successfully.", comboService.updateCombo(id, request));
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<ComboResponse> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody ComboStatusPatchRequest request) {
        return ApiResponse.ok("Combo status updated successfully.", comboService.updateComboStatus(id, request.active()));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        comboService.deleteCombo(id);
        return ApiResponse.ok("Combo deleted successfully.", null);
    }

    // --- Discount Rules CRUD ---

    @GetMapping("/rules")
    public ApiResponse<List<ComboDiscountRuleResponse>> listRules() {
        return ApiResponse.ok("Combo discount rules fetched successfully.", comboService.getAllDiscountRules());
    }

    @GetMapping("/rules/{id}")
    public ApiResponse<ComboDiscountRuleResponse> getRule(@PathVariable Long id) {
        return ApiResponse.ok("Combo discount rule fetched successfully.", comboService.getDiscountRule(id));
    }

    @PostMapping("/rules")
    public ApiResponse<ComboDiscountRuleResponse> createRule(@Valid @RequestBody ComboDiscountRuleRequest request) {
        return ApiResponse.ok("Combo discount rule created successfully.", comboService.createDiscountRule(request));
    }

    @PutMapping("/rules/{id}")
    public ApiResponse<ComboDiscountRuleResponse> updateRule(
            @PathVariable Long id,
            @Valid @RequestBody ComboDiscountRuleRequest request) {
        return ApiResponse.ok("Combo discount rule updated successfully.", comboService.updateDiscountRule(id, request));
    }

    @DeleteMapping("/rules/{id}")
    public ApiResponse<Void> deleteRule(@PathVariable Long id) {
        comboService.deleteDiscountRule(id);
        return ApiResponse.ok("Combo discount rule deleted successfully.", null);
    }
}

