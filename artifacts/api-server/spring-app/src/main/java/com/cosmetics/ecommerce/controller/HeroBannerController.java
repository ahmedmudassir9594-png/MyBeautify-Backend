package com.cosmetics.ecommerce.controller;

import com.cosmetics.ecommerce.dto.ApiResponse;
import com.cosmetics.ecommerce.dto.BannerDtos.HeroBannerRequest;
import com.cosmetics.ecommerce.dto.BannerDtos.HeroBannerResponse;
import com.cosmetics.ecommerce.service.HeroBannerService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class HeroBannerController {

    private final HeroBannerService bannerService;

    public HeroBannerController(HeroBannerService bannerService) {
        this.bannerService = bannerService;
    }

    /**
     * Public storefront endpoint to get all active hero slides
     */
    @GetMapping({"/banners", "/api/banners"})
    public ApiResponse<List<HeroBannerResponse>> getActiveBanners() {
        return ApiResponse.ok("Active hero banners fetched successfully.", bannerService.getActiveBanners());
    }

    @GetMapping({"/banners/{id}", "/api/banners/{id}"})
    public ApiResponse<HeroBannerResponse> getBanner(@PathVariable Long id) {
        return ApiResponse.ok("Hero banner fetched successfully.", bannerService.getBanner(id));
    }

    /**
     * Admin endpoints for full banner lifecycle management
     */
    @GetMapping({"/admin/banners", "/api/admin/banners"})
    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    public ApiResponse<List<HeroBannerResponse>> getAllBanners() {
        return ApiResponse.ok("All hero banners fetched successfully.", bannerService.getAllBanners());
    }

    @PostMapping({"/admin/banners", "/api/admin/banners"})
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<HeroBannerResponse> createBanner(@Valid @RequestBody HeroBannerRequest request) {
        return ApiResponse.ok("Hero banner created successfully.", bannerService.createBanner(request));
    }

    @PutMapping({"/admin/banners/{id}", "/api/admin/banners/{id}"})
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<HeroBannerResponse> updateBanner(
            @PathVariable Long id,
            @Valid @RequestBody HeroBannerRequest request) {
        return ApiResponse.ok("Hero banner updated successfully.", bannerService.updateBanner(id, request));
    }

    @PatchMapping({"/admin/banners/{id}/status", "/api/admin/banners/{id}/status"})
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<HeroBannerResponse> toggleBannerStatus(
            @PathVariable Long id,
            @RequestParam boolean active) {
        return ApiResponse.ok("Hero banner status updated successfully.", bannerService.toggleBannerActive(id, active));
    }

    @DeleteMapping({"/admin/banners/{id}", "/api/admin/banners/{id}"})
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> deleteBanner(@PathVariable Long id) {
        bannerService.deleteBanner(id);
        return ApiResponse.ok("Hero banner deleted successfully.", null);
    }
}

