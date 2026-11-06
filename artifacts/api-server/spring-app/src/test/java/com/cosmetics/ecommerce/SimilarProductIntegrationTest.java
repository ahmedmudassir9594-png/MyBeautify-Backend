package com.cosmetics.ecommerce;

import com.cosmetics.ecommerce.dto.ProductRelationshipDtos.ProductRelationshipRequest;
import com.cosmetics.ecommerce.entity.Category;
import com.cosmetics.ecommerce.entity.Product;
import com.cosmetics.ecommerce.entity.ProductRelationship;
import com.cosmetics.ecommerce.enums.RelationshipType;
import com.cosmetics.ecommerce.enums.Role;
import com.cosmetics.ecommerce.repository.CartRepository;
import com.cosmetics.ecommerce.repository.CategoryRepository;
import com.cosmetics.ecommerce.repository.ComboItemRepository;
import com.cosmetics.ecommerce.repository.ComboRepository;
import com.cosmetics.ecommerce.repository.ProductRelationshipRepository;
import com.cosmetics.ecommerce.repository.ProductRepository;
import com.cosmetics.ecommerce.repository.SubcategoryRepository;
import com.cosmetics.ecommerce.repository.UserRepository;
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
class SimilarProductIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private SubcategoryRepository subcategoryRepository;

    @Autowired
    private ProductRelationshipRepository relationshipRepository;

    @Autowired
    private ComboItemRepository comboItemRepository;

    @Autowired
    private ComboRepository comboRepository;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private Category jewelleryCategory;
    private Product mainPendant;
    private Product emeraldDrop;
    private Product floralGold;
    private Product classicRound;
    private Product goldenChain;

    @BeforeEach
    void setUp() {
        cleanup();

        jewelleryCategory = categoryRepository.save(
                new Category("Pendants & Necklaces", "Handcrafted fine jewellery pendants"));

        mainPendant = productRepository.save(new Product(
                "Plated Pendant Necklace", "plated-pendant-necklace", "SKU-MAIN-1",
                BigDecimal.valueOf(12.00), 43, jewelleryCategory
        ));
        mainPendant.setImageUrl("https://malkaz.com/images/plated-pendant.jpg");
        mainPendant = productRepository.save(mainPendant);

        emeraldDrop = productRepository.save(new Product(
                "Emerald Drop Pendant", "emerald-drop-pendant", "SKU-ED-1",
                BigDecimal.valueOf(15.00), 20, jewelleryCategory
        ));
        emeraldDrop.setImageUrl("https://malkaz.com/images/emerald-drop.jpg");
        emeraldDrop = productRepository.save(emeraldDrop);

        floralGold = productRepository.save(new Product(
                "Floral Gold Necklace", "floral-gold-necklace", "SKU-FG-1",
                BigDecimal.valueOf(18.00), 15, jewelleryCategory
        ));
        floralGold.setImageUrl("https://malkaz.com/images/floral-gold.jpg");
        floralGold = productRepository.save(floralGold);

        classicRound = productRepository.save(new Product(
                "Classic Round Pendant", "classic-round-pendant", "SKU-CR-1",
                BigDecimal.valueOf(14.00), 25, jewelleryCategory
        ));
        classicRound.setImageUrl("https://malkaz.com/images/classic-round.jpg");
        classicRound = productRepository.save(classicRound);

        goldenChain = productRepository.save(new Product(
                "Golden Chain Necklace", "golden-chain-necklace", "SKU-GC-1",
                BigDecimal.valueOf(16.00), 10, jewelleryCategory
        ));
        goldenChain.setImageUrl("https://malkaz.com/images/golden-chain.jpg");
        goldenChain = productRepository.save(goldenChain);
    }

    @Test
    @DisplayName("Public GET /products/{id}/similar returns similar products without authentication")
    void testPublicGetSimilarProducts() throws Exception {
        mockMvc.perform(get("/products/" + mainPendant.getId() + "/similar?limit=4&page=0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content.length()").value(4))
                .andExpect(jsonPath("$.data.content[0].imageUrl").isNotEmpty())
                .andExpect(jsonPath("$.data.content[0].price").isNumber())
                .andExpect(jsonPath("$.data.content[0].inStock").value(true));
    }

    @Test
    @DisplayName("Public GET /products/slug/{slug}/similar returns similar products by slug")
    void testPublicGetSimilarProductsBySlug() throws Exception {
        mockMvc.perform(get("/products/slug/" + mainPendant.getSlug() + "/similar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content.length()").value(4));
    }

    @Test
    @DisplayName("Product detail GET /products/{id} enriches response with similarProducts")
    void testGetProductDetailIncludesSimilarProducts() throws Exception {
        mockMvc.perform(get("/products/" + mainPendant.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(mainPendant.getId()))
                .andExpect(jsonPath("$.data.similarProducts").isArray())
                .andExpect(jsonPath("$.data.similarProducts.length()").value(4))
                .andExpect(jsonPath("$.data.similarProducts[0].imageUrl").isNotEmpty());
    }

    @Test
    @DisplayName("Explicit variants configured in product_relationships are prioritized at the top")
    void testExplicitVariantsPrioritized() throws Exception {
        // Associate Classic Round as explicit VARIANT with display order 1
        relationshipRepository.save(new ProductRelationship(mainPendant, classicRound, RelationshipType.VARIANT, 1));

        mockMvc.perform(get("/products/" + mainPendant.getId() + "/similar?limit=4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].id").value(classicRound.getId()))
                .andExpect(jsonPath("$.data.content[0].relationshipType").value("VARIANT"));
    }

    @Test
    @DisplayName("Nonexistent product ID returns 404")
    void testNonexistentProductReturns404() throws Exception {
        mockMvc.perform(get("/products/999999/similar"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Unauthenticated POST /admin/products/{id}/relationships returns 401")
    void testUnauthenticatedAddRelationshipReturns401() throws Exception {
        ProductRelationshipRequest req = new ProductRelationshipRequest(emeraldDrop.getId(), RelationshipType.SIMILAR, 1);
        mockMvc.perform(post("/admin/products/" + mainPendant.getId() + "/relationships")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Customer role POST /admin/products/{id}/relationships returns 403")
    @WithMockUser(username = "customer@example.com", roles = {"CUSTOMER"})
    void testCustomerAddRelationshipReturns403() throws Exception {
        ProductRelationshipRequest req = new ProductRelationshipRequest(emeraldDrop.getId(), RelationshipType.SIMILAR, 1);
        mockMvc.perform(post("/admin/products/" + mainPendant.getId() + "/relationships")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Admin role can add, view, and delete product relationship")
    @WithMockUser(username = "admin@example.com", roles = {"ADMIN"})
    void testAdminManageProductRelationship() throws Exception {
        ProductRelationshipRequest req = new ProductRelationshipRequest(emeraldDrop.getId(), RelationshipType.VARIANT, 1);

        // 1. Add relationship
        mockMvc.perform(post("/admin/products/" + mainPendant.getId() + "/relationships")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.sourceProductId").value(mainPendant.getId()))
                .andExpect(jsonPath("$.data.relatedProductId").value(emeraldDrop.getId()))
                .andExpect(jsonPath("$.data.relationshipType").value("VARIANT"));

        // 2. View relationships
        mockMvc.perform(get("/admin/products/" + mainPendant.getId() + "/relationships"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));

        // 3. Delete relationship
        mockMvc.perform(delete("/admin/products/" + mainPendant.getId() + "/relationships/" + emeraldDrop.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        assertThat(relationshipRepository.findBySourceProductIdOrderByDisplayOrderAscCreatedAtDesc(mainPendant.getId()))
                .isEmpty();
    }

    @Test
    @DisplayName("Admin cannot relate a product to itself (returns 400)")
    @WithMockUser(username = "admin@example.com", roles = {"ADMIN"})
    void testAdminCannotRelateProductToItself() throws Exception {
        ProductRelationshipRequest req = new ProductRelationshipRequest(mainPendant.getId(), RelationshipType.SIMILAR, 1);
        mockMvc.perform(post("/admin/products/" + mainPendant.getId() + "/relationships")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @AfterEach
    void tearDown() {
        cleanup();
    }

    private void cleanup() {
        relationshipRepository.deleteAll();
        cartRepository.deleteAll();
        comboItemRepository.deleteAll();
        comboRepository.deleteAll();
        productRepository.deleteAll();
        subcategoryRepository.deleteAll();
        categoryRepository.deleteAll();
        userRepository.deleteAll();
    }
}
