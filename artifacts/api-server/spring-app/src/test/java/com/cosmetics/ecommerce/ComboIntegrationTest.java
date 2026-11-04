package com.cosmetics.ecommerce;

import com.cosmetics.ecommerce.dto.ComboDtos.*;
import com.cosmetics.ecommerce.entity.*;
import com.cosmetics.ecommerce.enums.DiscountType;
import com.cosmetics.ecommerce.enums.Role;
import com.cosmetics.ecommerce.repository.*;
import com.cosmetics.ecommerce.security.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ComboIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ComboRepository comboRepository;

    @Autowired
    private ComboItemRepository comboItemRepository;

    @Autowired
    private ComboDiscountRuleRepository ruleRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ObjectMapper objectMapper;

    private Category jewelleryCategory;
    private Product necklaceProduct;
    private Product earringsProduct;
    private Product banglesProduct;
    private Combo predefinedCombo;
    private User testCustomer;
    private String customerToken;

    @BeforeEach
    void setUp() {
        cartRepository.deleteAll();
        comboItemRepository.deleteAll();
        comboRepository.deleteAll();
        ruleRepository.deleteAll();
        productRepository.deleteAll();
        categoryRepository.deleteAll();
        userRepository.deleteAll();

        jewelleryCategory = categoryRepository.save(new Category("Jewellery", "Luxury Jewellery"));

        necklaceProduct = productRepository.save(new Product(
                "Royal Emerald Necklace", "royal-emerald-necklace", "SKU-NECK-1",
                BigDecimal.valueOf(1200.00), 10, jewelleryCategory
        ));

        earringsProduct = productRepository.save(new Product(
                "Royal Emerald Earrings", "royal-emerald-earrings", "SKU-EAR-1",
                BigDecimal.valueOf(799.00), 10, jewelleryCategory
        ));

        banglesProduct = productRepository.save(new Product(
                "Gold Bangles Set", "gold-bangles-set", "SKU-BANG-1",
                BigDecimal.valueOf(1500.00), 10, jewelleryCategory
        ));

        // Create a predefined combo: Royal Elegance (sellingPrice 1499, original 1999, save 500)
        predefinedCombo = new Combo("Royal Elegance", "royal-elegance", "Regal Necklace + Earrings", BigDecimal.valueOf(1499.00));
        predefinedCombo.setOriginalPrice(BigDecimal.valueOf(1999.00));
        predefinedCombo.setDiscountAmount(BigDecimal.valueOf(500.00));
        predefinedCombo.setActive(true);
        predefinedCombo.setFeatured(true);
        predefinedCombo.addItem(new ComboItem(predefinedCombo, necklaceProduct, 1));
        predefinedCombo.addItem(new ComboItem(predefinedCombo, earringsProduct, 1));
        predefinedCombo = comboRepository.save(predefinedCombo);

        // Seed default 10% discount rule
        ComboDiscountRule rule10 = new ComboDiscountRule("Combo Discount (10%)", 2, DiscountType.PERCENTAGE, BigDecimal.valueOf(10.00));
        ruleRepository.save(rule10);

        testCustomer = userRepository.save(new User("Zara", "Khan", "zara@example.com", "secret123", Role.CUSTOMER));
        customerToken = jwtService.accessToken(testCustomer);
    }

    @Test
    @DisplayName("Public GET /combos lists active predefined combos without authentication")
    void testPublicGetCombos() throws Exception {
        mockMvc.perform(get("/combos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].name").value("Royal Elegance"))
                .andExpect(jsonPath("$.data.content[0].sellingPrice").value(1499.0))
                .andExpect(jsonPath("$.data.content[0].savingsAmount").value(500.0));
    }

    @Test
    @DisplayName("Public GET /combos/{slug} returns combo details and component items")
    void testPublicGetComboBySlug() throws Exception {
        mockMvc.perform(get("/combos/" + predefinedCombo.getSlug()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Royal Elegance"))
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items.length()").value(2));
    }

    @Test
    @DisplayName("Public GET /combos/custom/products returns eligible products")
    void testPublicGetCustomComboProducts() throws Exception {
        mockMvc.perform(get("/combos/custom/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.totalElements").value(3));
    }

    @Test
    @DisplayName("Public POST /combos/custom/preview calculates 10% discount for 2 products")
    void testPublicPreviewCustomCombo() throws Exception {
        CustomComboPreviewRequest request = new CustomComboPreviewRequest(List.of(
                new CustomComboItemRequest(necklaceProduct.getId(), 1),
                new CustomComboItemRequest(earringsProduct.getId(), 1)
        ));

        // Subtotal = 1200 + 799 = 1999.00, 10% discount = 199.90, final total = 1799.10
        mockMvc.perform(post("/combos/custom/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.originalSubtotal").value(1999.0))
                .andExpect(jsonPath("$.data.discountAmount").value(199.9))
                .andExpect(jsonPath("$.data.finalTotal").value(1799.1))
                .andExpect(jsonPath("$.data.eligible").value(true))
                .andExpect(jsonPath("$.data.appliedRuleName").value("Combo Discount (10%)"));
    }

    @Test
    @DisplayName("Unauthenticated POST /combos/{id}/cart returns 401")
    void testUnauthenticatedAddComboToCartReturns401() throws Exception {
        mockMvc.perform(post("/combos/" + predefinedCombo.getId() + "/cart")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Authenticated POST /combos/{id}/cart adds predefined combo items to user cart")
    void testAuthenticatedAddPredefinedComboToCart() throws Exception {
        mockMvc.perform(post("/combos/" + predefinedCombo.getId() + "/cart")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\": 1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].comboGroupId").isNotEmpty())
                .andExpect(jsonPath("$.data.items[0].comboName").value("Royal Elegance"))
                .andExpect(jsonPath("$.data.subtotal").value(1499.0));
    }

    @Test
    @DisplayName("Authenticated POST /combos/custom/cart adds custom combo items to user cart")
    void testAuthenticatedAddCustomComboToCart() throws Exception {
        AddCustomComboToCartRequest request = new AddCustomComboToCartRequest(List.of(
                new CustomComboItemRequest(necklaceProduct.getId(), 1),
                new CustomComboItemRequest(banglesProduct.getId(), 1)
        ));

        // Subtotal = 1200 + 1500 = 2700.00. 10% discount = 270.00. Final total = 2430.00
        mockMvc.perform(post("/combos/custom/cart")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.subtotal").value(2430.0));
    }

    @Test
    @DisplayName("Authenticated DELETE /combos/cart/groups/{comboGroupId} removes the combo group")
    void testRemoveComboGroupFromCart() throws Exception {
        // First add predefined combo to cart
        var mvcResult = mockMvc.perform(post("/combos/" + predefinedCombo.getId() + "/cart")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\": 1}"))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = mvcResult.getResponse().getContentAsString();
        com.fasterxml.jackson.databind.JsonNode rootNode = objectMapper.readTree(responseBody);
        String comboGroupId = rootNode.path("data").path("items").get(0).path("comboGroupId").asText();

        // Delete combo group
        mockMvc.perform(delete("/combos/cart/groups/" + comboGroupId)
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(0))
                .andExpect(jsonPath("$.data.subtotal").value(0.0));
    }

    @Test
    @DisplayName("Admin role can create and update a new predefined combo")
    @WithMockUser(username = "admin@example.com", roles = {"ADMIN"})
    void testAdminCreateAndUpdateCombo() throws Exception {
        ComboRequest request = new ComboRequest(
                "Festive Glow",
                "festive-glow",
                "Necklace + Bangles festive combo",
                "https://example.com/festive.jpg",
                BigDecimal.valueOf(2199.00),
                BigDecimal.valueOf(2700.00),
                null,
                null,
                true,
                true,
                List.of(
                        new ComboItemRequest(necklaceProduct.getId(), 1, null),
                        new ComboItemRequest(banglesProduct.getId(), 1, null)
                )
        );

        mockMvc.perform(post("/admin/combos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Festive Glow"))
                .andExpect(jsonPath("$.data.sellingPrice").value(2199.0))
                .andExpect(jsonPath("$.data.savingsAmount").value(501.0));
    }

    @Test
    @DisplayName("Admin role can manage combo discount rules CRUD")
    @WithMockUser(username = "admin@example.com", roles = {"ADMIN"})
    void testAdminManageDiscountRules() throws Exception {
        ComboDiscountRuleRequest request = new ComboDiscountRuleRequest(
                "VIP Mega Saver 20%",
                "20% off when 3 or more items are selected",
                3,
                DiscountType.PERCENTAGE,
                BigDecimal.valueOf(20.00),
                BigDecimal.valueOf(1000.00),
                BigDecimal.valueOf(800.00),
                null,
                null,
                true
        );

        mockMvc.perform(post("/admin/combos/rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("VIP Mega Saver 20%"))
                .andExpect(jsonPath("$.data.minProducts").value(3));

        mockMvc.perform(get("/admin/combos/rules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    @DisplayName("CUSTOMER role cannot access /admin/combos (returns 403)")
    @WithMockUser(username = "customer@example.com", roles = {"CUSTOMER"})
    void testCustomerCannotAccessAdminCombos() throws Exception {
        mockMvc.perform(get("/admin/combos"))
                .andExpect(status().isForbidden());
    }
}
