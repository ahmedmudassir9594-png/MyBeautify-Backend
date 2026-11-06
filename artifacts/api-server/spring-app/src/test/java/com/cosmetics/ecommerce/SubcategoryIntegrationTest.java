package com.cosmetics.ecommerce;

import com.cosmetics.ecommerce.dto.SubcategoryDtos.*;
import com.cosmetics.ecommerce.entity.Category;
import com.cosmetics.ecommerce.entity.Product;
import com.cosmetics.ecommerce.entity.Subcategory;
import com.cosmetics.ecommerce.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SubcategoryIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SubcategoryRepository subcategoryRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductRelationshipRepository relationshipRepository;

    @Autowired
    private ComboItemRepository comboItemRepository;

    @Autowired
    private ComboRepository comboRepository;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private Category snakeCategory;
    private Category earringsCategory;

    @BeforeEach
    void setUp() {
        cleanup();

        snakeCategory = categoryRepository.save(new Category("Snake", "Premium snake design collection"));
        snakeCategory.setImageUrl("https://malkaz.com/images/snake-cat.jpg");
        snakeCategory = categoryRepository.save(snakeCategory);

        earringsCategory = categoryRepository.save(new Category("I6", "Elegant jewellery collection"));
        earringsCategory.setImageUrl("https://malkaz.com/images/i6-cat.jpg");
        earringsCategory = categoryRepository.save(earringsCategory);
    }

    @AfterEach
    void tearDown() {
        cleanup();
    }

    private void cleanup() {
        try { paymentRepository.deleteAll(); } catch (Exception ignored) {}
        try { orderRepository.deleteAll(); } catch (Exception ignored) {}
        try { relationshipRepository.deleteAll(); } catch (Exception ignored) {}
        try { cartRepository.deleteAll(); } catch (Exception ignored) {}
        try { comboItemRepository.deleteAll(); } catch (Exception ignored) {}
        try { comboRepository.deleteAll(); } catch (Exception ignored) {}
        try { productRepository.deleteAll(); } catch (Exception ignored) {}
        try { subcategoryRepository.deleteAll(); } catch (Exception ignored) {}
        try { categoryRepository.deleteAll(); } catch (Exception ignored) {}
        try { userRepository.deleteAll(); } catch (Exception ignored) {}
    }

    @Test
    @DisplayName("Admin creates a subcategory successfully (POST /admin/subcategories)")
    @WithMockUser(username = "admin@example.com", roles = {"ADMIN"})
    void testAdminCreateSubcategorySuccess() throws Exception {
        SubcategoryRequest request = new SubcategoryRequest(
                "Pendant Necklaces",
                snakeCategory.getId(),
                "Elegant pendant necklaces collection",
                "https://malkaz.com/images/pendant.jpg",
                true
        );

        mockMvc.perform(post("/admin/subcategories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Pendant Necklaces"))
                .andExpect(jsonPath("$.data.slug").value("pendant-necklaces"))
                .andExpect(jsonPath("$.data.categoryId").value(snakeCategory.getId()))
                .andExpect(jsonPath("$.data.categoryName").value("Snake"))
                .andExpect(jsonPath("$.data.active").value(true));

        assertThat(subcategoryRepository.findByCategoryIdOrderByNameAsc(snakeCategory.getId()))
                .hasSize(1);
    }

    @Test
    @DisplayName("Admin cannot create duplicate subcategory under same category (returns error)")
    @WithMockUser(username = "admin@example.com", roles = {"ADMIN"})
    void testAdminCannotCreateDuplicateSubcategoryUnderSameCategory() throws Exception {
        Subcategory existing = new Subcategory("Gold Chains", snakeCategory);
        subcategoryRepository.save(existing);

        SubcategoryRequest duplicateReq = new SubcategoryRequest(
                "gold chains",
                snakeCategory.getId(),
                "Duplicate check",
                null,
                true
        );

        mockMvc.perform(post("/admin/subcategories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(duplicateReq)))
                .andExpect(status().is4xxClientError())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Admin creating subcategory with invalid category ID returns 404")
    @WithMockUser(username = "admin@example.com", roles = {"ADMIN"})
    void testCreateSubcategoryInvalidCategoryReturns404() throws Exception {
        SubcategoryRequest request = new SubcategoryRequest(
                "Jhumkas",
                99999L,
                "Non-existent parent",
                null,
                true
        );

        mockMvc.perform(post("/admin/subcategories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Admin updates subcategory details (PUT /admin/subcategories/{id})")
    @WithMockUser(username = "admin@example.com", roles = {"ADMIN"})
    void testAdminUpdateSubcategory() throws Exception {
        Subcategory s = subcategoryRepository.save(new Subcategory("Initial Name", snakeCategory));

        SubcategoryRequest updateReq = new SubcategoryRequest(
                "Updated Necklaces",
                snakeCategory.getId(),
                "Updated description",
                "https://malkaz.com/updated.jpg",
                true
        );

        mockMvc.perform(put("/admin/subcategories/" + s.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Updated Necklaces"))
                .andExpect(jsonPath("$.data.description").value("Updated description"));
    }

    @Test
    @DisplayName("Admin activates/deactivates subcategory status (PATCH /admin/subcategories/{id}/status)")
    @WithMockUser(username = "admin@example.com", roles = {"ADMIN"})
    void testAdminToggleSubcategoryStatus() throws Exception {
        Subcategory s = subcategoryRepository.save(new Subcategory("Jhumka Earrings", earringsCategory));
        assertThat(s.isActive()).isTrue();

        mockMvc.perform(patch("/admin/subcategories/" + s.getId() + "/status?active=false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.active").value(false));

        Subcategory reloaded = subcategoryRepository.findById(s.getId()).orElseThrow();
        assertThat(reloaded.isActive()).isFalse();
    }

    @Test
    @DisplayName("Customer GET /categories returns active categories with subcategoriesCount and active subcategories")
    void testCustomerGetCategoriesEnriched() throws Exception {
        Subcategory sub1 = new Subcategory("Pendant Necklaces", snakeCategory);
        sub1.setActive(true);
        subcategoryRepository.save(sub1);

        Subcategory sub2 = new Subcategory("Gold Chains", snakeCategory);
        sub2.setActive(true);
        subcategoryRepository.save(sub2);

        Subcategory inactiveSub = new Subcategory("Secret Draft", snakeCategory);
        inactiveSub.setActive(false);
        subcategoryRepository.save(inactiveSub);

        mockMvc.perform(get("/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[?(@.id == " + snakeCategory.getId() + ")].subcategoriesCount").value(3))
                .andExpect(jsonPath("$.data[?(@.id == " + snakeCategory.getId() + ")].subcategories.length()").value(2));
    }

    @Test
    @DisplayName("Customer GET /categories/{id}/subcategories returns only active subcategories")
    void testCustomerGetCategorySubcategoriesOnlyActive() throws Exception {
        Subcategory activeSub = new Subcategory("Active Style", snakeCategory);
        activeSub.setActive(true);
        subcategoryRepository.save(activeSub);

        Subcategory inactiveSub = new Subcategory("Inactive Style", snakeCategory);
        inactiveSub.setActive(false);
        subcategoryRepository.save(inactiveSub);

        mockMvc.perform(get("/categories/" + snakeCategory.getId() + "/subcategories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].name").value("Active Style"));
    }

    @Test
    @DisplayName("Admin GET /admin/categories/{id}/subcategories returns both active and inactive")
    @WithMockUser(username = "admin@example.com", roles = {"ADMIN"})
    void testAdminGetCategorySubcategoriesReturnsAll() throws Exception {
        Subcategory activeSub = new Subcategory("Active Style", snakeCategory);
        activeSub.setActive(true);
        subcategoryRepository.save(activeSub);

        Subcategory inactiveSub = new Subcategory("Inactive Style", snakeCategory);
        inactiveSub.setActive(false);
        subcategoryRepository.save(inactiveSub);

        mockMvc.perform(get("/admin/categories/" + snakeCategory.getId() + "/subcategories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    @DisplayName("Safe category deletion: reject deleting category when it has active subcategories")
    @WithMockUser(username = "admin@example.com", roles = {"ADMIN"})
    void testSafeCategoryDeletionRejectsWhenSubcategoriesExist() throws Exception {
        subcategoryRepository.save(new Subcategory("Linked Subcategory", snakeCategory));

        mockMvc.perform(delete("/admin/categories/" + snakeCategory.getId()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("subcategory(ies) are associated")));
    }

    @Test
    @DisplayName("Safe subcategory deletion: delete subcategory with no products succeeds")
    @WithMockUser(username = "admin@example.com", roles = {"ADMIN"})
    void testSafeSubcategoryDeletionSuccess() throws Exception {
        Subcategory s = subcategoryRepository.save(new Subcategory("Temporary Sub", snakeCategory));

        mockMvc.perform(delete("/admin/subcategories/" + s.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        assertThat(subcategoryRepository.findById(s.getId())).isEmpty();
    }

    @Test
    @DisplayName("Unauthenticated request to /admin/subcategories returns 401")
    void testUnauthenticatedAdminSubcategoriesReturns401() throws Exception {
        mockMvc.perform(get("/admin/subcategories"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("CUSTOMER role cannot access /admin/subcategories (returns 403)")
    @WithMockUser(username = "customer@example.com", roles = {"CUSTOMER"})
    void testCustomerCannotAccessAdminSubcategories() throws Exception {
        mockMvc.perform(get("/admin/subcategories"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.success").value(false));
    }
}

