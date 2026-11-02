package com.cosmetics.ecommerce.controller;

import com.cosmetics.ecommerce.dto.*;
import com.cosmetics.ecommerce.enums.OrderStatus;
import com.cosmetics.ecommerce.exception.BadRequestException;
import com.cosmetics.ecommerce.service.*;
import com.cosmetics.ecommerce.dto.ProfileDtos.AdminUserUpdateRequest;
import com.cosmetics.ecommerce.dto.UserResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.*;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/admin")
@PreAuthorize("hasAnyRole('ADMIN','STAFF')")
public class AdminController {
    private final DashboardService dashboard;
    private final OrderService orders;
    private final ProductService products;
    private final UserAdminService users;
    private final FileStorageService fileStorageService;

    @org.springframework.beans.factory.annotation.Autowired
    public AdminController(DashboardService dashboard, OrderService orders, ProductService products,
                           UserAdminService users, FileStorageService fileStorageService) {
        this.dashboard = dashboard;
        this.orders = orders;
        this.products = products;
        this.users = users;
        this.fileStorageService = fileStorageService;
    }

    public AdminController(DashboardService dashboard, OrderService orders, ProductService products, UserAdminService users) {
        this(dashboard, orders, products, users, null);
    }

    @PostMapping(value = "/uploads/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ImageUploadResponse> uploadImage(
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "image", required = false) MultipartFile image) {
        MultipartFile uploadFile = file != null ? file : image;
        if (uploadFile == null || uploadFile.isEmpty()) {
            throw new BadRequestException("Uploaded file cannot be empty.");
        }
        if (fileStorageService == null) {
            throw new IllegalStateException("File storage service is not available.");
        }
        StoredFile stored = fileStorageService.storeFile(uploadFile);
        return ApiResponse.ok("Image uploaded successfully.", ImageUploadResponse.from(stored));
    }

    @GetMapping("/dashboard") @PreAuthorize("hasRole('ADMIN')") public ApiResponse<DashboardResponse> dashboard() { return ApiResponse.ok("Dashboard fetched successfully.", dashboard.get()); }
    @PatchMapping("/orders/{id}/status") public ApiResponse<com.cosmetics.ecommerce.dto.OrderDtos.OrderResponse> status(@PathVariable Long id, @RequestParam OrderStatus status) { return ApiResponse.ok("Order status updated.", orders.updateStatus(id, status)); }
    @GetMapping("/orders") public ApiResponse<PageResponse<com.cosmetics.ecommerce.dto.OrderDtos.OrderResponse>> allOrders(@PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) { return ApiResponse.ok("Orders fetched successfully.", orders.all(pageable)); }
    @GetMapping("/inventory") public ApiResponse<List<com.cosmetics.ecommerce.dto.CatalogDtos.ProductResponse>> inventory() { return ApiResponse.ok("Inventory fetched successfully.", products.allInventory()); }
    @GetMapping("/inventory/low-stock") public ApiResponse<List<com.cosmetics.ecommerce.dto.CatalogDtos.ProductResponse>> lowStock() { return ApiResponse.ok("Low-stock products fetched successfully.", products.inventory(false)); }
    @GetMapping("/inventory/out-of-stock") public ApiResponse<List<com.cosmetics.ecommerce.dto.CatalogDtos.ProductResponse>> outOfStock() { return ApiResponse.ok("Out-of-stock products fetched successfully.", products.inventory(true)); }
    @GetMapping("/users") @PreAuthorize("hasRole('ADMIN')") public ApiResponse<PageResponse<UserResponse>> users(@PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) { return ApiResponse.ok("Users fetched successfully.", users.list(pageable)); }
    @PatchMapping("/users/{id}") @PreAuthorize("hasRole('ADMIN')") public ApiResponse<UserResponse> updateUser(@PathVariable Long id, @Valid @RequestBody AdminUserUpdateRequest request) { return ApiResponse.ok("User updated successfully.", users.update(id, request)); }
}