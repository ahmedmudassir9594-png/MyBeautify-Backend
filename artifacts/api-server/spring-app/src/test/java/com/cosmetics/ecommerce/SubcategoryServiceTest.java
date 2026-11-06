package com.cosmetics.ecommerce;

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
import com.cosmetics.ecommerce.service.AuditLogService;
import com.cosmetics.ecommerce.service.FileStorageService;
import com.cosmetics.ecommerce.service.SubcategoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubcategoryServiceTest {

    @Mock
    private SubcategoryRepository subcategoryRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private FileStorageService fileStorageService;

    @Mock
    private AuditLogService auditLogService;

    private SubcategoryService subcategoryService;

    private Category necklacesCategory;
    private Category earringsCategory;

    @BeforeEach
    void setUp() {
        subcategoryService = new SubcategoryService(
                subcategoryRepository, categoryRepository, productRepository,
                fileStorageService, auditLogService);

        necklacesCategory = new Category("Necklaces", "Fine jewellery necklaces");
        ReflectionTestUtils.setField(necklacesCategory, "id", 1L);

        earringsCategory = new Category("Earrings", "Fine jewellery earrings");
        ReflectionTestUtils.setField(earringsCategory, "id", 2L);
    }

    @Test
    @DisplayName("Successfully create subcategory under parent category")
    void testCreateSubcategorySuccess() {
        SubcategoryRequest request = new SubcategoryRequest(
                "Pendant Necklaces", 1L, "Elegant pendant necklaces collection",
                "https://example.com/pendant.jpg", true);

        when(categoryRepository.findById(1L)).thenReturn(Optional.of(necklacesCategory));
        when(subcategoryRepository.existsByCategoryIdAndNameIgnoreCase(1L, "Pendant Necklaces")).thenReturn(false);
        when(subcategoryRepository.save(any(Subcategory.class))).thenAnswer(invocation -> {
            Subcategory s = invocation.getArgument(0);
            ReflectionTestUtils.setField(s, "id", 10L);
            return s;
        });

        SubcategoryResponse response = subcategoryService.create(request);

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.name()).isEqualTo("Pendant Necklaces");
        assertThat(response.slug()).isEqualTo("pendant-necklaces");
        assertThat(response.categoryId()).isEqualTo(1L);
        assertThat(response.categoryName()).isEqualTo("Necklaces");
        assertThat(response.active()).isTrue();
        assertThat(response.imageUrl()).isEqualTo("https://example.com/pendant.jpg");

        verify(auditLogService, times(1)).logEvent(
                eq(AuditAction.SUBCATEGORY_CREATE), eq(AuditResult.SUCCESS),
                eq("SUBCATEGORY"), eq("10"), anyString(), isNull(), anyString());
    }

    @Test
    @DisplayName("Create subcategory rejects blank name with BadRequestException")
    void testCreateBlankNameThrowsBadRequest() {
        SubcategoryRequest request = new SubcategoryRequest("   ", 1L, "Description", null, true);

        assertThatThrownBy(() -> subcategoryService.create(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("name cannot be blank");
    }

    @Test
    @DisplayName("Create subcategory rejects null categoryId with BadRequestException")
    void testCreateNullCategoryIdThrowsBadRequest() {
        SubcategoryRequest request = new SubcategoryRequest("Gold Chains", null, "Description", null, true);

        assertThatThrownBy(() -> subcategoryService.create(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Parent category ID is required");
    }

    @Test
    @DisplayName("Create subcategory with non-existent parent category throws ResourceNotFoundException")
    void testCreateNonExistentCategoryThrowsNotFound() {
        SubcategoryRequest request = new SubcategoryRequest("Gold Chains", 999L, "Description", null, true);
        when(categoryRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> subcategoryService.create(request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Parent category not found");
    }

    @Test
    @DisplayName("Prevent duplicate subcategory name under the same parent category (case-insensitive)")
    void testCreateDuplicateNameUnderSameCategoryThrowsDuplicate() {
        SubcategoryRequest request = new SubcategoryRequest("pendant necklaces", 1L, "Description", null, true);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(necklacesCategory));
        when(subcategoryRepository.existsByCategoryIdAndNameIgnoreCase(1L, "pendant necklaces")).thenReturn(true);

        assertThatThrownBy(() -> subcategoryService.create(request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("already exists under category");
    }

    @Test
    @DisplayName("Allow same subcategory name under a different parent category")
    void testCreateSameNameUnderDifferentCategoryAllowed() {
        SubcategoryRequest request = new SubcategoryRequest("Classic", 2L, "Classic earrings", null, true);
        when(categoryRepository.findById(2L)).thenReturn(Optional.of(earringsCategory));
        when(subcategoryRepository.existsByCategoryIdAndNameIgnoreCase(2L, "Classic")).thenReturn(false);
        when(subcategoryRepository.save(any(Subcategory.class))).thenAnswer(invocation -> {
            Subcategory s = invocation.getArgument(0);
            ReflectionTestUtils.setField(s, "id", 20L);
            return s;
        });

        SubcategoryResponse response = subcategoryService.create(request);
        assertThat(response.id()).isEqualTo(20L);
        assertThat(response.categoryName()).isEqualTo("Earrings");
    }

    @Test
    @DisplayName("Retrieve subcategory by ID with product count")
    void testGetByIdReturnsResponseWithProductCount() {
        Subcategory s = new Subcategory("Gold Chains", necklacesCategory);
        ReflectionTestUtils.setField(s, "id", 5L);

        when(subcategoryRepository.findByIdWithCategory(5L)).thenReturn(Optional.of(s));
        when(productRepository.countBySubcategoryId(5L)).thenReturn(8L);

        SubcategoryResponse response = subcategoryService.getById(5L);
        assertThat(response.id()).isEqualTo(5L);
        assertThat(response.name()).isEqualTo("Gold Chains");
        assertThat(response.productCount()).isEqualTo(8L);
    }

    @Test
    @DisplayName("Retrieve active subcategory by ID throws ResourceNotFound if inactive")
    void testGetActiveByIdThrowsIfInactive() {
        when(subcategoryRepository.findActiveByIdWithCategory(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> subcategoryService.getActiveById(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Update subcategory information successfully")
    void testUpdateSubcategorySuccess() {
        Subcategory existing = new Subcategory("Old Name", necklacesCategory);
        ReflectionTestUtils.setField(existing, "id", 15L);

        when(subcategoryRepository.findByIdWithCategory(15L)).thenReturn(Optional.of(existing));
        when(subcategoryRepository.existsByCategoryIdAndNameIgnoreCaseAndIdNot(1L, "Choker Sets", 15L)).thenReturn(false);
        when(subcategoryRepository.save(any(Subcategory.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SubcategoryRequest updateReq = new SubcategoryRequest(
                "Choker Sets", 1L, "Royal choker collection", "https://img.com/choker.jpg", true);

        SubcategoryResponse response = subcategoryService.update(15L, updateReq);

        assertThat(response.name()).isEqualTo("Choker Sets");
        assertThat(response.description()).isEqualTo("Royal choker collection");
        verify(auditLogService).logEvent(eq(AuditAction.SUBCATEGORY_UPDATE), eq(AuditResult.SUCCESS), eq("SUBCATEGORY"), eq("15"), anyString(), any(), anyString());
    }

    @Test
    @DisplayName("Update subcategory active/inactive status")
    void testUpdateStatusSuccess() {
        Subcategory existing = new Subcategory("Jhumkas", earringsCategory);
        ReflectionTestUtils.setField(existing, "id", 12L);
        existing.setActive(true);

        when(subcategoryRepository.findByIdWithCategory(12L)).thenReturn(Optional.of(existing));
        when(subcategoryRepository.save(any(Subcategory.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SubcategoryResponse response = subcategoryService.updateStatus(12L, false);

        assertThat(response.active()).isFalse();
        verify(auditLogService).logEvent(eq(AuditAction.SUBCATEGORY_STATUS_CHANGE), eq(AuditResult.SUCCESS), eq("SUBCATEGORY"), eq("12"), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Safe deletion: successfully delete subcategory when no products are associated")
    void testSafeDeletionSuccessWhenNoProducts() {
        Subcategory s = new Subcategory("Silver Rings", necklacesCategory);
        ReflectionTestUtils.setField(s, "id", 30L);
        s.setImageUrl("https://example.com/uploads/silver.png");

        when(subcategoryRepository.findByIdWithCategory(30L)).thenReturn(Optional.of(s));
        when(productRepository.countBySubcategoryId(30L)).thenReturn(0L);

        subcategoryService.delete(30L);

        verify(subcategoryRepository).delete(s);
        verify(auditLogService).logEvent(eq(AuditAction.SUBCATEGORY_DELETE), eq(AuditResult.SUCCESS), eq("SUBCATEGORY"), eq("30"), anyString(), anyString(), isNull());
    }

    @Test
    @DisplayName("Safe deletion: reject deleting subcategory when products are linked to it")
    void testSafeDeletionRejectsWhenProductsExist() {
        Subcategory s = new Subcategory("Diamond Rings", necklacesCategory);
        ReflectionTestUtils.setField(s, "id", 31L);

        when(subcategoryRepository.findByIdWithCategory(31L)).thenReturn(Optional.of(s));
        when(productRepository.countBySubcategoryId(31L)).thenReturn(5L);

        assertThatThrownBy(() -> subcategoryService.delete(31L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Cannot delete subcategory")
                .hasMessageContaining("5 product(s) are associated");

        verify(subcategoryRepository, never()).delete(any());
    }
}

