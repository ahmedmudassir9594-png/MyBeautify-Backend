package com.cosmetics.ecommerce.service;

import com.cosmetics.ecommerce.dto.CatalogDtos.*;
import com.cosmetics.ecommerce.dto.PageResponse;
import com.cosmetics.ecommerce.dto.StoredFile;
import com.cosmetics.ecommerce.dto.SubcategoryDtos.SubcategoryResponse;
import com.cosmetics.ecommerce.entity.*;
import com.cosmetics.ecommerce.enums.AuditAction;
import com.cosmetics.ecommerce.enums.AuditResult;
import com.cosmetics.ecommerce.exception.*;
import com.cosmetics.ecommerce.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class CatalogService {
    private final CategoryRepository categories;
    private final FileStorageService fileStorageService;
    private final ProductRepository productRepository;
    private final SubcategoryRepository subcategoryRepository;
    private final AuditLogService auditLogService;

    public CatalogService(CategoryRepository categories) {
        this(categories, null, null, null, null);
    }

    public CatalogService(CategoryRepository categories,
                          FileStorageService fileStorageService,
                          ProductRepository productRepository) {
        this(categories, fileStorageService, productRepository, null, null);
    }

    @Autowired
    public CatalogService(CategoryRepository categories,
                          FileStorageService fileStorageService,
                          ProductRepository productRepository,
                          @Autowired(required = false) SubcategoryRepository subcategoryRepository,
                          @Autowired(required = false) AuditLogService auditLogService) {
        this.categories = categories;
        this.fileStorageService = fileStorageService;
        this.productRepository = productRepository;
        this.subcategoryRepository = subcategoryRepository;
        this.auditLogService = auditLogService;
    }

    public PageResponse<CategoryResponse> categories(Pageable pageable) {
        Page<Category> page = categories.findByActiveTrueOrderByNameAsc(pageable);
        Map<Long, List<SubcategoryResponse>> subcategoryMap = getActiveSubcategoryMap();
        Map<Long, Long> countMap = getSubcategoryCountMap(true);

        return PageResponse.from(page.map(c -> {
            long count = countMap.getOrDefault(c.getId(), 0L);
            List<SubcategoryResponse> subs = subcategoryMap.getOrDefault(c.getId(), Collections.emptyList());
            return CategoryResponse.from(c, count, subs);
        }));
    }

    public List<CategoryResponse> getAllCategory() {
        // Sirf active categories fetch karega:
        List<Category> allCategories = categories.findByActiveTrueOrderByNameAsc();
        Map<Long, List<SubcategoryResponse>> subcategoryMap = getActiveSubcategoryMap();
        Map<Long, Long> countMap = getSubcategoryCountMap(true);

        return allCategories.stream().map(c -> {
            long count = countMap.getOrDefault(c.getId(), 0L);
            List<SubcategoryResponse> subs = subcategoryMap.getOrDefault(c.getId(), Collections.emptyList());
            return CategoryResponse.from(c, count, subs);
        }).toList();
    }

    public CategoryResponse category(Long id) {
        Category c = categories.findById(id).orElseThrow(() -> new ResourceNotFoundException("Category not found."));
        long count = 0L;
        List<SubcategoryResponse> subs = Collections.emptyList();
        if (subcategoryRepository != null) {
            count = subcategoryRepository.countByCategoryId(id);
            subs = subcategoryRepository.findByCategoryIdAndActiveTrueOrderByNameAsc(id).stream()
                    .map(s -> {
                        long prodCount = productRepository != null ? productRepository.countBySubcategoryId(s.getId()) : 0L;
                        return SubcategoryResponse.from(s, prodCount);
                    }).toList();
        }
        return CategoryResponse.from(c, count, subs);
    }

    @Transactional
    public CategoryResponse createCategory(CategoryRequest r) {
        if (r.name() == null || r.name().trim().isBlank()) {
            throw new BadRequestException("Category name cannot be blank.");
        }
        if (categories.existsByNameIgnoreCase(r.name().trim())) {
            throw new DuplicateResourceException("Category name is already in use.");
        }
        Category c = new Category(r.name().trim(), r.description());
        c.setImageUrl(r.imageUrl());
        if (r.active() != null) {
            c.setActive(r.active());
        }
        Category saved = categories.save(c);

        if (auditLogService != null) {
            auditLogService.logEvent(
                    AuditAction.CATEGORY_CREATE,
                    AuditResult.SUCCESS,
                    "CATEGORY",
                    String.valueOf(saved.getId()),
                    "Category created: " + saved.getName(),
                    null,
                    "{\"name\":\"" + saved.getName() + "\",\"active\":" + saved.isActive() + "}"
            );
        }

        return CategoryResponse.from(saved, 0L, Collections.emptyList());
    }

    @Transactional
    public CategoryResponse updateCategory(Long id, CategoryRequest r) {
        if (r.name() == null || r.name().trim().isBlank()) {
            throw new BadRequestException("Category name cannot be blank.");
        }
        Category c = categories.findById(id).orElseThrow(() -> new ResourceNotFoundException("Category not found."));
        String oldImageUrl = c.getImageUrl();
        String oldName = c.getName();
        c.setName(r.name().trim());
        c.setDescription(r.description());
        c.setImageUrl(r.imageUrl());
        if (r.active() != null) {
            c.setActive(r.active());
        }
        Category saved = categories.save(c);
        if (oldImageUrl != null && !oldImageUrl.equals(saved.getImageUrl())) {
            cleanupImageIfOrphaned(oldImageUrl);
        }

        if (auditLogService != null) {
            auditLogService.logEvent(
                    AuditAction.CATEGORY_UPDATE,
                    AuditResult.SUCCESS,
                    "CATEGORY",
                    String.valueOf(saved.getId()),
                    "Category updated: " + saved.getName(),
                    "{\"name\":\"" + oldName + "\"}",
                    "{\"name\":\"" + saved.getName() + "\",\"active\":" + saved.isActive() + "}"
            );
        }

        long count = subcategoryRepository != null ? subcategoryRepository.countByCategoryId(saved.getId()) : 0L;
        return CategoryResponse.from(saved, count, Collections.emptyList());
    }

    @Transactional
    public CategoryResponse updateCategoryImage(Long id, MultipartFile file) {
        if (fileStorageService == null) {
            throw new IllegalStateException("File storage service is not available.");
        }
        Category c = categories.findById(id).orElseThrow(() -> new ResourceNotFoundException("Category not found."));
        String oldImageUrl = c.getImageUrl();
        StoredFile stored = fileStorageService.storeFile(file);
        c.setImageUrl(stored.fullUrl());
        Category saved = categories.save(c);
        if (oldImageUrl != null && !oldImageUrl.equals(saved.getImageUrl())) {
            cleanupImageIfOrphaned(oldImageUrl);
        }
        long count = subcategoryRepository != null ? subcategoryRepository.countByCategoryId(saved.getId()) : 0L;
        return CategoryResponse.from(saved, count, Collections.emptyList());
    }

    @Transactional
    public void deleteCategory(Long id) {
        Category c = categories.findById(id).orElseThrow(() -> new ResourceNotFoundException("Category not found."));

        if (productRepository != null) {
            long productCount = productRepository.countByCategoryIdAndActiveTrue(id);
            if (productCount > 0) {
                throw new BadRequestException("Cannot delete category '" + c.getName() + "' because "
                        + productCount + " active product(s) are associated with it. Please reassign or delete products first.");
            }
        }

        if (subcategoryRepository != null) {
            long subcategoryCount = subcategoryRepository.countByCategoryId(id);
            if (subcategoryCount > 0) {
                throw new BadRequestException("Cannot delete category '" + c.getName() + "' because "
                        + subcategoryCount + " subcategory(ies) are associated with it. Please reassign or delete subcategories first.");
            }
        }

        c.setActive(false);
        categories.save(c);

        if (auditLogService != null) {
            auditLogService.logEvent(
                    AuditAction.CATEGORY_DELETE,
                    AuditResult.SUCCESS,
                    "CATEGORY",
                    String.valueOf(c.getId()),
                    "Category deactivated/archived: " + c.getName(),
                    "{\"active\":true}",
                    "{\"active\":false}"
            );
        }
    }

    private Map<Long, Long> getSubcategoryCountMap(boolean onlyActive) {
        if (subcategoryRepository == null) return Collections.emptyMap();
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

    private Map<Long, List<SubcategoryResponse>> getActiveSubcategoryMap() {
        if (subcategoryRepository == null) return Collections.emptyMap();
        List<Subcategory> activeSubcategories = subcategoryRepository.findByActiveTrueOrderByNameAsc();
        return activeSubcategories.stream()
                .map(s -> {
                    long prodCount = productRepository != null ? productRepository.countBySubcategoryIdAndActiveTrue(s.getId()) : 0L;
                    return SubcategoryResponse.from(s, prodCount);
                })
                .collect(Collectors.groupingBy(SubcategoryResponse::categoryId));
    }

    private void cleanupImageIfOrphaned(String imageUrl) {
        if (fileStorageService == null || imageUrl == null || imageUrl.isBlank()) {
            return;
        }
        if (!fileStorageService.isLocalUpload(imageUrl)) {
            return;
        }
        long categoryCount = categories.countByImageUrl(imageUrl);
        long productCount = (productRepository != null) ? productRepository.countByImageUrl(imageUrl) : 0;
        long subcategoryCount = (subcategoryRepository != null) ? subcategoryRepository.countByImageUrl(imageUrl) : 0;
        if (categoryCount == 0 && productCount == 0 && subcategoryCount == 0) {
            fileStorageService.deleteFileIfExists(imageUrl);
        }
    }
}
