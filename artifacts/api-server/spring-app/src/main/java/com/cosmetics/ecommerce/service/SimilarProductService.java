package com.cosmetics.ecommerce.service;

import com.cosmetics.ecommerce.dto.PageResponse;
import com.cosmetics.ecommerce.dto.ProductRelationshipDtos.*;
import com.cosmetics.ecommerce.entity.Product;
import com.cosmetics.ecommerce.entity.ProductRelationship;
import com.cosmetics.ecommerce.enums.AuditAction;
import com.cosmetics.ecommerce.enums.AuditResult;
import com.cosmetics.ecommerce.enums.RelationshipType;
import com.cosmetics.ecommerce.exception.BadRequestException;
import com.cosmetics.ecommerce.exception.DuplicateResourceException;
import com.cosmetics.ecommerce.exception.ResourceNotFoundException;
import com.cosmetics.ecommerce.repository.ProductRelationshipRepository;
import com.cosmetics.ecommerce.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Transactional
public class SimilarProductService {

    private final ProductRepository productRepository;
    private final ProductRelationshipRepository relationshipRepository;
    private final AuditLogService auditLogService;

    private static final Pattern STYLE_KEYWORD_PATTERN = Pattern.compile(
            "\\b(pendant|necklace|choker|chain|earring|earrings|jhumka|studs|bangle|bangles|bracelet|ring|kundan|emerald|ruby|diamond|gold|pearl)\\b",
            Pattern.CASE_INSENSITIVE
    );

    @Autowired
    public SimilarProductService(ProductRepository productRepository,
                                 ProductRelationshipRepository relationshipRepository,
                                 @Autowired(required = false) AuditLogService auditLogService) {
        this.productRepository = productRepository;
        this.relationshipRepository = relationshipRepository;
        this.auditLogService = auditLogService;
    }

    private record RankedCandidate(Product product, String relationshipType, int displayOrder) {}

    // --- Public Methods ---

    @Transactional(readOnly = true)
    public PageResponse<SimilarProductResponse> findSimilarProducts(
            Long productId, int limit, int page, RelationshipType typeFilter) {

        if (limit <= 0) {
            limit = 4;
        }
        if (page < 0) {
            page = 0;
        }

        Product source = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with ID: " + productId));

        List<RankedCandidate> candidates = collectCandidates(source, limit, page, typeFilter);

        int totalElements = candidates.size();
        int totalPages = (totalElements == 0) ? 0 : (int) Math.ceil((double) totalElements / limit);
        int fromIndex = page * limit;

        List<SimilarProductResponse> content;
        if (fromIndex >= totalElements) {
            content = Collections.emptyList();
        } else {
            int toIndex = Math.min(fromIndex + limit, totalElements);
            content = candidates.subList(fromIndex, toIndex).stream()
                    .map(rc -> {
                        Double rating = productRepository.averageRating(rc.product().getId());
                        return SimilarProductResponse.from(
                                rc.product(),
                                rating != null ? rating : 0.0,
                                rc.relationshipType(),
                                rc.displayOrder()
                        );
                    })
                    .toList();
        }

        boolean first = (page == 0);
        boolean last = (page >= totalPages - 1);

        return new PageResponse<>(content, page, limit, totalElements, totalPages, first, last);
    }

    @Transactional(readOnly = true)
    public PageResponse<SimilarProductResponse> findSimilarProductsBySlug(
            String slug, int limit, int page, RelationshipType typeFilter) {
        Product source = productRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with slug: " + slug));
        return findSimilarProducts(source.getId(), limit, page, typeFilter);
    }

    @Transactional(readOnly = true)
    public List<SimilarProductResponse> findTopSimilarProducts(Long productId, int limit) {
        PageResponse<SimilarProductResponse> paged = findSimilarProducts(productId, limit, 0, null);
        return paged.content();
    }

    // --- Candidate Matching Algorithm ---

    private List<RankedCandidate> collectCandidates(
            Product source, int limit, int page, RelationshipType typeFilter) {

        int maxToCollect = Math.max(16, (page + 1) * limit * 2);
        Map<Long, RankedCandidate> candidateMap = new LinkedHashMap<>();

        // Priority 1: Explicit relationships configured by admin
        List<ProductRelationship> explicitList = relationshipRepository.findActiveRelatedProducts(source.getId(), typeFilter);
        for (ProductRelationship rel : explicitList) {
            Product related = rel.getRelatedProduct();
            if (related != null && related.isActive() && !related.getId().equals(source.getId())) {
                candidateMap.putIfAbsent(related.getId(), new RankedCandidate(
                        related, rel.getRelationshipType().name(), rel.getDisplayOrder()));
            }
        }

        // If explicit filter is specified and strictly requested (e.g. only VARIANT), don't auto-fill unless generic SIMILAR
        if (typeFilter != null && typeFilter != RelationshipType.SIMILAR) {
            return new ArrayList<>(candidateMap.values());
        }

        // Priority 2: Matching style/keyword within the same category
        Long categoryId = (source.getCategory() != null) ? source.getCategory().getId() : null;
        String styleKeyword = extractStyleKeyword(source.getName());

        if (categoryId != null && styleKeyword != null && candidateMap.size() < maxToCollect) {
            List<Product> styleMatches = productRepository.findCandidateSimilarProducts(
                    source.getId(), categoryId, styleKeyword, PageRequest.of(0, maxToCollect));
            int order = 100;
            for (Product p : styleMatches) {
                if (p.isActive() && !p.getId().equals(source.getId())) {
                    candidateMap.putIfAbsent(p.getId(), new RankedCandidate(p, "STYLE_MATCH", order++));
                }
            }
        }

        // Priority 3: Same category fallback
        if (categoryId != null && candidateMap.size() < maxToCollect) {
            List<Product> categoryMatches = productRepository.findByCategoryExcludingProduct(
                    categoryId, source.getId(), PageRequest.of(0, maxToCollect));
            int order = 200;
            for (Product p : categoryMatches) {
                if (p.isActive() && !p.getId().equals(source.getId())) {
                    candidateMap.putIfAbsent(p.getId(), new RankedCandidate(p, "CATEGORY_MATCH", order++));
                }
            }
        }

        // Priority 4: Catalog fallback (featured active products)
        if (candidateMap.size() < maxToCollect) {
            List<Product> fallback = productRepository.findCatalogFallbackProducts(
                    source.getId(), PageRequest.of(0, maxToCollect));
            int order = 300;
            for (Product p : fallback) {
                if (p.isActive() && !p.getId().equals(source.getId())) {
                    candidateMap.putIfAbsent(p.getId(), new RankedCandidate(p, "CATALOG_MATCH", order++));
                }
            }
        }

        return new ArrayList<>(candidateMap.values());
    }

    private String extractStyleKeyword(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Matcher matcher = STYLE_KEYWORD_PATTERN.matcher(text);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    // --- Admin Relationship Management ---

    @Transactional(readOnly = true)
    public List<ProductRelationshipResponse> getRelationshipsForProduct(Long productId) {
        if (!productRepository.existsById(productId)) {
            throw new ResourceNotFoundException("Source product not found with ID: " + productId);
        }
        return relationshipRepository.findBySourceProductIdOrderByDisplayOrderAscCreatedAtDesc(productId).stream()
                .map(ProductRelationshipResponse::from)
                .toList();
    }

    public ProductRelationshipResponse addRelationship(Long sourceProductId, ProductRelationshipRequest request) {
        if (request.relatedProductId() == null) {
            throw new BadRequestException("Related product ID is required.");
        }
        if (sourceProductId.equals(request.relatedProductId())) {
            throw new BadRequestException("A product cannot be related to itself.");
        }

        Product source = productRepository.findById(sourceProductId)
                .orElseThrow(() -> new ResourceNotFoundException("Source product not found with ID: " + sourceProductId));

        Product related = productRepository.findById(request.relatedProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Related product not found with ID: " + request.relatedProductId()));

        RelationshipType relType = request.relationshipType() != null ? request.relationshipType() : RelationshipType.SIMILAR;
        int order = request.displayOrder() != null ? request.displayOrder() : 0;

        if (relationshipRepository.existsBySourceProductIdAndRelatedProductIdAndRelationshipType(sourceProductId, request.relatedProductId(), relType)) {
            throw new DuplicateResourceException("Relationship of type " + relType + " already exists between product "
                    + sourceProductId + " and " + request.relatedProductId());
        }

        ProductRelationship relationship = new ProductRelationship(source, related, relType, order);
        ProductRelationship saved = relationshipRepository.save(relationship);

        if (auditLogService != null) {
            String newState = String.format("{\"sourceId\":%d,\"sourceName\":\"%s\",\"relatedId\":%d,\"relatedName\":\"%s\",\"type\":\"%s\",\"order\":%d}",
                    source.getId(), source.getName(), related.getId(), related.getName(), relType, order);
            auditLogService.logEvent(AuditAction.PRODUCT_RELATIONSHIP_CREATE, AuditResult.SUCCESS, "PRODUCT_RELATIONSHIP",
                    String.valueOf(saved.getId()), "Created product relationship: " + source.getName() + " -> " + related.getName(), null, newState);
        }

        return ProductRelationshipResponse.from(saved);
    }

    public void removeRelationship(Long sourceProductId, Long relatedProductId, RelationshipType type) {
        ProductRelationship relationship;
        if (type != null) {
            relationship = relationshipRepository.findBySourceProductIdAndRelatedProductIdAndRelationshipType(sourceProductId, relatedProductId, type)
                    .orElseThrow(() -> new ResourceNotFoundException("Relationship not found between source " + sourceProductId + " and related " + relatedProductId));
        } else {
            relationship = relationshipRepository.findBySourceProductIdAndRelatedProductId(sourceProductId, relatedProductId)
                    .orElseThrow(() -> new ResourceNotFoundException("Relationship not found between source " + sourceProductId + " and related " + relatedProductId));
        }

        Long relId = relationship.getId();
        relationshipRepository.delete(relationship);

        if (auditLogService != null) {
            auditLogService.logEvent(AuditAction.PRODUCT_RELATIONSHIP_DELETE, AuditResult.SUCCESS, "PRODUCT_RELATIONSHIP",
                    String.valueOf(relId), "Deleted product relationship between " + sourceProductId + " and " + relatedProductId, null, null);
        }
    }

    public void removeRelationshipById(Long relationshipId) {
        ProductRelationship relationship = relationshipRepository.findById(relationshipId)
                .orElseThrow(() -> new ResourceNotFoundException("Product relationship not found with ID: " + relationshipId));

        relationshipRepository.delete(relationship);

        if (auditLogService != null) {
            auditLogService.logEvent(AuditAction.PRODUCT_RELATIONSHIP_DELETE, AuditResult.SUCCESS, "PRODUCT_RELATIONSHIP",
                    String.valueOf(relationshipId), "Deleted product relationship with ID: " + relationshipId, null, null);
        }
    }
}

