package com.cosmetics.ecommerce.controller;

import com.cosmetics.ecommerce.dto.ApiResponse;
import com.cosmetics.ecommerce.dto.ImageUploadResponse;
import com.cosmetics.ecommerce.dto.PageResponse;
import com.cosmetics.ecommerce.dto.StoredFile;
import com.cosmetics.ecommerce.dto.SubcategoryDtos.*;
import com.cosmetics.ecommerce.exception.BadRequestException;
import com.cosmetics.ecommerce.service.FileStorageService;
import com.cosmetics.ecommerce.service.SubcategoryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import org.springframework.beans.factory.annotation.Autowired;
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
@RequestMapping({"/admin/subcategories", "/api/admin/subcategories"})
@PreAuthorize("hasAnyRole('ADMIN','STAFF')")
public class AdminSubcategoryController {

    private final SubcategoryService subcategoryService;
    private final FileStorageService fileStorageService;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    @Autowired
    public AdminSubcategoryController(SubcategoryService subcategoryService,
                                     @Autowired(required = false) FileStorageService fileStorageService,
                                     ObjectMapper objectMapper,
                                     Validator validator) {
        this.subcategoryService = subcategoryService;
        this.fileStorageService = fileStorageService;
        this.objectMapper = objectMapper;
        this.validator = validator;
    }

    public AdminSubcategoryController(SubcategoryService subcategoryService) {
        this(subcategoryService, null, new ObjectMapper(), null);
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<SubcategoryResponse> create(@Valid @RequestBody SubcategoryRequest request) {
        return ApiResponse.ok("Subcategory created successfully.", subcategoryService.create(request));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<SubcategoryResponse> createMultipart(
            @RequestParam(value = "subcategory", required = false) String subcategoryJson,
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "categoryId", required = false) Long categoryId,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "imageUrl", required = false) String imageUrl,
            @RequestParam(value = "active", required = false) Boolean active,
            @RequestPart(value = "image", required = false) MultipartFile image,
            @RequestPart(value = "file", required = false) MultipartFile file) {

        MultipartFile uploadFile = image != null ? image : file;
        SubcategoryRequest request = buildSubcategoryRequest(subcategoryJson, name, categoryId, description, imageUrl, active);
        validateRequest(request);

        return ApiResponse.ok("Subcategory created successfully.",
                subcategoryService.createWithMultipart(request, uploadFile));
    }

    @PostMapping(value = "/upload-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
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

    @GetMapping
    public ApiResponse<PageResponse<SubcategoryResponse>> list(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Boolean active,
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        return ApiResponse.ok("Subcategories fetched successfully.",
                subcategoryService.getAllAdmin(query, categoryId, active, pageable));
    }

    @GetMapping("/{id}")
    public ApiResponse<SubcategoryResponse> getById(@PathVariable Long id) {
        return ApiResponse.ok("Subcategory fetched successfully.", subcategoryService.getById(id));
    }

    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<SubcategoryResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody SubcategoryRequest request) {
        return ApiResponse.ok("Subcategory updated successfully.", subcategoryService.update(id, request));
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<SubcategoryResponse> updateMultipart(
            @PathVariable Long id,
            @RequestParam(value = "subcategory", required = false) String subcategoryJson,
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "categoryId", required = false) Long categoryId,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "imageUrl", required = false) String imageUrl,
            @RequestParam(value = "active", required = false) Boolean active,
            @RequestPart(value = "image", required = false) MultipartFile image,
            @RequestPart(value = "file", required = false) MultipartFile file) {

        MultipartFile uploadFile = image != null ? image : file;
        SubcategoryRequest request = buildSubcategoryRequest(subcategoryJson, name, categoryId, description, imageUrl, active);
        validateRequest(request);

        return ApiResponse.ok("Subcategory updated successfully.",
                subcategoryService.updateWithMultipart(id, request, uploadFile));
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<SubcategoryResponse> updateStatus(
            @PathVariable Long id,
            @RequestParam(value = "active", required = false) Boolean activeParam,
            @RequestBody(required = false) SubcategoryStatusUpdateRequest body) {

        Boolean active = activeParam != null ? activeParam : (body != null ? body.active() : null);
        if (active == null) {
            throw new BadRequestException("Active status parameter or request body is required.");
        }

        return ApiResponse.ok("Subcategory status updated successfully.",
                subcategoryService.updateStatus(id, active));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        subcategoryService.delete(id);
        return ApiResponse.ok("Subcategory deleted successfully.", null);
    }

    private SubcategoryRequest buildSubcategoryRequest(String subcategoryJson, String name, Long categoryId,
                                                      String description, String imageUrl, Boolean active) {
        if (subcategoryJson != null && !subcategoryJson.isBlank()) {
            try {
                SubcategoryRequest parsed = objectMapper.readValue(subcategoryJson, SubcategoryRequest.class);
                return new SubcategoryRequest(
                        parsed.name(),
                        parsed.categoryId(),
                        parsed.description(),
                        imageUrl != null ? imageUrl : parsed.imageUrl(),
                        parsed.active()
                );
            } catch (Exception e) {
                throw new BadRequestException("Invalid subcategory JSON part: " + e.getMessage());
            }
        }
        return new SubcategoryRequest(name, categoryId, description, imageUrl, active != null ? active : true);
    }

    private void validateRequest(SubcategoryRequest request) {
        if (validator != null) {
            Set<ConstraintViolation<SubcategoryRequest>> violations = validator.validate(request);
            if (!violations.isEmpty()) {
                String message = violations.stream()
                        .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                        .collect(Collectors.joining("; "));
                throw new BadRequestException(message);
            }
        }
    }
}

