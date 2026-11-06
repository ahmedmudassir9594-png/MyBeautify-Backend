package com.cosmetics.ecommerce;

import com.cosmetics.ecommerce.dto.PageResponse;
import com.cosmetics.ecommerce.dto.ProductRelationshipDtos.*;
import com.cosmetics.ecommerce.entity.Category;
import com.cosmetics.ecommerce.entity.Product;
import com.cosmetics.ecommerce.entity.ProductRelationship;
import com.cosmetics.ecommerce.enums.RelationshipType;
import com.cosmetics.ecommerce.exception.BadRequestException;
import com.cosmetics.ecommerce.exception.DuplicateResourceException;
import com.cosmetics.ecommerce.exception.ResourceNotFoundException;
import com.cosmetics.ecommerce.repository.ProductRelationshipRepository;
import com.cosmetics.ecommerce.repository.ProductRepository;
import com.cosmetics.ecommerce.service.AuditLogService;
import com.cosmetics.ecommerce.service.SimilarProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SimilarProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductRelationshipRepository relationshipRepository;

    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private SimilarProductService similarProductService;

    private Category pendantsCategory;
    private Product sourcePendant;
    private Product emeraldDrop;
    private Product floralGold;
    private Product classicRound;
    private Product goldenChain;

    @BeforeEach
    void setUp() {
        pendantsCategory = new Category("Pendants & Necklaces", "Handcrafted fine jewellery pendants");
        ReflectionTestUtils.setField(pendantsCategory, "id", 1L);

        sourcePendant = new Product("Plated Pendant Necklace", "plated-pendant-necklace", "SKU-MAIN",
                BigDecimal.valueOf(12.00), 43, pendantsCategory);
        ReflectionTestUtils.setField(sourcePendant, "id", 100L);
        sourcePendant.setImageUrl("https://malkaz.com/images/plated-pendant.jpg");

        emeraldDrop = new Product("Emerald Drop Pendant", "emerald-drop-pendant", "SKU-ED",
                BigDecimal.valueOf(15.00), 20, pendantsCategory);
        ReflectionTestUtils.setField(emeraldDrop, "id", 101L);
        emeraldDrop.setImageUrl("https://malkaz.com/images/emerald-drop.jpg");

        floralGold = new Product("Floral Gold Necklace", "floral-gold-necklace", "SKU-FG",
                BigDecimal.valueOf(18.00), 15, pendantsCategory);
        ReflectionTestUtils.setField(floralGold, "id", 102L);
        floralGold.setImageUrl("https://malkaz.com/images/floral-gold.jpg");

        classicRound = new Product("Classic Round Pendant", "classic-round-pendant", "SKU-CR",
                BigDecimal.valueOf(14.00), 25, pendantsCategory);
        ReflectionTestUtils.setField(classicRound, "id", 103L);
        classicRound.setImageUrl("https://malkaz.com/images/classic-round.jpg");

        goldenChain = new Product("Golden Chain Necklace", "golden-chain-necklace", "SKU-GC",
                BigDecimal.valueOf(16.00), 10, pendantsCategory);
        ReflectionTestUtils.setField(goldenChain, "id", 104L);
        goldenChain.setImageUrl("https://malkaz.com/images/golden-chain.jpg");
    }

    @Test
    @DisplayName("Priority 1: Explicitly associated variants are returned first")
    void testExplicitVariantsReturnedFirst() {
        when(productRepository.findById(100L)).thenReturn(Optional.of(sourcePendant));

        ProductRelationship rel1 = new ProductRelationship(sourcePendant, emeraldDrop, RelationshipType.VARIANT, 1);
        ProductRelationship rel2 = new ProductRelationship(sourcePendant, classicRound, RelationshipType.VARIANT, 2);

        when(relationshipRepository.findActiveRelatedProducts(eq(100L), isNull()))
                .thenReturn(List.of(rel1, rel2));
        when(productRepository.findCandidateSimilarProducts(eq(100L), eq(1L), anyString(), any()))
                .thenReturn(List.of(floralGold, goldenChain));
        when(productRepository.findByCategoryExcludingProduct(eq(1L), eq(100L), any()))
                .thenReturn(Collections.emptyList());
        when(productRepository.findCatalogFallbackProducts(eq(100L), any()))
                .thenReturn(Collections.emptyList());

        PageResponse<SimilarProductResponse> response = similarProductService.findSimilarProducts(100L, 4, 0, null);

        assertThat(response.content()).hasSize(4);
        assertThat(response.content().get(0).id()).isEqualTo(101L);
        assertThat(response.content().get(0).relationshipType()).isEqualTo("VARIANT");
        assertThat(response.content().get(1).id()).isEqualTo(103L);
        assertThat(response.content().get(1).relationshipType()).isEqualTo("VARIANT");
        assertThat(response.content().get(2).id()).isEqualTo(102L);
        assertThat(response.content().get(2).relationshipType()).isEqualTo("STYLE_MATCH");
        assertThat(response.content().get(3).id()).isEqualTo(104L);
        assertThat(response.content().get(3).relationshipType()).isEqualTo("STYLE_MATCH");
    }

    @Test
    @DisplayName("Current viewed product is strictly excluded from results")
    void testCurrentProductIsExcluded() {
        when(productRepository.findById(100L)).thenReturn(Optional.of(sourcePendant));
        when(relationshipRepository.findActiveRelatedProducts(eq(100L), isNull()))
                .thenReturn(Collections.emptyList());

        // Even if repository returned source product in candidates
        when(productRepository.findCandidateSimilarProducts(eq(100L), eq(1L), anyString(), any()))
                .thenReturn(List.of(sourcePendant, emeraldDrop));
        when(productRepository.findByCategoryExcludingProduct(eq(1L), eq(100L), any()))
                .thenReturn(Collections.emptyList());
        when(productRepository.findCatalogFallbackProducts(eq(100L), any()))
                .thenReturn(Collections.emptyList());

        PageResponse<SimilarProductResponse> response = similarProductService.findSimilarProducts(100L, 4, 0, null);

        assertThat(response.content()).hasSize(1);
        assertThat(response.content().get(0).id()).isEqualTo(101L);
        assertThat(response.content()).noneMatch(p -> p.id().equals(100L));
    }

    @Test
    @DisplayName("Inactive products are filtered out")
    void testInactiveProductsAreExcluded() {
        emeraldDrop.setActive(false);

        when(productRepository.findById(100L)).thenReturn(Optional.of(sourcePendant));
        when(relationshipRepository.findActiveRelatedProducts(eq(100L), isNull()))
                .thenReturn(Collections.emptyList());
        when(productRepository.findCandidateSimilarProducts(eq(100L), eq(1L), anyString(), any()))
                .thenReturn(List.of(emeraldDrop, floralGold));
        when(productRepository.findByCategoryExcludingProduct(eq(1L), eq(100L), any()))
                .thenReturn(Collections.emptyList());
        when(productRepository.findCatalogFallbackProducts(eq(100L), any()))
                .thenReturn(Collections.emptyList());

        PageResponse<SimilarProductResponse> response = similarProductService.findSimilarProducts(100L, 4, 0, null);

        assertThat(response.content()).hasSize(1);
        assertThat(response.content().get(0).id()).isEqualTo(102L); // floralGold
    }

    @Test
    @DisplayName("Pagination limits results to requested page size")
    void testPaginationLimit() {
        when(productRepository.findById(100L)).thenReturn(Optional.of(sourcePendant));
        when(relationshipRepository.findActiveRelatedProducts(eq(100L), isNull()))
                .thenReturn(Collections.emptyList());
        when(productRepository.findCandidateSimilarProducts(eq(100L), eq(1L), anyString(), any()))
                .thenReturn(List.of(emeraldDrop, floralGold, classicRound, goldenChain));
        when(productRepository.findByCategoryExcludingProduct(eq(1L), eq(100L), any()))
                .thenReturn(Collections.emptyList());
        when(productRepository.findCatalogFallbackProducts(eq(100L), any()))
                .thenReturn(Collections.emptyList());

        // Request page 0, limit 2
        PageResponse<SimilarProductResponse> page0 = similarProductService.findSimilarProducts(100L, 2, 0, null);
        assertThat(page0.content()).hasSize(2);
        assertThat(page0.content().get(0).id()).isEqualTo(101L);
        assertThat(page0.content().get(1).id()).isEqualTo(102L);
        assertThat(page0.totalPages()).isEqualTo(2);
        assertThat(page0.first()).isTrue();
        assertThat(page0.last()).isFalse();

        // Request page 1, limit 2
        PageResponse<SimilarProductResponse> page1 = similarProductService.findSimilarProducts(100L, 2, 1, null);
        assertThat(page1.content()).hasSize(2);
        assertThat(page1.content().get(0).id()).isEqualTo(103L);
        assertThat(page1.content().get(1).id()).isEqualTo(104L);
        assertThat(page1.first()).isFalse();
        assertThat(page1.last()).isTrue();
    }

    @Test
    @DisplayName("Nonexistent product throws ResourceNotFoundException")
    void testNonexistentProductThrowsException() {
        when(productRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> similarProductService.findSimilarProducts(999L, 4, 0, null))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Product not found with ID: 999");
    }

    @Test
    @DisplayName("Adding relationship to self is rejected")
    void testSelfReferencingRelationshipRejected() {
        ProductRelationshipRequest req = new ProductRelationshipRequest(100L, RelationshipType.VARIANT, 1);

        assertThatThrownBy(() -> similarProductService.addRelationship(100L, req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("A product cannot be related to itself");
    }

    @Test
    @DisplayName("Duplicate relationship is rejected with DuplicateResourceException")
    void testDuplicateRelationshipRejected() {
        when(productRepository.findById(100L)).thenReturn(Optional.of(sourcePendant));
        when(productRepository.findById(101L)).thenReturn(Optional.of(emeraldDrop));
        when(relationshipRepository.existsBySourceProductIdAndRelatedProductIdAndRelationshipType(100L, 101L, RelationshipType.SIMILAR))
                .thenReturn(true);

        ProductRelationshipRequest req = new ProductRelationshipRequest(101L, RelationshipType.SIMILAR, 1);

        assertThatThrownBy(() -> similarProductService.addRelationship(100L, req))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    @DisplayName("Admin can add valid product relationship")
    void testAdminAddRelationship() {
        when(productRepository.findById(100L)).thenReturn(Optional.of(sourcePendant));
        when(productRepository.findById(101L)).thenReturn(Optional.of(emeraldDrop));
        when(relationshipRepository.existsBySourceProductIdAndRelatedProductIdAndRelationshipType(100L, 101L, RelationshipType.SIMILAR))
                .thenReturn(false);

        ProductRelationship saved = new ProductRelationship(sourcePendant, emeraldDrop, RelationshipType.SIMILAR, 1);
        ReflectionTestUtils.setField(saved, "id", 50L);
        when(relationshipRepository.save(any(ProductRelationship.class))).thenReturn(saved);

        ProductRelationshipRequest req = new ProductRelationshipRequest(101L, RelationshipType.SIMILAR, 1);
        ProductRelationshipResponse res = similarProductService.addRelationship(100L, req);

        assertThat(res.id()).isEqualTo(50L);
        assertThat(res.sourceProductId()).isEqualTo(100L);
        assertThat(res.relatedProductId()).isEqualTo(101L);
        verify(relationshipRepository, times(1)).save(any(ProductRelationship.class));
    }
}
