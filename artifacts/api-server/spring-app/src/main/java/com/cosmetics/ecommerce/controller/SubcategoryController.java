package com.cosmetics.ecommerce.controller;

import com.cosmetics.ecommerce.dto.ApiResponse;
import com.cosmetics.ecommerce.dto.SubcategoryDtos.SubcategoryResponse;
import com.cosmetics.ecommerce.service.SubcategoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping({"/subcategories", "/api/subcategories"})
public class SubcategoryController {

    private final SubcategoryService subcategoryService;

    @Autowired
    public SubcategoryController(SubcategoryService subcategoryService) {
        this.subcategoryService = subcategoryService;
    }

    @GetMapping
    public ApiResponse<List<SubcategoryResponse>> list(
            @RequestParam(required = false) Long categoryId) {
        List<SubcategoryResponse> list = categoryId != null
                ? subcategoryService.getByParentCategory(categoryId, true)
                : subcategoryService.getAllActive();
        return ApiResponse.ok("Subcategories fetched successfully.", list);
    }

    @GetMapping("/{id}")
    public ApiResponse<SubcategoryResponse> getById(@PathVariable Long id) {
        return ApiResponse.ok("Subcategory fetched successfully.", subcategoryService.getActiveById(id));
    }

    @GetMapping("/slug/{slug}")
    public ApiResponse<SubcategoryResponse> getBySlug(@PathVariable String slug) {
        return ApiResponse.ok("Subcategory fetched successfully.", subcategoryService.getActiveBySlug(slug));
    }
}

