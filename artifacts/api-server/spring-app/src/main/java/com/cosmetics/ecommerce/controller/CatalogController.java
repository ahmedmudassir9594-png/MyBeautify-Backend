package com.cosmetics.ecommerce.controller;

import com.cosmetics.ecommerce.dto.*;
import com.cosmetics.ecommerce.dto.CatalogDtos.*;
import com.cosmetics.ecommerce.exception.BadRequestException;
import com.cosmetics.ecommerce.service.CatalogService;
import com.cosmetics.ecommerce.service.FileStorageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
public class CatalogController {
    private final CatalogService catalog;
    private final FileStorageService fileStorageService;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    @org.springframework.beans.factory.annotation.Autowired
    public CatalogController(CatalogService catalog, FileStorageService fileStorageService,
                             ObjectMapper objectMapper, Validator validator) {
        this.catalog = catalog;
        this.fileStorageService = fileStorageService;
        this.objectMapper = objectMapper;
        this.validator = validator;
    }

    public CatalogController(CatalogService catalog) {
        this(catalog, null, new ObjectMapper(), null);
    }

    @GetMapping("/categoriesASC")
    public ApiResponse<PageResponse<CategoryResponse>> categories(
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.ASC) Pageable pageable) {
        return ApiResponse.ok("Categories fetched successfully.", catalog.categories(pageable));
    }

    @GetMapping("/categories")
    public ApiResponse<List<CategoryResponse>> getAllCategory() {
        return ApiResponse.ok("Category fetched successfully.", catalog.getAllCategory());
    }

    @GetMapping("/categories/{id}")
    public ApiResponse<CategoryResponse> category(@PathVariable Long id) {
        return ApiResponse.ok("Category fetched successfully.", catalog.category(id));
    }

    @GetMapping("/brands")
    public ApiResponse<PageResponse<BrandResponse>> brands(
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        return ApiResponse.ok("Brands fetched successfully.", catalog.brands(pageable));
    }

    @GetMapping("/brands/{id}")
    public ApiResponse<BrandResponse> brand(@PathVariable Long id) {
        return ApiResponse.ok("Brand fetched successfully.", catalog.brand(id));
    }

    @PostMapping(value = "/admin/categories", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<CategoryResponse> createCategory(@Valid @RequestBody CategoryRequest r) {
        return ApiResponse.ok("Category created successfully.", catalog.createCategory(r));
    }

    @PostMapping(value = "/admin/categories", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<CategoryResponse> createCategoryMultipart(
            @RequestParam(value = "category", required = false) String categoryJson,
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "imageUrl", required = false) String imageUrl,
            @RequestParam(value = "active", required = false) Boolean active,
            @RequestPart(value = "image", required = false) MultipartFile image,
            @RequestPart(value = "file", required = false) MultipartFile file) {

        MultipartFile uploadFile = image != null ? image : file;
        String finalImageUrl = imageUrl;
        if (uploadFile != null && !uploadFile.isEmpty()) {
            if (fileStorageService == null) {
                throw new IllegalStateException("File storage service is not available.");
            }
            StoredFile stored = fileStorageService.storeFile(uploadFile);
            finalImageUrl = stored.fullUrl();
        }

        CategoryRequest request = buildCategoryRequest(categoryJson, name, description, finalImageUrl, active);
        validateRequest(request);
        return ApiResponse.ok("Category created successfully.", catalog.createCategory(request));
    }

    @PutMapping(value = "/admin/categories/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<CategoryResponse> updateCategory(@PathVariable Long id, @Valid @RequestBody CategoryRequest r) {
        return ApiResponse.ok("Category updated successfully.", catalog.updateCategory(id, r));
    }

    @PutMapping(value = "/admin/categories/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<CategoryResponse> updateCategoryMultipart(
            @PathVariable Long id,
            @RequestParam(value = "category", required = false) String categoryJson,
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "imageUrl", required = false) String imageUrl,
            @RequestParam(value = "active", required = false) Boolean active,
            @RequestPart(value = "image", required = false) MultipartFile image,
            @RequestPart(value = "file", required = false) MultipartFile file) {

        MultipartFile uploadFile = image != null ? image : file;
        String finalImageUrl = imageUrl;
        if (uploadFile != null && !uploadFile.isEmpty()) {
            if (fileStorageService == null) {
                throw new IllegalStateException("File storage service is not available.");
            }
            StoredFile stored = fileStorageService.storeFile(uploadFile);
            finalImageUrl = stored.fullUrl();
        }

        CategoryRequest request = buildCategoryRequest(categoryJson, name, description, finalImageUrl, active);
        validateRequest(request);
        return ApiResponse.ok("Category updated successfully.", catalog.updateCategory(id, request));
    }

    @PostMapping(value = "/admin/categories/{id}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<CategoryResponse> uploadCategoryImage(
            @PathVariable Long id,
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "image", required = false) MultipartFile image) {
        MultipartFile uploadFile = file != null ? file : image;
        if (uploadFile == null || uploadFile.isEmpty()) {
            throw new BadRequestException("Uploaded file cannot be empty.");
        }
        return ApiResponse.ok("Category image updated successfully.", catalog.updateCategoryImage(id, uploadFile));
    }

    @PostMapping(value = "/admin/categories/upload-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<ImageUploadResponse> uploadImageStandalone(
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

    @DeleteMapping("/admin/categories/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> deleteCategory(@PathVariable Long id) {
        catalog.deleteCategory(id);
        return ApiResponse.ok("Category archived successfully.", null);
    }

    @PostMapping("/admin/brands")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<BrandResponse> createBrand(@Valid @RequestBody BrandRequest r) {
        return ApiResponse.ok("Brand created successfully.", catalog.createBrand(r));
    }

    @PutMapping("/admin/brands/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<BrandResponse> updateBrand(@PathVariable Long id, @Valid @RequestBody BrandRequest r) {
        return ApiResponse.ok("Brand updated successfully.", catalog.updateBrand(id, r));
    }

    @DeleteMapping("/admin/brands/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> deleteBrand(@PathVariable Long id) {
        catalog.deleteBrand(id);
        return ApiResponse.ok("Brand archived successfully.", null);
    }

    private CategoryRequest buildCategoryRequest(String categoryJson, String name, String description,
                                                 String imageUrl, Boolean active) {
        if (categoryJson != null && !categoryJson.isBlank()) {
            try {
                CategoryRequest parsed = objectMapper.readValue(categoryJson, CategoryRequest.class);
                return new CategoryRequest(
                        parsed.name(),
                        parsed.description(),
                        imageUrl != null ? imageUrl : parsed.imageUrl(),
                        parsed.active()
                );
            } catch (Exception e) {
                throw new BadRequestException("Invalid category JSON part: " + e.getMessage());
            }
        }
        return new CategoryRequest(name, description, imageUrl, active != null ? active : true);
    }

    private void validateRequest(CategoryRequest request) {
        if (validator != null) {
            Set<ConstraintViolation<CategoryRequest>> violations = validator.validate(request);
            if (!violations.isEmpty()) {
                String message = violations.stream()
                        .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                        .collect(Collectors.joining("; "));
                throw new BadRequestException(message);
            }
        }
    }
}
