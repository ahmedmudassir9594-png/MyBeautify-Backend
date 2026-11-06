package com.cosmetics.ecommerce.service;

import com.cosmetics.ecommerce.dto.CartDtos.CartResponse;
import com.cosmetics.ecommerce.dto.CatalogDtos.ProductResponse;
import com.cosmetics.ecommerce.dto.ComboDtos.*;
import com.cosmetics.ecommerce.dto.PageResponse;
import com.cosmetics.ecommerce.dto.StoredFile;
import com.cosmetics.ecommerce.entity.*;
import com.cosmetics.ecommerce.enums.AuditAction;
import com.cosmetics.ecommerce.enums.AuditResult;
import com.cosmetics.ecommerce.enums.ComboType;
import com.cosmetics.ecommerce.exception.BadRequestException;
import com.cosmetics.ecommerce.exception.DuplicateResourceException;
import com.cosmetics.ecommerce.exception.InsufficientStockException;
import com.cosmetics.ecommerce.exception.ResourceNotFoundException;
import com.cosmetics.ecommerce.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.Instant;
import java.util.*;

@Service
@Transactional
public class ComboService {

    private static final Logger log = LoggerFactory.getLogger(ComboService.class);

    private final ComboRepository comboRepository;
    private final ComboItemRepository comboItemRepository;
    private final ComboDiscountRuleRepository ruleRepository;
    private final ProductRepository productRepository;
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ComboPricingService pricingService;
    private final AuditLogService auditLogService;
    private final FileStorageService fileStorageService;
    private final CategoryRepository categoryRepository;

    @Autowired
    public ComboService(ComboRepository comboRepository,
                        ComboItemRepository comboItemRepository,
                        ComboDiscountRuleRepository ruleRepository,
                        ProductRepository productRepository,
                        CartRepository cartRepository,
                        CartItemRepository cartItemRepository,
                        ComboPricingService pricingService,
                        @Autowired(required = false) AuditLogService auditLogService,
                        @Autowired(required = false) FileStorageService fileStorageService,
                        @Autowired(required = false) CategoryRepository categoryRepository) {
        this.comboRepository = comboRepository;
        this.comboItemRepository = comboItemRepository;
        this.ruleRepository = ruleRepository;
        this.productRepository = productRepository;
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.pricingService = pricingService;
        this.auditLogService = auditLogService;
        this.fileStorageService = fileStorageService;
        this.categoryRepository = categoryRepository;
    }

    // --- Public Customer Facing APIs ---

    @Transactional(readOnly = true)
    public PageResponse<ComboResponse> getActiveCombos(Pageable pageable) {
        Page<Combo> page = comboRepository.findActiveAndValid(Instant.now(), pageable);
        return PageResponse.from(page.map(ComboResponse::from));
    }

    @Transactional(readOnly = true)
    public List<ComboResponse> getFeaturedCombos() {
        return comboRepository.findFeaturedActiveAndValid(Instant.now()).stream()
                .map(ComboResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ComboResponse getComboBySlug(String slug) {
        Combo combo = comboRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Combo not found with slug: " + slug));
        if (!combo.isValidNow(Instant.now())) {
            throw new ResourceNotFoundException("This combo is currently unavailable or expired.");
        }
        return ComboResponse.from(combo);
    }

    @Transactional(readOnly = true)
    public ComboResponse getComboById(Long id) {
        Combo combo = comboRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Combo not found with id: " + id));
        return ComboResponse.from(combo);
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> getCustomComboEligibleProducts(Pageable pageable) {
        Page<Product> activeProducts = productRepository.findByActiveTrue(pageable);
        return PageResponse.from(activeProducts.map(p -> {
            Double rating = productRepository.averageRating(p.getId());
            return ProductResponse.from(p, rating == null ? 0d : rating);
        }));
    }

    @Transactional(readOnly = true)
    public CustomComboPreviewResponse previewCustomCombo(CustomComboPreviewRequest request) {
        if (request.items() == null || request.items().isEmpty()) {
            throw new BadRequestException("At least one product must be selected to preview a combo.");
        }

        Map<Long, Product> productMap = new HashMap<>();
        for (CustomComboItemRequest itemReq : request.items()) {
            if (itemReq.quantity() < 1) {
                throw new BadRequestException("Quantity must be at least 1 for product ID: " + itemReq.productId());
            }
            Product p = productRepository.findById(itemReq.productId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found with ID: " + itemReq.productId()));
            if (!p.isActive()) {
                throw new BadRequestException("Product '" + p.getName() + "' is currently unavailable.");
            }
            productMap.put(p.getId(), p);
        }

        ComboPricingService.CalculatedComboPricing pricing =
                pricingService.calculateCustomCombo(request.items(), productMap, Instant.now());

        List<CustomComboItemPreview> itemPreviews = new ArrayList<>();
        int totalQuantity = 0;

        for (ComboPricingService.AllocatedItemPricing item : pricing.items()) {
            Product p = item.product();
            boolean inStock = p.getStockQuantity() >= item.quantity();
            totalQuantity += item.quantity();

            itemPreviews.add(new CustomComboItemPreview(
                    item.productId(),
                    p.getName(),
                    p.getImageUrl(),
                    item.quantity(),
                    item.originalUnitPrice(),
                    item.originalSubtotal(),
                    item.discountedUnitPrice(),
                    item.allocatedDiscount(),
                    inStock,
                    p.getStockQuantity()
            ));
        }

        return new CustomComboPreviewResponse(
                itemPreviews,
                totalQuantity,
                request.items().size(),
                pricing.originalTotal(),
                pricing.discountAmount(),
                pricing.finalTotal(),
                pricing.discountPercentage(),
                pricing.appliedRuleName(),
                pricing.eligible(),
                pricing.eligibleMessage()
        );
    }

    // --- Cart Integration ---

    public CartResponse addPredefinedComboToCart(User user, Long comboId, int comboQuantity) {
        if (comboQuantity < 1) {
            throw new BadRequestException("Combo quantity must be at least 1.");
        }

        Combo combo = comboRepository.findById(comboId)
                .orElseThrow(() -> new ResourceNotFoundException("Combo not found with id: " + comboId));

        if (!combo.isValidNow(Instant.now())) {
            throw new BadRequestException("This combo is currently unavailable or expired.");
        }

        if (combo.getItems().isEmpty()) {
            throw new BadRequestException("This combo has no items configured.");
        }

        // Validate stock for all component items
        for (ComboItem item : combo.getItems()) {
            Product p = item.getProduct();
            if (!p.isActive()) {
                throw new BadRequestException("Product '" + p.getName() + "' in this combo is no longer available.");
            }
            int requiredStock = item.getQuantity() * comboQuantity;
            if (p.getStockQuantity() < requiredStock) {
                throw new InsufficientStockException("Insufficient stock for '" + p.getName() + "'. Required: "
                        + requiredStock + ", Available: " + p.getStockQuantity());
            }
        }

        Cart cart = getOrCreateCart(user);
        String comboGroupId = UUID.randomUUID().toString();

        List<ComboPricingService.AllocatedItemPricing> allocatedList =
                pricingService.allocatePredefinedCombo(combo, comboQuantity);

        for (ComboPricingService.AllocatedItemPricing allocated : allocatedList) {
            CartItem cartItem = new CartItem(
                    cart,
                    allocated.product(),
                    allocated.quantity(),
                    allocated.discountedUnitPrice(),
                    combo,
                    comboGroupId,
                    ComboType.PREDEFINED,
                    combo.getName(),
                    allocated.originalUnitPrice(),
                    allocated.allocatedDiscount()
            );
            cart.getItems().add(cartItem);
        }

        cart.touch();
        Cart saved = cartRepository.save(cart);
        return CartResponse.from(saved);
    }

    public CartResponse addCustomComboToCart(User user, AddCustomComboToCartRequest request) {
        if (request.items() == null || request.items().isEmpty()) {
            throw new BadRequestException("Custom combo cannot be empty.");
        }
        if (request.items().size() < 2) {
            throw new BadRequestException("Select at least 2 distinct products to build a custom combo.");
        }

        Map<Long, Product> productMap = new HashMap<>();
        for (CustomComboItemRequest itemReq : request.items()) {
            if (itemReq.quantity() < 1) {
                throw new BadRequestException("Quantity must be at least 1.");
            }
            Product p = productRepository.findById(itemReq.productId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found with ID: " + itemReq.productId()));
            if (!p.isActive()) {
                throw new BadRequestException("Product '" + p.getName() + "' is currently unavailable.");
            }
            if (p.getStockQuantity() < itemReq.quantity()) {
                throw new InsufficientStockException("Insufficient stock for '" + p.getName() + "'. Available: " + p.getStockQuantity());
            }
            productMap.put(p.getId(), p);
        }

        ComboPricingService.CalculatedComboPricing pricing =
                pricingService.calculateCustomCombo(request.items(), productMap, Instant.now());

        Cart cart = getOrCreateCart(user);
        String comboGroupId = UUID.randomUUID().toString();
        String comboName = pricing.appliedRuleName() != null ? ("Custom Combo (" + pricing.appliedRuleName() + ")") : "Custom Combo";

        for (ComboPricingService.AllocatedItemPricing allocated : pricing.items()) {
            CartItem cartItem = new CartItem(
                    cart,
                    allocated.product(),
                    allocated.quantity(),
                    allocated.discountedUnitPrice(),
                    null,
                    comboGroupId,
                    ComboType.CUSTOM,
                    comboName,
                    allocated.originalUnitPrice(),
                    allocated.allocatedDiscount()
            );
            cart.getItems().add(cartItem);
        }

        cart.touch();
        Cart saved = cartRepository.save(cart);
        return CartResponse.from(saved);
    }

    public CartResponse removeComboGroupFromCart(User user, String comboGroupId) {
        Cart cart = getOrCreateCart(user);
        List<CartItem> toRemove = cart.getItems().stream()
                .filter(item -> comboGroupId.equals(item.getComboGroupId()))
                .toList();

        if (toRemove.isEmpty()) {
            throw new ResourceNotFoundException("Combo group not found in your cart.");
        }

        cart.getItems().removeAll(toRemove);
        cartItemRepository.deleteAll(toRemove);
        cart.touch();
        Cart saved = cartRepository.save(cart);
        return CartResponse.from(saved);
    }

    // --- Admin Predefined Combo CRUD ---

    @Transactional(readOnly = true)
    public PageResponse<ComboResponse> getAllCombos(String query, Pageable pageable) {
        Page<Combo> page;
        if (query != null && !query.isBlank()) {
            page = comboRepository.findByNameContainingIgnoreCase(query.trim(), pageable);
        } else {
            page = comboRepository.findAll(pageable);
        }
        return PageResponse.from(page.map(ComboResponse::from));
    }

    public ComboResponse createCombo(ComboRequest request) {
        String trimmedName = request.name().trim();
        if (comboRepository.existsByNameIgnoreCase(trimmedName)) {
            throw new DuplicateResourceException("A combo with name '" + trimmedName + "' already exists.");
        }

        String slug = (request.slug() != null && !request.slug().isBlank())
                ? slugify(request.slug().trim())
                : uniqueSlug(trimmedName, null);

        if (comboRepository.existsBySlugIgnoreCase(slug)) {
            throw new DuplicateResourceException("A combo with slug '" + slug + "' already exists.");
        }

        Combo combo = new Combo(trimmedName, slug, request.description(), request.sellingPrice());
        combo.setImageFileName(sanitizeFileName(request.imageFileName()));
        combo.setStartDate(request.startDate());
        combo.setEndDate(request.endDate());
        combo.setActive(request.active());
        combo.setFeatured(request.featured());

        populateComboItems(combo, request.items());
        if (request.originalPrice() != null && request.originalPrice().compareTo(BigDecimal.ZERO) > 0) {
            combo.setOriginalPrice(request.originalPrice());
            combo.setDiscountAmount(request.originalPrice().subtract(request.sellingPrice()).max(BigDecimal.ZERO));
        } else {
            combo.recalculatePricing();
        }

        Combo saved = comboRepository.save(combo);

        if (auditLogService != null) {
            String newState = String.format("{\"id\":%d,\"name\":\"%s\",\"slug\":\"%s\",\"sellingPrice\":%s,\"originalPrice\":%s,\"active\":%s}",
                    saved.getId(), saved.getName(), saved.getSlug(), saved.getSellingPrice(), saved.getOriginalPrice(), saved.isActive());
            auditLogService.logEvent(AuditAction.COMBO_CREATE, AuditResult.SUCCESS, "COMBO",
                    String.valueOf(saved.getId()), "Combo created: " + saved.getName(), null, newState);
        }

        return ComboResponse.from(saved);
    }

    public ComboResponse createComboWithMultipart(ComboRequest request, MultipartFile file) {
        String imageFileName = sanitizeFileName(request.imageFileName());
        if (file != null && !file.isEmpty()) {
            if (fileStorageService == null) {
                throw new IllegalStateException("File storage service is not available.");
            }
            StoredFile stored = fileStorageService.storeFile(file);
            imageFileName = stored.fileName();
        }

        ComboRequest updatedRequest = new ComboRequest(
                request.name(),
                request.slug(),
                request.description(),
                imageFileName,
                request.sellingPrice(),
                request.originalPrice(),
                request.startDate(),
                request.endDate(),
                request.active(),
                request.featured(),
                request.items()
        );

        return createCombo(updatedRequest);
    }

    public ComboResponse updateCombo(Long id, ComboRequest request) {
        Combo combo = comboRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Combo not found with id: " + id));

        String trimmedName = request.name().trim();
        if (comboRepository.existsByNameIgnoreCaseAndIdNot(trimmedName, id)) {
            throw new DuplicateResourceException("A combo with name '" + trimmedName + "' already exists.");
        }

        String slug = (request.slug() != null && !request.slug().isBlank())
                ? slugify(request.slug().trim())
                : uniqueSlug(trimmedName, id);

        if (comboRepository.existsBySlugIgnoreCaseAndIdNot(slug, id)) {
            throw new DuplicateResourceException("A combo with slug '" + slug + "' already exists.");
        }

        String prevState = String.format("{\"id\":%d,\"name\":\"%s\",\"sellingPrice\":%s,\"active\":%s}",
                combo.getId(), combo.getName(), combo.getSellingPrice(), combo.isActive());

        String oldImageFileName = combo.getImageFileName();
        String newImageFileName = request.imageFileName() != null ? sanitizeFileName(request.imageFileName()) : oldImageFileName;

        combo.setName(trimmedName);
        combo.setSlug(slug);
        combo.setDescription(request.description());
        combo.setImageFileName(newImageFileName);
        combo.setSellingPrice(request.sellingPrice());
        combo.setStartDate(request.startDate());
        combo.setEndDate(request.endDate());
        combo.setActive(request.active());
        combo.setFeatured(request.featured());

        // Replace items
        combo.getItems().clear();
        populateComboItems(combo, request.items());

        if (request.originalPrice() != null && request.originalPrice().compareTo(BigDecimal.ZERO) > 0) {
            combo.setOriginalPrice(request.originalPrice());
            combo.setDiscountAmount(request.originalPrice().subtract(request.sellingPrice()).max(BigDecimal.ZERO));
        } else {
            combo.recalculatePricing();
        }

        Combo saved = comboRepository.saveAndFlush(combo);

        if (oldImageFileName != null && !oldImageFileName.equals(saved.getImageFileName())) {
            cleanupImageIfOrphaned(oldImageFileName);
        }

        if (auditLogService != null) {
            String newState = String.format("{\"id\":%d,\"name\":\"%s\",\"sellingPrice\":%s,\"active\":%s}",
                    saved.getId(), saved.getName(), saved.getSellingPrice(), saved.isActive());
            auditLogService.logEvent(AuditAction.COMBO_UPDATE, AuditResult.SUCCESS, "COMBO",
                    String.valueOf(saved.getId()), "Combo updated: " + saved.getName(), prevState, newState);
        }

        return ComboResponse.from(saved);
    }

    public ComboResponse updateComboWithMultipart(Long id, ComboRequest request, MultipartFile file) {
        Combo combo = comboRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Combo not found with id: " + id));

        String oldImageFileName = combo.getImageFileName();
        String newImageFileName;

        if (file != null && !file.isEmpty()) {
            if (fileStorageService == null) {
                throw new IllegalStateException("File storage service is not available.");
            }
            StoredFile stored = fileStorageService.storeFile(file);
            newImageFileName = stored.fileName();
        } else if (request.imageFileName() != null && !request.imageFileName().isBlank()) {
            newImageFileName = sanitizeFileName(request.imageFileName());
        } else {
            // Retain existing image when no new file or filename is provided
            newImageFileName = oldImageFileName;
        }

        ComboRequest updatedRequest = new ComboRequest(
                request.name(),
                request.slug(),
                request.description(),
                newImageFileName,
                request.sellingPrice(),
                request.originalPrice(),
                request.startDate(),
                request.endDate(),
                request.active(),
                request.featured(),
                request.items()
        );

        return updateCombo(id, updatedRequest);
    }

    public ComboResponse updateComboImage(Long id, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Uploaded file cannot be empty.");
        }
        if (fileStorageService == null) {
            throw new IllegalStateException("File storage service is not available.");
        }

        Combo combo = comboRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Combo not found with id: " + id));

        String oldImageFileName = combo.getImageFileName();
        StoredFile stored = fileStorageService.storeFile(file);
        combo.setImageFileName(stored.fileName());

        Combo saved = comboRepository.saveAndFlush(combo);

        if (oldImageFileName != null && !oldImageFileName.equals(saved.getImageFileName())) {
            cleanupImageIfOrphaned(oldImageFileName);
        }

        if (auditLogService != null) {
            auditLogService.logEvent(AuditAction.COMBO_UPDATE, AuditResult.SUCCESS, "COMBO",
                    String.valueOf(saved.getId()), "Combo image updated: " + saved.getName(),
                    oldImageFileName, saved.getImageFileName());
        }

        return ComboResponse.from(saved);
    }

    public ComboResponse updateComboStatus(Long id, boolean active) {
        Combo combo = comboRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Combo not found with id: " + id));

        boolean prevActive = combo.isActive();
        combo.setActive(active);
        Combo saved = comboRepository.save(combo);

        if (auditLogService != null) {
            auditLogService.logEvent(AuditAction.COMBO_STATUS_CHANGE, AuditResult.SUCCESS, "COMBO",
                    String.valueOf(saved.getId()), "Combo status updated to active=" + active,
                    "{\"active\":" + prevActive + "}", "{\"active\":" + active + "}");
        }

        return ComboResponse.from(saved);
    }

    public void deleteCombo(Long id) {
        Combo combo = comboRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Combo not found with id: " + id));

        String oldImageFileName = combo.getImageFileName();
        String state = String.format("{\"id\":%d,\"name\":\"%s\"}", combo.getId(), combo.getName());

        comboRepository.delete(combo);
        comboRepository.flush();

        if (auditLogService != null) {
            auditLogService.logEvent(AuditAction.COMBO_DELETE, AuditResult.SUCCESS, "COMBO",
                    String.valueOf(id), "Combo deleted: " + combo.getName(), state, null);
        }

        if (oldImageFileName != null) {
            cleanupImageIfOrphaned(oldImageFileName);
        }
    }

    // --- Admin Discount Rules CRUD ---

    @Transactional(readOnly = true)
    public List<ComboDiscountRuleResponse> getAllDiscountRules() {
        return ruleRepository.findAll().stream()
                .map(ComboDiscountRuleResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ComboDiscountRuleResponse getDiscountRule(Long id) {
        ComboDiscountRule rule = ruleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Discount rule not found with id: " + id));
        return ComboDiscountRuleResponse.from(rule);
    }

    public ComboDiscountRuleResponse createDiscountRule(ComboDiscountRuleRequest request) {
        ComboDiscountRule rule = new ComboDiscountRule(
                request.name().trim(),
                request.minProducts(),
                request.discountType(),
                request.discountValue()
        );
        rule.setDescription(request.description());
        rule.setMinSubtotal(request.minSubtotal());
        rule.setMaxDiscountAmount(request.maxDiscountAmount());
        rule.setStartDate(request.startDate());
        rule.setEndDate(request.endDate());
        rule.setActive(request.active());

        ComboDiscountRule saved = ruleRepository.save(rule);
        return ComboDiscountRuleResponse.from(saved);
    }

    public ComboDiscountRuleResponse updateDiscountRule(Long id, ComboDiscountRuleRequest request) {
        ComboDiscountRule rule = ruleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Discount rule not found with id: " + id));

        rule.setName(request.name().trim());
        rule.setDescription(request.description());
        rule.setMinProducts(request.minProducts());
        rule.setDiscountType(request.discountType());
        rule.setDiscountValue(request.discountValue());
        rule.setMinSubtotal(request.minSubtotal());
        rule.setMaxDiscountAmount(request.maxDiscountAmount());
        rule.setStartDate(request.startDate());
        rule.setEndDate(request.endDate());
        rule.setActive(request.active());

        ComboDiscountRule saved = ruleRepository.save(rule);
        return ComboDiscountRuleResponse.from(saved);
    }

    public void deleteDiscountRule(Long id) {
        ComboDiscountRule rule = ruleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Discount rule not found with id: " + id));
        ruleRepository.delete(rule);
    }

    // --- Helper Methods ---

    private void populateComboItems(Combo combo, List<ComboItemRequest> itemRequests) {
        for (ComboItemRequest req : itemRequests) {
            Product p = productRepository.findById(req.productId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found with ID: " + req.productId()));
            ComboItem item = new ComboItem(combo, p, req.quantity(), req.customPrice());
            combo.addItem(item);
        }
    }

    private Cart getOrCreateCart(User user) {
        return cartRepository.findByUserId(user.getId())
                .orElseGet(() -> cartRepository.save(new Cart(user)));
    }

    private String slugify(String input) {
        return Normalizer.normalize(input.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
    }

    private String uniqueSlug(String input, Long currentId) {
        String base = slugify(input);
        String slug = base;
        int suffix = 2;
        while (currentId == null
                ? comboRepository.existsBySlugIgnoreCase(slug)
                : comboRepository.existsBySlugIgnoreCaseAndIdNot(slug, currentId)) {
            slug = base + "-" + suffix++;
        }
        return slug;
    }

    private void cleanupImageIfOrphaned(String imageFileName) {
        if (fileStorageService == null || imageFileName == null || imageFileName.isBlank()) {
            return;
        }
        String cleanName = sanitizeFileName(imageFileName);
        if (cleanName == null || cleanName.isBlank()) {
            return;
        }

        // Check if any other combo references this imageFileName
        long comboCount = comboRepository.countByImageFileName(cleanName);
        if (comboCount > 0) {
            return;
        }
        if (!cleanName.equals(imageFileName) && comboRepository.countByImageFileName(imageFileName) > 0) {
            return;
        }

        // Also check if any product or category references this image (as URL or relative URL)
        String relativeUrl = "/api/uploads/" + cleanName;
        long productCount = productRepository.countByImageUrl(relativeUrl);
        long categoryCount = (categoryRepository != null) ? categoryRepository.countByImageUrl(relativeUrl) : 0;
        if (productCount > 0 || categoryCount > 0) {
            return;
        }

        String deletePath = "/uploads/" + cleanName;
        if (fileStorageService.isLocalUpload(deletePath)) {
            fileStorageService.deleteFileIfExists(deletePath);
        }
    }

    private String sanitizeFileName(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String s = raw.trim();
        int lastSlash = s.lastIndexOf('/');
        if (lastSlash != -1) {
            s = s.substring(lastSlash + 1);
        }
        int lastBackslash = s.lastIndexOf('\\');
        if (lastBackslash != -1) {
            s = s.substring(lastBackslash + 1);
        }
        return s.trim();
    }
}

