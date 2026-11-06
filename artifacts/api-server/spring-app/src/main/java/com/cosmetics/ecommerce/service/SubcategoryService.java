package com.cosmetics.ecommerce.service;

import com.cosmetics.ecommerce.dto.PageResponse;
import com.cosmetics.ecommerce.dto.StoredFile;
import com.cosmetics.ecommerce.dto.SubcategoryDtos.*;
import com.cosmetics.ecommerce.entity.Category;
import com.cosmetics.ecommerce.entity.Subcategory;
import com.cosmetics.ecommerce.enums.AuditAction;
import com.cosmetics.ecommerce.enums.AuditResult;
import com.cosmetics.ecommerce.exception.BadRequestException;
import com.cosmetics.ecommerce.exception.DuplicateResourceException;
import com.cosmetics.ecommerce.exception.ResourceNotFoundException;
import com.cosmetics.ecommerce.repository.CategoryRepository;
import com.cosmetics.ecommerce.repository.ProductRepository;
import com.cosmetics.ecommerce.repository.SubcategoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class SubcategoryService {

    private final SubcategoryRepository subcategoryRepository;
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final FileStorageService fileStorageService;
    private final AuditLogService auditLogService;

    @Autowired
    public SubcategoryService(SubcategoryRepository subcategoryRepository,
                              CategoryRepository categoryRepository,
                              ProductRepository productRepository,
                              @Autowired(required = false) FileStorageService fileStorageService,
                              @Autowired(required = false) AuditLogService auditLogService) {
        this.subcategoryRepository = subcategoryRepository;
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
        this.fileStorageService = fileStorageService;
        this.auditLogService = auditLogService;
    }

    public SubcategoryService(SubcategoryRepository subcategoryRepository,
                              CategoryRepository categoryRepository,
                              ProductRepository productRepository) {
        this(subcategoryRepository, categoryRepository, productRepository, null, null);
    }

    @Transactional
    public SubcategoryResponse create(SubcategoryRequest request) {
        if (request.name() == null || request.name().trim().isBlank()) {
            throw new BadRequestException("Subcategory name cannot be blank.");
        }
        if (request.categoryId() == null) {
            throw new BadRequestException("Parent category ID is required.");
        }

        Category category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Parent category not found with id: " + request.categoryId()));

        String trimmedName = request.name().trim();
        if (subcategoryRepository.existsByCategoryIdAndNameIgnoreCase(category.getId(), trimmedName)) {
            throw new DuplicateResourceException("Subcategory with name '" + trimmedName + "' already exists under category '" + category.getName() + "'.");
        }

        Subcategory subcategory = new Subcategory(trimmedName, request.description(), category);
        subcategory.setImageUrl(request.imageUrl());
        if (request.active() != null) {
            subcategory.setActive(request.active());
        }

        Subcategory saved = subcategoryRepository.save(subcategory);

        if (auditLogService != null) {
            auditLogService.logEvent(
                    AuditAction.SUBCATEGORY_CREATE,
                    AuditResult.SUCCESS,
                    "SUBCATEGORY",
                    String.valueOf(saved.getId()),
                    "Subcategory created: " + saved.getName() + " under category " + category.getName(),
                    null,
                    "{\"name\":\"" + saved.getName() + "\",\"categoryId\":" + category.getId() + ",\"active\":" + saved.isActive() + "}"
            );
        }

        return SubcategoryResponse.from(saved, 0L);
    }

    @Transactional
    public SubcategoryResponse createWithMultipart(SubcategoryRequest request, MultipartFile file) {
        String imageUrl = request.imageUrl();
        if (file != null && !file.isEmpty()) {
            if (fileStorageService == null) {
                throw new IllegalStateException("File storage service is not available.");
            }
            StoredFile stored = fileStorageService.storeFile(file);
            imageUrl = stored.fullUrl();
        }

        SubcategoryRequest updatedRequest = new SubcategoryRequest(
                request.name(),
                request.categoryId(),
                request.description(),
                imageUrl,
                request.active()
        );

        return create(updatedRequest);
    }

    public SubcategoryResponse getById(Long id) {
        Subcategory s = subcategoryRepository.findByIdWithCategory(id)
                .orElseThrow(() -> new ResourceNotFoundException("Subcategory not found with id: " + id));
        long productCount = productRepository.countBySubcategoryId(id);
        return SubcategoryResponse.from(s, productCount);
    }

    public SubcategoryResponse getActiveById(Long id) {
        Subcategory s = subcategoryRepository.findActiveByIdWithCategory(id)
                .orElseThrow(() -> new ResourceNotFoundException("Subcategory not found with id: " + id));
        long productCount = productRepository.countBySubcategoryIdAndActiveTrue(id);
        return SubcategoryResponse.from(s, productCount);
    }

    public SubcategoryResponse getActiveBySlug(String slug) {
        Subcategory s = subcategoryRepository.findActiveBySlugWithCategory(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Subcategory not found with slug: " + slug));
        long productCount = productRepository.countBySubcategoryIdAndActiveTrue(s.getId());
        return SubcategoryResponse.from(s, productCount);
    }

    public PageResponse<SubcategoryResponse> getAllAdmin(String query, Long categoryId, Boolean active, Pageable pageable) {
        String cleanQuery = (query != null && !query.trim().isBlank()) ? query.trim() : null;
        Page<Subcategory> page = subcategoryRepository.searchAdmin(cleanQuery, categoryId, active, pageable);
        return PageResponse.from(page.map(s -> {
            long productCount = productRepository.countBySubcategoryId(s.getId());
            return SubcategoryResponse.from(s, productCount);
        }));
    }

    public List<SubcategoryResponse> getByParentCategory(Long categoryId, boolean onlyActive) {
        if (!categoryRepository.existsById(categoryId)) {
            throw new ResourceNotFoundException("Parent category not found with id: " + categoryId);
        }

        List<Subcategory> list = onlyActive
                ? subcategoryRepository.findByCategoryIdAndActiveTrueOrderByNameAsc(categoryId)
                : subcategoryRepository.findByCategoryIdOrderByNameAsc(categoryId);

        return list.stream().map(s -> {
            long productCount = onlyActive
                    ? productRepository.countBySubcategoryIdAndActiveTrue(s.getId())
                    : productRepository.countBySubcategoryId(s.getId());
            return SubcategoryResponse.from(s, productCount);
        }).toList();
    }

    public List<SubcategoryResponse> getAllActive() {
        return subcategoryRepository.findByActiveTrueOrderByNameAsc().stream().map(s -> {
            long productCount = productRepository.countBySubcategoryIdAndActiveTrue(s.getId());
            return SubcategoryResponse.from(s, productCount);
        }).toList();
    }

    @Transactional
    public SubcategoryResponse update(Long id, SubcategoryRequest request) {
        if (request.name() == null || request.name().trim().isBlank()) {
            throw new BadRequestException("Subcategory name cannot be blank.");
        }
        Subcategory s = subcategoryRepository.findByIdWithCategory(id)
                .orElseThrow(() -> new ResourceNotFoundException("Subcategory not found with id: " + id));

        Category targetCategory = s.getCategory();
        if (request.categoryId() != null && !request.categoryId().equals(targetCategory.getId())) {
            targetCategory = categoryRepository.findById(request.categoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("Parent category not found with id: " + request.categoryId()));
        }

        String trimmedName = request.name().trim();
        if (subcategoryRepository.existsByCategoryIdAndNameIgnoreCaseAndIdNot(targetCategory.getId(), trimmedName, id)) {
            throw new DuplicateResourceException("Subcategory with name '" + trimmedName + "' already exists under category '" + targetCategory.getName() + "'.");
        }

        String oldImageUrl = s.getImageUrl();
        s.setName(trimmedName);
        s.setDescription(request.description());
        s.setCategory(targetCategory);
        s.setImageUrl(request.imageUrl());
        if (request.active() != null) {
            s.setActive(request.active());
        }

        Subcategory saved = subcategoryRepository.save(s);

        if (oldImageUrl != null && !oldImageUrl.equals(saved.getImageUrl())) {
            cleanupImageIfOrphaned(oldImageUrl);
        }

        if (auditLogService != null) {
            auditLogService.logEvent(
                    AuditAction.SUBCATEGORY_UPDATE,
                    AuditResult.SUCCESS,
                    "SUBCATEGORY",
                    String.valueOf(saved.getId()),
                    "Subcategory updated: " + saved.getName(),
                    null,
                    "{\"name\":\"" + saved.getName() + "\",\"categoryId\":" + targetCategory.getId() + ",\"active\":" + saved.isActive() + "}"
            );
        }

        long productCount = productRepository.countBySubcategoryId(saved.getId());
        return SubcategoryResponse.from(saved, productCount);
    }

    @Transactional
    public SubcategoryResponse updateWithMultipart(Long id, SubcategoryRequest request, MultipartFile file) {
        String imageUrl = request.imageUrl();
        if (file != null && !file.isEmpty()) {
            if (fileStorageService == null) {
                throw new IllegalStateException("File storage service is not available.");
            }
            StoredFile stored = fileStorageService.storeFile(file);
            imageUrl = stored.fullUrl();
        }

        SubcategoryRequest updatedRequest = new SubcategoryRequest(
                request.name(),
                request.categoryId(),
                request.description(),
                imageUrl,
                request.active()
        );

        return update(id, updatedRequest);
    }

    @Transactional
    public SubcategoryResponse updateStatus(Long id, boolean active) {
        Subcategory s = subcategoryRepository.findByIdWithCategory(id)
                .orElseThrow(() -> new ResourceNotFoundException("Subcategory not found with id: " + id));

        boolean oldStatus = s.isActive();
        s.setActive(active);
        Subcategory saved = subcategoryRepository.save(s);

        if (auditLogService != null) {
            auditLogService.logEvent(
                    AuditAction.SUBCATEGORY_STATUS_CHANGE,
                    AuditResult.SUCCESS,
                    "SUBCATEGORY",
                    String.valueOf(saved.getId()),
                    "Subcategory status changed to " + (active ? "ACTIVE" : "INACTIVE") + " for " + saved.getName(),
                    "{\"active\":" + oldStatus + "}",
                    "{\"active\":" + active + "}"
            );
        }

        long productCount = productRepository.countBySubcategoryId(saved.getId());
        return SubcategoryResponse.from(saved, productCount);
    }

    @Transactional
    public void delete(Long id) {
        Subcategory s = subcategoryRepository.findByIdWithCategory(id)
                .orElseThrow(() -> new ResourceNotFoundException("Subcategory not found with id: " + id));

        long linkedProducts = productRepository.countBySubcategoryId(id);
        if (linkedProducts > 0) {
            throw new BadRequestException("Cannot delete subcategory '" + s.getName() + "' because "
                    + linkedProducts + " product(s) are associated with it. Please reassign or delete the products first.");
        }

        String imageUrl = s.getImageUrl();
        subcategoryRepository.delete(s);

        if (auditLogService != null) {
            auditLogService.logEvent(
                    AuditAction.SUBCATEGORY_DELETE,
                    AuditResult.SUCCESS,
                    "SUBCATEGORY",
                    String.valueOf(id),
                    "Subcategory deleted: " + s.getName(),
                    "{\"name\":\"" + s.getName() + "\",\"categoryId\":" + s.getCategory().getId() + "}",
                    null
            );
        }

        if (imageUrl != null) {
            cleanupImageIfOrphaned(imageUrl);
        }
    }

    public Map<Long, Long> getSubcategoryCountMap(boolean onlyActive) {
        List<Object[]> rows = onlyActive
                ? subcategoryRepository.countActiveSubcategoriesGroupedByCategory()
                : subcategoryRepository.countSubcategoriesGroupedByCategory();

        Map<Long, Long> map = new HashMap<>();
        for (Object[] row : rows) {
            if (row[0] != null && row[1] != null) {
                map.put((Long) row[0], ((Number) row[1]).longValue());
            }
        }
        return map;
    }

    public Map<Long, List<SubcategoryResponse>> getActiveSubcategoriesGroupedByCategory() {
        List<Subcategory> subcategories = subcategoryRepository.findByActiveTrueOrderByNameAsc();
        return subcategories.stream()
                .map(SubcategoryResponse::from)
                .collect(Collectors.groupingBy(SubcategoryResponse::categoryId));
    }

    private void cleanupImageIfOrphaned(String imageUrl) {
        if (fileStorageService == null || imageUrl == null || imageUrl.isBlank()) {
            return;
        }
        if (!fileStorageService.isLocalUpload(imageUrl)) {
            return;
        }
        long subcategoryCount = subcategoryRepository.countByImageUrl(imageUrl);
        long categoryCount = categoryRepository.countByImageUrl(imageUrl);
        long productCount = (productRepository != null) ? productRepository.countByImageUrl(imageUrl) : 0;
        if (subcategoryCount == 0 && categoryCount == 0 && productCount == 0) {
            fileStorageService.deleteFileIfExists(imageUrl);
        }
    }
}

