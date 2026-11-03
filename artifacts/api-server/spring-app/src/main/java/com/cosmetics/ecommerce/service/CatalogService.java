package com.cosmetics.ecommerce.service;

import com.cosmetics.ecommerce.dto.CatalogDtos.*;
import com.cosmetics.ecommerce.dto.PageResponse;
import com.cosmetics.ecommerce.dto.StoredFile;
import com.cosmetics.ecommerce.entity.*;
import com.cosmetics.ecommerce.exception.*;
import com.cosmetics.ecommerce.repository.*;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
public class CatalogService {
    private final CategoryRepository categories;
    private final FileStorageService fileStorageService;
    private final ProductRepository productRepository;

    public CatalogService(CategoryRepository categories) {
        this(categories,null,null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public CatalogService(CategoryRepository categories,
                          FileStorageService fileStorageService, ProductRepository productRepository) {
        this.categories = categories;
        this.fileStorageService = fileStorageService;
        this.productRepository = productRepository;
    }

    public PageResponse<CategoryResponse> categories(Pageable pageable) {
        return PageResponse.from(categories.findByActiveTrueOrderByNameAsc(pageable).map(CategoryResponse::from));
    }

    public List<CategoryResponse> getAllCategory() {
        return categories.findAll(Sort.by(Sort.Direction.ASC, "id")).stream().map(CategoryResponse::from).toList();
    }

    public CategoryResponse category(Long id) {
        return CategoryResponse.from(categories.findById(id).orElseThrow(() -> new ResourceNotFoundException("Category not found.")));
    }




    @Transactional
    public CategoryResponse createCategory(CategoryRequest r) {
        if (categories.existsByNameIgnoreCase(r.name())) {
            throw new DuplicateResourceException("Category name is already in use.");
        }
        Category c = new Category(r.name().trim(), r.description());
        c.setImageUrl(r.imageUrl());
        if (r.active() != null) {
            c.setActive(r.active());
        }
        return CategoryResponse.from(categories.save(c));
    }

    @Transactional
    public CategoryResponse updateCategory(Long id, CategoryRequest r) {
        Category c = categories.findById(id).orElseThrow(() -> new ResourceNotFoundException("Category not found."));
        String oldImageUrl = c.getImageUrl();
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
        return CategoryResponse.from(saved);
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
        return CategoryResponse.from(saved);
    }

    @Transactional
    public void deleteCategory(Long id) {
        Category c = categories.findById(id).orElseThrow(() -> new ResourceNotFoundException("Category not found."));
        c.setActive(false);
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
        if (categoryCount == 0 && productCount == 0) {
            fileStorageService.deleteFileIfExists(imageUrl);
        }
    }
}
