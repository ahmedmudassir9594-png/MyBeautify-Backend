package com.cosmetics.ecommerce.controller;

import com.cosmetics.ecommerce.dto.ApiResponse;
import com.cosmetics.ecommerce.dto.ComboDtos.*;
import com.cosmetics.ecommerce.dto.ImageUploadResponse;
import com.cosmetics.ecommerce.dto.PageResponse;
import com.cosmetics.ecommerce.dto.StoredFile;
import com.cosmetics.ecommerce.exception.BadRequestException;
import com.cosmetics.ecommerce.service.ComboService;
import com.cosmetics.ecommerce.service.FileStorageService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.Part;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/admin/combos")
@PreAuthorize("hasAnyRole('ADMIN','STAFF')")
public class AdminComboController {

    private final ComboService comboService;
    private final FileStorageService fileStorageService;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    public AdminComboController(
            ComboService comboService,
            @Autowired(required = false) FileStorageService fileStorageService,
            @Autowired(required = false) ObjectMapper objectMapper,
            @Autowired(required = false) Validator validator) {
        this.comboService = comboService;
        this.fileStorageService = fileStorageService;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
        this.validator = validator;
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

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<ComboResponse> create(@Valid @RequestBody ComboRequest request) {
        return ApiResponse.ok("Combo created successfully.", comboService.createCombo(request));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ComboResponse> createMultipart(
            HttpServletRequest servletRequest,
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "slug", required = false) String slug,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "imageFileName", required = false) String imageFileName,
            @RequestParam(value = "sellingPrice", required = false) BigDecimal sellingPrice,
            @RequestParam(value = "originalPrice", required = false) BigDecimal originalPrice,
            @RequestParam(value = "startDate", required = false) Instant startDate,
            @RequestParam(value = "endDate", required = false) Instant endDate,
            @RequestParam(value = "active", required = false) Boolean active,
            @RequestParam(value = "featured", required = false) Boolean featured,
            @RequestPart(value = "image", required = false) MultipartFile image,
            @RequestPart(value = "file", required = false) MultipartFile file) {

        String comboJson = extractPartOrParam(servletRequest, "combo");
        String itemsJson = extractPartOrParam(servletRequest, "items");
        MultipartFile uploadFile = image != null ? image : file;

        ComboRequest request = buildComboRequest(comboJson, name, slug, description, imageFileName,
                sellingPrice, originalPrice, startDate, endDate, active, featured, itemsJson);
        validateRequest(request);

        return ApiResponse.ok("Combo created successfully.",
                comboService.createComboWithMultipart(request, uploadFile));
    }

    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<ComboResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody ComboRequest request) {
        return ApiResponse.ok("Combo updated successfully.", comboService.updateCombo(id, request));
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ComboResponse> updateMultipart(
            @PathVariable Long id,
            HttpServletRequest servletRequest,
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "slug", required = false) String slug,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "imageFileName", required = false) String imageFileName,
            @RequestParam(value = "sellingPrice", required = false) BigDecimal sellingPrice,
            @RequestParam(value = "originalPrice", required = false) BigDecimal originalPrice,
            @RequestParam(value = "startDate", required = false) Instant startDate,
            @RequestParam(value = "endDate", required = false) Instant endDate,
            @RequestParam(value = "active", required = false) Boolean active,
            @RequestParam(value = "featured", required = false) Boolean featured,
            @RequestPart(value = "image", required = false) MultipartFile image,
            @RequestPart(value = "file", required = false) MultipartFile file) {

        String comboJson = extractPartOrParam(servletRequest, "combo");
        String itemsJson = extractPartOrParam(servletRequest, "items");
        MultipartFile uploadFile = image != null ? image : file;

        ComboRequest request = buildComboRequest(comboJson, name, slug, description, imageFileName,
                sellingPrice, originalPrice, startDate, endDate, active, featured, itemsJson);
        validateRequest(request);

        return ApiResponse.ok("Combo updated successfully.",
                comboService.updateComboWithMultipart(id, request, uploadFile));
    }

    @PostMapping(value = "/{id}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ComboResponse> uploadComboImage(
            @PathVariable Long id,
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "image", required = false) MultipartFile image) {
        MultipartFile uploadFile = file != null ? file : image;
        if (uploadFile == null || uploadFile.isEmpty()) {
            throw new BadRequestException("Uploaded file cannot be empty.");
        }
        return ApiResponse.ok("Combo image updated successfully.", comboService.updateComboImage(id, uploadFile));
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

    // --- Helper Methods ---

    private String extractPartOrParam(HttpServletRequest request, String name) {
        if (request == null) return null;
        String param = request.getParameter(name);
        if (param != null && !param.isBlank()) {
            return param;
        }
        if (request instanceof org.springframework.web.multipart.MultipartRequest multipartRequest) {
            MultipartFile filePart = multipartRequest.getFile(name);
            if (filePart != null && !filePart.isEmpty()) {
                try {
                    return new String(filePart.getBytes(), StandardCharsets.UTF_8);
                } catch (Exception ignored) {}
            }
        }
        try {
            for (Part part : request.getParts()) {
                if (name.equalsIgnoreCase(part.getName())) {
                    byte[] bytes = part.getInputStream().readAllBytes();
                    if (bytes.length > 0) {
                        return new String(bytes, StandardCharsets.UTF_8);
                    }
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private ComboRequest buildComboRequest(
            String comboJson,
            String name,
            String slug,
            String description,
            String imageFileName,
            BigDecimal sellingPrice,
            BigDecimal originalPrice,
            Instant startDate,
            Instant endDate,
            Boolean active,
            Boolean featured,
            String itemsJson) {

        if (comboJson != null && !comboJson.isBlank()) {
            try {
                ComboRequest parsed = objectMapper.readValue(comboJson, ComboRequest.class);
                return new ComboRequest(
                        parsed.name(),
                        parsed.slug(),
                        parsed.description(),
                        imageFileName != null ? imageFileName : parsed.imageFileName(),
                        parsed.sellingPrice(),
                        parsed.originalPrice(),
                        parsed.startDate(),
                        parsed.endDate(),
                        parsed.active(),
                        parsed.featured(),
                        parsed.items()
                );
            } catch (Exception e) {
                throw new BadRequestException("Invalid combo JSON part: " + e.getMessage());
            }
        }

        List<ComboItemRequest> items = Collections.emptyList();
        if (itemsJson != null && !itemsJson.isBlank()) {
            try {
                items = objectMapper.readValue(itemsJson, new TypeReference<List<ComboItemRequest>>() {});
            } catch (Exception e) {
                throw new BadRequestException("Invalid combo items JSON: " + e.getMessage());
            }
        }

        return new ComboRequest(
                name,
                slug,
                description,
                imageFileName,
                sellingPrice,
                originalPrice,
                startDate,
                endDate,
                active != null ? active : true,
                featured != null ? featured : false,
                items
        );
    }

    private void validateRequest(ComboRequest request) {
        if (validator != null) {
            Set<ConstraintViolation<ComboRequest>> violations = validator.validate(request);
            if (!violations.isEmpty()) {
                String message = violations.stream()
                        .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                        .collect(Collectors.joining("; "));
                throw new BadRequestException(message);
            }
        }
    }
}
