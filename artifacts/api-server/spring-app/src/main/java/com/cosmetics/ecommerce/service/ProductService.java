package com.cosmetics.ecommerce.service;

import com.cosmetics.ecommerce.dto.*;
import com.cosmetics.ecommerce.dto.CatalogDtos.*;
import com.cosmetics.ecommerce.entity.*;
import com.cosmetics.ecommerce.enums.AuditAction;
import com.cosmetics.ecommerce.enums.AuditResult;
import com.cosmetics.ecommerce.exception.*;
import com.cosmetics.ecommerce.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.text.Normalizer;
import java.util.Locale;

@Service
@Transactional(readOnly = true)
public class ProductService {
    private final ProductRepository products;
    private final CategoryRepository categories;
    private final AuditLogService auditLogService;
    private final FileStorageService fileStorageService;
    private final SimilarProductService similarProductService;

    @org.springframework.beans.factory.annotation.Autowired
    public ProductService(ProductRepository products, CategoryRepository categories,
                          AuditLogService auditLogService,
                          FileStorageService fileStorageService,
                          @Autowired(required = false) SimilarProductService similarProductService) {
        this.products = products;
        this.categories = categories;
        this.auditLogService = auditLogService;
        this.fileStorageService = fileStorageService;
        this.similarProductService = similarProductService;
    }

    public ProductService(ProductRepository products, CategoryRepository categories,
                          AuditLogService auditLogService,
                          FileStorageService fileStorageService) {
        this(products, categories, auditLogService, fileStorageService, null);
    }

    public ProductService(ProductRepository products, CategoryRepository categories,
                          AuditLogService auditLogService) {
        this(products, categories, auditLogService, null, null);
    }

    public PageResponse<ProductResponse> list(String query, Long categoryId, Boolean featured,
                                               java.math.BigDecimal minPrice, java.math.BigDecimal maxPrice, Pageable pageable) {
        return list(query, categoryId, null, featured, minPrice, maxPrice, pageable);
    }

    public PageResponse<ProductResponse> list(String query, Long categoryId, Long subcategoryId, Boolean featured,
                                               java.math.BigDecimal minPrice, java.math.BigDecimal maxPrice, Pageable pageable) {
        Page<Product> page;
        if (query != null && !query.isBlank()) page = products.search(query.trim(), pageable);
        else if (subcategoryId != null) page = products.findBySubcategoryIdAndActiveTrue(subcategoryId, pageable);
        else if (categoryId != null) page = products.findByCategoryIdAndActiveTrue(categoryId, pageable);
        else if (Boolean.TRUE.equals(featured)) page = products.findByFeaturedTrueAndActiveTrue(pageable);
        else if (minPrice != null && maxPrice != null) page = products.findByPriceBetween(minPrice, maxPrice, pageable);
        else page = products.findByActiveTrue(pageable);
        return PageResponse.from(page.map(this::response));
    }

    public ProductResponse get(Long id) {
        Product p = products.findById(id).orElseThrow(() -> new ResourceNotFoundException("Product not found."));
        return responseWithSimilar(p);
    }

    public ProductResponse getBySlug(String slug) {
        Product p = products.findBySlug(slug).orElseThrow(() -> new ResourceNotFoundException("Product not found."));
        return responseWithSimilar(p);
    }

    @Transactional
    public ProductResponse create(ProductRequest request) {
        if (products.findBySku(request.sku()).isPresent()) throw new DuplicateResourceException("SKU is already in use.");
        Category category = categories.findById(request.categoryId()).orElseThrow(() -> new ResourceNotFoundException("Category not found."));
        Product p = new Product(request.name().trim(), uniqueSlug(request.name()), request.sku().trim(), request.price(),
                request.stockQuantity(), category);
        copy(request, p);
        Product saved = products.save(p);

        if (auditLogService != null) {
            String state = String.format("{\"name\":\"%s\",\"sku\":\"%s\",\"price\":%s,\"stock\":%d,\"categoryId\":%d,\"active\":%s}",
                    saved.getName(), saved.getSku(), saved.getPrice(), saved.getStockQuantity(), category.getId(), saved.isActive());
            auditLogService.logEvent(AuditAction.PRODUCT_CREATE, AuditResult.SUCCESS, "PRODUCT", String.valueOf(saved.getId()),
                    "Product created: " + saved.getName(), null, state);
        }

        return response(saved);
    }

    @Transactional
    public ProductResponse update(Long id, ProductRequest request) {
        Product p = products.findById(id).orElseThrow(() -> new ResourceNotFoundException("Product not found."));
        products.findBySku(request.sku()).filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> { throw new DuplicateResourceException("SKU is already in use."); });

        String oldImageUrl = p.getImageUrl();
        String prevState = String.format("{\"name\":\"%s\",\"sku\":\"%s\",\"price\":%s,\"discountPrice\":%s,\"stock\":%d,\"active\":%s}",
                p.getName(), p.getSku(), p.getPrice(), p.getDiscountPrice(), p.getStockQuantity(), p.isActive());

        p.setName(request.name().trim());
        p.setSku(request.sku().trim());
        p.setPrice(request.price());
        p.setStockQuantity(request.stockQuantity());
        p.setCategory(categories.findById(request.categoryId()).orElseThrow(() -> new ResourceNotFoundException("Category not found.")));
        copy(request, p);

        Product saved = products.save(p);

        if (oldImageUrl != null && !oldImageUrl.equals(saved.getImageUrl())) {
            cleanupImageIfOrphaned(oldImageUrl);
        }

        if (auditLogService != null) {
            String newState = String.format("{\"name\":\"%s\",\"sku\":\"%s\",\"price\":%s,\"discountPrice\":%s,\"stock\":%d,\"active\":%s}",
                    saved.getName(), saved.getSku(), saved.getPrice(), saved.getDiscountPrice(), saved.getStockQuantity(), saved.isActive());
            auditLogService.logEvent(AuditAction.PRODUCT_UPDATE, AuditResult.SUCCESS, "PRODUCT", String.valueOf(saved.getId()),
                    "Product updated: " + saved.getName(), prevState, newState);
        }

        return response(saved);
    }

    @Transactional
    public ProductResponse updateProductImage(Long id, MultipartFile file) {
        if (fileStorageService == null) {
            throw new IllegalStateException("File storage service is not available.");
        }
        Product p = products.findById(id).orElseThrow(() -> new ResourceNotFoundException("Product not found."));
        String oldImageUrl = p.getImageUrl();
        StoredFile stored = fileStorageService.storeFile(file);
        p.setImageUrl(stored.fullUrl());
        Product saved = products.save(p);

        if (oldImageUrl != null && !oldImageUrl.equals(saved.getImageUrl())) {
            cleanupImageIfOrphaned(oldImageUrl);
        }

        if (auditLogService != null) {
            auditLogService.logEvent(AuditAction.PRODUCT_UPDATE, AuditResult.SUCCESS, "PRODUCT", String.valueOf(saved.getId()),
                    "Product image updated: " + saved.getName(), oldImageUrl, saved.getImageUrl());
        }

        return response(saved);
    }

    @Transactional
    public void delete(Long id) {
        Product p = products.findById(id).orElseThrow(() -> new ResourceNotFoundException("Product not found."));
        boolean wasActive = p.isActive();
        p.setActive(false);

        if (auditLogService != null) {
            auditLogService.logEvent(AuditAction.PRODUCT_DELETE, AuditResult.SUCCESS, "PRODUCT", String.valueOf(p.getId()),
                    "Product deactivated/archived: " + p.getName(),
                    "{\"active\":" + wasActive + "}", "{\"active\":false}");
        }
    }

    @Transactional
    public ProductResponse updateStock(Long id, int stock) {
        if (stock < 0) throw new BadRequestException("Stock cannot be negative.");
        Product p = products.findById(id).orElseThrow(() -> new ResourceNotFoundException("Product not found."));
        int oldStock = p.getStockQuantity();
        p.setStockQuantity(stock);

        if (auditLogService != null) {
            auditLogService.logEvent(AuditAction.PRODUCT_STOCK_UPDATE, AuditResult.SUCCESS, "PRODUCT", String.valueOf(p.getId()),
                    "Stock updated for product " + p.getName() + " from " + oldStock + " to " + stock,
                    "{\"stockQuantity\":" + oldStock + "}", "{\"stockQuantity\":" + stock + "}");
        }

        return response(p);
    }

    private void cleanupImageIfOrphaned(String imageUrl) {
        if (fileStorageService == null || imageUrl == null || imageUrl.isBlank()) {
            return;
        }
        if (!fileStorageService.isLocalUpload(imageUrl)) {
            return;
        }
        long productCount = products.countByImageUrl(imageUrl);
        long categoryCount = categories.countByImageUrl(imageUrl);
        if (productCount == 0 && categoryCount == 0) {
            fileStorageService.deleteFileIfExists(imageUrl);
        }
    }

    private void copy(ProductRequest r, Product p) {
        if (r.discountPrice() != null && r.discountPrice().compareTo(r.price()) >= 0) {
            throw new BadRequestException("Discount price must be less than regular price.");
        }
        p.setDiscountPrice(r.discountPrice()); p.setLowStockThreshold(r.lowStockThreshold()); p.setDescription(r.description());
        p.setShortDescription(r.shortDescription()); p.setImageUrl(r.imageUrl()); p.setActive(r.active() == null || r.active());
        p.setFeatured(Boolean.TRUE.equals(r.featured())); p.setIngredients(r.ingredients()); p.setBenefits(r.benefits());
        p.setHowToUse(r.howToUse()); p.setSkinType(r.skinType()); p.setShade(r.shade()); p.setSize(r.size()); p.setExpiryDate(r.expiryDate());
    }

    public ProductResponse response(Product p) {
        Double rating = products.averageRating(p.getId());
        return ProductResponse.from(p, rating == null ? 0d : rating);
    }

    public ProductResponse responseWithSimilar(Product p) {
        Double rating = products.averageRating(p.getId());
        java.util.List<ProductResponse> similarList = java.util.Collections.emptyList();
        if (similarProductService != null) {
            try {
                similarList = similarProductService.findTopSimilarProducts(p.getId(), 4).stream()
                        .map(sp -> new ProductResponse(
                                sp.id(), sp.name(), sp.slug(), sp.sku(), sp.description(), sp.shortDescription(),
                                sp.price(), sp.discountPrice(), sp.sellingPrice(), sp.stockQuantity(),
                                sp.lowStockThreshold(), sp.categoryId(), sp.categoryName(), sp.imageUrl(),
                                sp.active(), sp.featured(), null, null, null, sp.skinType(), sp.shade(),
                                sp.size(), sp.expiryDate(), sp.averageRating(), sp.brandId(), sp.brandName(),
                                sp.relationshipType(), java.util.Collections.emptyList()
                        ))
                        .toList();
            } catch (Exception ignored) {}
        }
        return ProductResponse.from(p, rating == null ? 0d : rating, similarList);
    }

    public java.util.List<ProductResponse> inventory(boolean outOfStock) {
        return products.findAll().stream()
                .filter(p -> outOfStock ? p.getStockQuantity() == 0 : p.getStockQuantity() <= p.getLowStockThreshold())
                .map(this::response).toList();
    }

    public java.util.List<ProductResponse> allInventory() {
        return products.findAll().stream().map(this::response).toList();
    }

    private String uniqueSlug(String input) {
        String base = Normalizer.normalize(input.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        String slug = base; int suffix = 2;
        while (products.findBySlug(slug).isPresent()) slug = base + "-" + suffix++;
        return slug;
    }
}
