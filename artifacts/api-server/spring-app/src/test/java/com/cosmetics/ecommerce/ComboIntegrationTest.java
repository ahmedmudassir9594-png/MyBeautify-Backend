package com.cosmetics.ecommerce;

import com.cosmetics.ecommerce.dto.ComboDtos.*;
import com.cosmetics.ecommerce.entity.*;
import com.cosmetics.ecommerce.enums.DiscountType;
import com.cosmetics.ecommerce.enums.Role;
import com.cosmetics.ecommerce.repository.*;
import com.cosmetics.ecommerce.security.JwtService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ComboIntegrationTest {

    private static final byte[] VALID_PNG = new byte[]{
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52
    };

    private static final byte[] VALID_JPEG = new byte[]{
            (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10, 0x4A, 0x46, 0x49, 0x46, 0x00, 0x01
    };

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
    private ProductRelationshipRepository relationshipRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private SubcategoryRepository subcategoryRepository;

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
        relationshipRepository.deleteAll();
        cartRepository.deleteAll();
        comboItemRepository.deleteAll();
        comboRepository.deleteAll();
        ruleRepository.deleteAll();
        productRepository.deleteAll();
        subcategoryRepository.deleteAll();
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
        predefinedCombo.setImageFileName("royal-elegance.png");
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
                .andExpect(jsonPath("$.data.content[0].savingsAmount").value(500.0))
                .andExpect(jsonPath("$.data.content[0].imageFileName").value("royal-elegance.png"))
                .andExpect(jsonPath("$.data.content[0].imageUrl").value("/api/uploads/royal-elegance.png"));
    }

    @Test
    @DisplayName("Public GET /combos/{slug} returns combo details and component items")
    void testPublicGetComboBySlug() throws Exception {
        mockMvc.perform(get("/combos/" + predefinedCombo.getSlug()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Royal Elegance"))
                .andExpect(jsonPath("$.data.imageFileName").value("royal-elegance.png"))
                .andExpect(jsonPath("$.data.imageUrl").value("/api/uploads/royal-elegance.png"))
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
        var mvcResult = mockMvc.perform(post("/combos/" + predefinedCombo.getId() + "/cart")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\": 1}"))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = mvcResult.getResponse().getContentAsString();
        JsonNode rootNode = objectMapper.readTree(responseBody);
        String comboGroupId = rootNode.path("data").path("items").get(0).path("comboGroupId").asText();

        mockMvc.perform(delete("/combos/cart/groups/" + comboGroupId)
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(0))
                .andExpect(jsonPath("$.data.subtotal").value(0.0));
    }

    @Test
    @DisplayName("Admin role can create a new predefined combo via JSON")
    @WithMockUser(username = "admin@example.com", roles = {"ADMIN"})
    void testAdminCreateAndUpdateCombo() throws Exception {
        ComboRequest request = new ComboRequest(
                "Festive Glow",
                "festive-glow",
                "Necklace + Bangles festive combo",
                "festive.jpg",
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
                .andExpect(jsonPath("$.data.imageFileName").value("festive.jpg"))
                .andExpect(jsonPath("$.data.imageUrl").value("/api/uploads/festive.jpg"))
                .andExpect(jsonPath("$.data.sellingPrice").value(2199.0))
                .andExpect(jsonPath("$.data.savingsAmount").value(501.0));
    }

    @Test
    @DisplayName("Admin role can create combo with multipart image upload")
    @WithMockUser(username = "admin@example.com", roles = {"ADMIN"})
    void testAdminCreateComboWithMultipartImage() throws Exception {
        ComboRequest comboReq = new ComboRequest(
                "Bridal Grandeur",
                "bridal-grandeur",
                "Complete bridal luxury package",
                null,
                BigDecimal.valueOf(2999.00),
                BigDecimal.valueOf(3499.00),
                null,
                null,
                true,
                true,
                List.of(
                        new ComboItemRequest(necklaceProduct.getId(), 1, null),
                        new ComboItemRequest(earringsProduct.getId(), 1, null)
                )
        );

        MockMultipartFile imagePart = new MockMultipartFile("image", "bridal-suite.png", "image/png", VALID_PNG);

        MvcResult result = mockMvc.perform(multipart("/admin/combos")
                        .file(imagePart)
                        .param("combo", objectMapper.writeValueAsString(comboReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Bridal Grandeur"))
                .andExpect(jsonPath("$.data.imageFileName").value(endsWith(".png")))
                .andExpect(jsonPath("$.data.imageUrl").value(startsWith("/api/uploads/")))
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        String savedFileName = JsonPath.read(responseBody, "$.data.imageFileName");
        Path filePath = Paths.get("target/test-uploads").resolve(savedFileName);
        assertThat(Files.exists(filePath)).isTrue();
    }

    @Test
    @DisplayName("Admin role can create combo without image upload")
    @WithMockUser(username = "admin@example.com", roles = {"ADMIN"})
    void testAdminCreateComboWithoutImage() throws Exception {
        ComboRequest comboReq = new ComboRequest(
                "Simple Daily Set",
                "simple-daily-set",
                "Everyday essentials",
                null,
                BigDecimal.valueOf(999.00),
                BigDecimal.valueOf(1200.00),
                null,
                null,
                true,
                false,
                List.of(new ComboItemRequest(earringsProduct.getId(), 1, null))
        );

        mockMvc.perform(multipart("/admin/combos")
                        .param("combo", objectMapper.writeValueAsString(comboReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.imageFileName").value(nullValue()))
                .andExpect(jsonPath("$.data.imageUrl").value(nullValue()));
    }

    @Test
    @DisplayName("Admin multipart combo creation rejects invalid file extension (.txt)")
    @WithMockUser(username = "admin@example.com", roles = {"ADMIN"})
    void testAdminCreateComboInvalidFileExtension() throws Exception {
        ComboRequest comboReq = new ComboRequest(
                "Invalid Ext Combo",
                "invalid-ext-combo",
                "Should fail due to bad extension",
                null,
                BigDecimal.valueOf(999.00),
                null,
                null,
                null,
                true,
                false,
                List.of(new ComboItemRequest(necklaceProduct.getId(), 1, null))
        );

        MockMultipartFile textFile = new MockMultipartFile("image", "script.txt", "text/plain", "malicious payload".getBytes());

        mockMvc.perform(multipart("/admin/combos")
                        .file(textFile)
                        .param("combo", objectMapper.writeValueAsString(comboReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(containsString("Unsupported file extension")));
    }

    @Test
    @DisplayName("Admin multipart combo creation rejects oversized image (> 5MB)")
    @WithMockUser(username = "admin@example.com", roles = {"ADMIN"})
    void testAdminCreateComboOversizedImage() throws Exception {
        ComboRequest comboReq = new ComboRequest(
                "Oversized Image Combo",
                "oversized-combo",
                "Should fail due to oversized image",
                null,
                BigDecimal.valueOf(999.00),
                null,
                null,
                null,
                true,
                false,
                List.of(new ComboItemRequest(necklaceProduct.getId(), 1, null))
        );

        byte[] largeBytes = new byte[6 * 1024 * 1024]; // 6 MB
        System.arraycopy(VALID_PNG, 0, largeBytes, 0, VALID_PNG.length);
        MockMultipartFile bigFile = new MockMultipartFile("image", "giant.png", "image/png", largeBytes);

        mockMvc.perform(multipart("/admin/combos")
                        .file(bigFile)
                        .param("combo", objectMapper.writeValueAsString(comboReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(containsString("exceeds maximum allowed limit")));
    }

    @Test
    @DisplayName("Admin updating combo with new image replaces old file and safely deletes it from storage")
    @WithMockUser(username = "admin@example.com", roles = {"ADMIN"})
    void testAdminUpdateComboImageReplacement() throws Exception {
        // Step 1: Create combo with initial PNG image
        ComboRequest initialReq = new ComboRequest(
                "Replaceable Combo",
                "replaceable-combo",
                "Initial combo description",
                null,
                BigDecimal.valueOf(1800.00),
                BigDecimal.valueOf(2000.00),
                null, null, true, false,
                List.of(new ComboItemRequest(necklaceProduct.getId(), 1, null))
        );

        MockMultipartFile file1 = new MockMultipartFile("image", "initial.png", "image/png", VALID_PNG);

        MvcResult createResult = mockMvc.perform(multipart("/admin/combos")
                        .file(file1)
                        .param("combo", objectMapper.writeValueAsString(initialReq)))
                .andExpect(status().isOk())
                .andReturn();

        String createJson = createResult.getResponse().getContentAsString();
        long comboId = ((Number) JsonPath.read(createJson, "$.data.id")).longValue();
        String initialFileName = JsonPath.read(createJson, "$.data.imageFileName");
        Path initialFilePath = Paths.get("target/test-uploads").resolve(initialFileName);
        assertThat(Files.exists(initialFilePath)).isTrue();

        // Step 2: Update combo with JPEG image
        ComboRequest updateReq = new ComboRequest(
                "Replaceable Combo Updated",
                "replaceable-combo",
                "Updated description",
                null,
                BigDecimal.valueOf(1750.00),
                BigDecimal.valueOf(2000.00),
                null, null, true, false,
                List.of(new ComboItemRequest(necklaceProduct.getId(), 1, null))
        );

        MockMultipartFile file2 = new MockMultipartFile("image", "new-look.jpg", "image/jpeg", VALID_JPEG);

        MvcResult updateResult = mockMvc.perform(multipart("/admin/combos/" + comboId)
                        .file(file2)
                        .param("combo", objectMapper.writeValueAsString(updateReq))
                        .with(req -> { req.setMethod("PUT"); return req; }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.imageFileName").value(endsWith(".jpg")))
                .andReturn();

        String updateJson = updateResult.getResponse().getContentAsString();
        String updatedFileName = JsonPath.read(updateJson, "$.data.imageFileName");
        Path updatedFilePath = Paths.get("target/test-uploads").resolve(updatedFileName);

        // Verify: New image exists, old image has been safely deleted
        assertThat(Files.exists(updatedFilePath)).isTrue();
        assertThat(Files.exists(initialFilePath)).isFalse();
    }

    @Test
    @DisplayName("Admin updating combo without image retains existing image and file remains on disk")
    @WithMockUser(username = "admin@example.com", roles = {"ADMIN"})
    void testAdminUpdateComboRetainsImageWhenNoNewFile() throws Exception {
        // Step 1: Create combo with image
        ComboRequest initialReq = new ComboRequest(
                "Retain Image Combo",
                "retain-image-combo",
                "Description",
                null,
                BigDecimal.valueOf(1500.00),
                null, null, null, true, false,
                List.of(new ComboItemRequest(earringsProduct.getId(), 1, null))
        );

        MockMultipartFile file = new MockMultipartFile("image", "earrings.png", "image/png", VALID_PNG);

        MvcResult createResult = mockMvc.perform(multipart("/admin/combos")
                        .file(file)
                        .param("combo", objectMapper.writeValueAsString(initialReq)))
                .andExpect(status().isOk())
                .andReturn();

        long comboId = ((Number) JsonPath.read(createResult.getResponse().getContentAsString(), "$.data.id")).longValue();
        String originalFileName = JsonPath.read(createResult.getResponse().getContentAsString(), "$.data.imageFileName");
        Path originalFilePath = Paths.get("target/test-uploads").resolve(originalFileName);
        assertThat(Files.exists(originalFilePath)).isTrue();

        // Step 2: Update only price and name without providing a file
        ComboRequest updateReq = new ComboRequest(
                "Retain Image Combo Renamed",
                "retain-image-combo",
                "Updated Description",
                null, // No image specified
                BigDecimal.valueOf(1400.00),
                null, null, null, true, false,
                List.of(new ComboItemRequest(earringsProduct.getId(), 1, null))
        );

        mockMvc.perform(multipart("/admin/combos/" + comboId)
                        .param("combo", objectMapper.writeValueAsString(updateReq))
                        .with(req -> { req.setMethod("PUT"); return req; }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Retain Image Combo Renamed"))
                .andExpect(jsonPath("$.data.imageFileName").value(originalFileName))
                .andExpect(jsonPath("$.data.imageUrl").value("/api/uploads/" + originalFileName));

        // File must still be intact on disk
        assertThat(Files.exists(originalFilePath)).isTrue();
    }

    @Test
    @DisplayName("Admin deleting combo safely deletes its stored image file from disk")
    @WithMockUser(username = "admin@example.com", roles = {"ADMIN"})
    void testAdminDeleteComboCleansUpImageFile() throws Exception {
        // Create combo with image
        ComboRequest req = new ComboRequest(
                "Combo To Delete",
                "combo-to-delete",
                "Delete me soon",
                null,
                BigDecimal.valueOf(1100.00),
                null, null, null, true, false,
                List.of(new ComboItemRequest(necklaceProduct.getId(), 1, null))
        );

        MockMultipartFile file = new MockMultipartFile("image", "temp.png", "image/png", VALID_PNG);

        MvcResult createResult = mockMvc.perform(multipart("/admin/combos")
                        .file(file)
                        .param("combo", objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();

        long comboId = ((Number) JsonPath.read(createResult.getResponse().getContentAsString(), "$.data.id")).longValue();
        String fileName = JsonPath.read(createResult.getResponse().getContentAsString(), "$.data.imageFileName");
        Path filePath = Paths.get("target/test-uploads").resolve(fileName);
        assertThat(Files.exists(filePath)).isTrue();

        // Delete combo
        mockMvc.perform(delete("/admin/combos/" + comboId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // Verify combo is removed from database
        assertThat(comboRepository.findById(comboId)).isEmpty();

        // Verify image file has been cleaned up from disk
        assertThat(Files.exists(filePath)).isFalse();
    }

    @Test
    @DisplayName("Admin deleting combo does not delete shared image file if another combo uses it")
    @WithMockUser(username = "admin@example.com", roles = {"ADMIN"})
    void testAdminDeleteComboDoesNotDeleteSharedImageFile() throws Exception {
        // Create shared file on disk in target/test-uploads
        Path uploadDir = Paths.get("target/test-uploads");
        Files.createDirectories(uploadDir);
        String sharedFileName = "shared-signature-jewels.png";
        Path sharedFilePath = uploadDir.resolve(sharedFileName);
        Files.write(sharedFilePath, VALID_PNG);

        // Combo 1 references shared image
        Combo combo1 = new Combo("Shared Combo 1", "shared-combo-1", "Set 1", BigDecimal.valueOf(1000.00));
        combo1.setImageFileName(sharedFileName);
        combo1.addItem(new ComboItem(combo1, necklaceProduct, 1));
        combo1 = comboRepository.save(combo1);

        // Combo 2 references same shared image
        Combo combo2 = new Combo("Shared Combo 2", "shared-combo-2", "Set 2", BigDecimal.valueOf(1200.00));
        combo2.setImageFileName(sharedFileName);
        combo2.addItem(new ComboItem(combo2, earringsProduct, 1));
        combo2 = comboRepository.save(combo2);

        assertThat(comboRepository.countByImageFileName(sharedFileName)).isEqualTo(2);

        // Delete combo 1
        mockMvc.perform(delete("/admin/combos/" + combo1.getId()))
                .andExpect(status().isOk());

        // File must NOT be deleted because combo 2 still references it
        assertThat(Files.exists(sharedFilePath)).isTrue();
        assertThat(comboRepository.countByImageFileName(sharedFileName)).isEqualTo(1);
    }

    @Test
    @DisplayName("Admin standalone image upload /admin/combos/upload-image returns ImageUploadResponse")
    @WithMockUser(username = "admin@example.com", roles = {"ADMIN"})
    void testAdminStandaloneImageUpload() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "standalone-offer.png", "image/png", VALID_PNG);

        MvcResult result = mockMvc.perform(multipart("/admin/combos/upload-image").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.fileName").value(endsWith(".png")))
                .andExpect(jsonPath("$.data.imageUrl").isNotEmpty())
                .andExpect(jsonPath("$.data.relativeUrl").value(startsWith("/api/uploads/")))
                .andReturn();

        String fileName = JsonPath.read(result.getResponse().getContentAsString(), "$.data.fileName");
        Path filePath = Paths.get("target/test-uploads").resolve(fileName);
        assertThat(Files.exists(filePath)).isTrue();
    }

    @Test
    @DisplayName("Admin direct combo image update /admin/combos/{id}/image succeeds")
    @WithMockUser(username = "admin@example.com", roles = {"ADMIN"})
    void testAdminUploadComboImageById() throws Exception {
        MockMultipartFile file = new MockMultipartFile("image", "new-pendant.png", "image/png", VALID_PNG);

        mockMvc.perform(multipart("/admin/combos/" + predefinedCombo.getId() + "/image")
                        .file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.imageFileName").value(endsWith(".png")))
                .andExpect(jsonPath("$.data.imageUrl").value(startsWith("/api/uploads/")));
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

    @Test
    @DisplayName("CUSTOMER role cannot upload image to /admin/combos/upload-image (returns 403)")
    @WithMockUser(username = "customer@example.com", roles = {"CUSTOMER"})
    void testCustomerCannotUploadToAdminCombos() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "attempt.png", "image/png", VALID_PNG);
        mockMvc.perform(multipart("/admin/combos/upload-image").file(file))
                .andExpect(status().isForbidden());
    }
}
