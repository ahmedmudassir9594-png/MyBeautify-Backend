package com.cosmetics.ecommerce.controller;

import com.cosmetics.ecommerce.dto.*;
import com.cosmetics.ecommerce.dto.CatalogDtos.*;
import com.cosmetics.ecommerce.exception.BadRequestException;
import com.cosmetics.ecommerce.service.FileStorageService;
import com.cosmetics.ecommerce.service.ProductService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import org.springframework.data.domain.*;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping({"/products", "/admin/products"})
public class ProductController {
    private final ProductService products;
    private final FileStorageService fileStorageService;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    @org.springframework.beans.factory.annotation.Autowired
    public ProductController(ProductService products, FileStorageService fileStorageService,
                             ObjectMapper objectMapper, Validator validator) {
        this.products = products;
        this.fileStorageService = fileStorageService;
        this.objectMapper = objectMapper;
        this.validator = validator;
    }

    public ProductController(ProductService products) {
        this(products, null, new ObjectMapper(), null);
    }

    @GetMapping
    public ApiResponse<PageResponse<ProductResponse>> list(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Boolean featured,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.ok("Products fetched successfully.",
                products.list(query, categoryId, featured, minPrice, maxPrice, pageable));
    }

    @GetMapping("/{id}")
    public ApiResponse<ProductResponse> get(@PathVariable Long id) {
        return ApiResponse.ok("Product fetched successfully.", products.get(id));
    }

    @GetMapping("/slug/{slug}")
    public ApiResponse<ProductResponse> slug(@PathVariable String slug) {
        return ApiResponse.ok("Product fetched successfully.", products.getBySlug(slug));
    }

    @GetMapping("/categories/{categoryId}/products")
    public ApiResponse<PageResponse<ProductResponse>> categoryProducts(
            @PathVariable Long categoryId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok("Category products fetched successfully.",
                products.list(null, categoryId, null, null, null, pageable));
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<ProductResponse> create(@Valid @RequestBody ProductRequest request) {
        return ApiResponse.ok("Product created successfully.", products.create(request));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<ProductResponse> createMultipart(
            @RequestParam(value = "product", required = false) String productJson,
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "sku", required = false) String sku,
            @RequestParam(value = "price", required = false) BigDecimal price,
            @RequestParam(value = "discountPrice", required = false) BigDecimal discountPrice,
            @RequestParam(value = "stockQuantity", required = false) Integer stockQuantity,
            @RequestParam(value = "lowStockThreshold", required = false) Integer lowStockThreshold,
            @RequestParam(value = "categoryId", required = false) Long categoryId,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "shortDescription", required = false) String shortDescription,
            @RequestParam(value = "imageUrl", required = false) String imageUrl,
            @RequestParam(value = "active", required = false) Boolean active,
            @RequestParam(value = "featured", required = false) Boolean featured,
            @RequestParam(value = "ingredients", required = false) String ingredients,
            @RequestParam(value = "benefits", required = false) String benefits,
            @RequestParam(value = "howToUse", required = false) String howToUse,
            @RequestParam(value = "skinType", required = false) String skinType,
            @RequestParam(value = "shade", required = false) String shade,
            @RequestParam(value = "size", required = false) String size,
            @RequestParam(value = "expiryDate", required = false) Instant expiryDate,
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

        ProductRequest request = buildProductRequest(productJson, name, sku, price, discountPrice,
                stockQuantity, lowStockThreshold, categoryId, description, shortDescription,
                finalImageUrl, active, featured, ingredients, benefits, howToUse, skinType, shade, size, expiryDate);

        validateRequest(request);
        return ApiResponse.ok("Product created successfully.", products.create(request));
    }

    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<ProductResponse> update(@PathVariable Long id, @Valid @RequestBody ProductRequest request) {
        return ApiResponse.ok("Product updated successfully.", products.update(id, request));
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<ProductResponse> updateMultipart(
            @PathVariable Long id,
            @RequestParam(value = "product", required = false) String productJson,
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "sku", required = false) String sku,
            @RequestParam(value = "price", required = false) BigDecimal price,
            @RequestParam(value = "discountPrice", required = false) BigDecimal discountPrice,
            @RequestParam(value = "stockQuantity", required = false) Integer stockQuantity,
            @RequestParam(value = "lowStockThreshold", required = false) Integer lowStockThreshold,
            @RequestParam(value = "categoryId", required = false) Long categoryId,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "shortDescription", required = false) String shortDescription,
            @RequestParam(value = "imageUrl", required = false) String imageUrl,
            @RequestParam(value = "active", required = false) Boolean active,
            @RequestParam(value = "featured", required = false) Boolean featured,
            @RequestParam(value = "ingredients", required = false) String ingredients,
            @RequestParam(value = "benefits", required = false) String benefits,
            @RequestParam(value = "howToUse", required = false) String howToUse,
            @RequestParam(value = "skinType", required = false) String skinType,
            @RequestParam(value = "shade", required = false) String shade,
            @RequestParam(value = "size", required = false) String size,
            @RequestParam(value = "expiryDate", required = false) Instant expiryDate,
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

        ProductRequest request = buildProductRequest(productJson, name, sku, price, discountPrice,
                stockQuantity, lowStockThreshold, categoryId, description, shortDescription,
                finalImageUrl, active, featured, ingredients, benefits, howToUse, skinType, shade, size, expiryDate);

        validateRequest(request);
        return ApiResponse.ok("Product updated successfully.", products.update(id, request));
    }

    @PostMapping(value = "/{id}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<ProductResponse> uploadProductImage(
            @PathVariable Long id,
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "image", required = false) MultipartFile image) {
        MultipartFile uploadFile = file != null ? file : image;
        if (uploadFile == null || uploadFile.isEmpty()) {
            throw new BadRequestException("Uploaded file cannot be empty.");
        }
        return ApiResponse.ok("Product image updated successfully.", products.updateProductImage(id, uploadFile));
    }

    @PostMapping(value = "/upload-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
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

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        products.delete(id);
        return ApiResponse.ok("Product archived successfully.", null);
    }

    @PatchMapping("/{id}/stock")
    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    public ApiResponse<ProductResponse> stock(@PathVariable Long id, @RequestParam int quantity) {
        return ApiResponse.ok("Stock updated successfully.", products.updateStock(id, quantity));
    }

    private ProductRequest buildProductRequest(
            String productJson, String name, String sku, BigDecimal price, BigDecimal discountPrice,
            Integer stockQuantity, Integer lowStockThreshold, Long categoryId,
            String description, String shortDescription, String imageUrl, Boolean active,
            Boolean featured, String ingredients, String benefits, String howToUse,
            String skinType, String shade, String size, Instant expiryDate) {

        if (productJson != null && !productJson.isBlank()) {
            try {
                ProductRequest p = objectMapper.readValue(productJson, ProductRequest.class);
                return new ProductRequest(
                        p.name(), p.sku(), p.price(), p.discountPrice(),
                        p.stockQuantity(), p.lowStockThreshold(), p.categoryId(),
                        p.description(), p.shortDescription(),
                        imageUrl != null ? imageUrl : p.imageUrl(),
                        p.active(), p.featured(), p.ingredients(), p.benefits(),
                        p.howToUse(), p.skinType(), p.shade(), p.size(), p.expiryDate()
                );
            } catch (Exception e) {
                throw new BadRequestException("Invalid product JSON part: " + e.getMessage());
            }
        }

        return new ProductRequest(
                name, sku, price, discountPrice,
                stockQuantity != null ? stockQuantity : 0,
                lowStockThreshold != null ? lowStockThreshold : 5,
                categoryId, description, shortDescription,
                imageUrl, active != null ? active : true,
                Boolean.TRUE.equals(featured),
                ingredients, benefits, howToUse, skinType, shade, size, expiryDate
        );
    }

    private void validateRequest(ProductRequest request) {
        if (validator != null) {
            Set<ConstraintViolation<ProductRequest>> violations = validator.validate(request);
            if (!violations.isEmpty()) {
                String message = violations.stream()
                        .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                        .collect(Collectors.joining("; "));
                throw new BadRequestException(message);
            }
        }
    }
}
