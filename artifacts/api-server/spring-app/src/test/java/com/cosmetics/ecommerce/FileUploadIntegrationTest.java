package com.cosmetics.ecommerce;

import com.cosmetics.ecommerce.dto.CatalogDtos.ProductRequest;
import com.cosmetics.ecommerce.entity.Brand;
import com.cosmetics.ecommerce.entity.Category;
import com.cosmetics.ecommerce.repository.BrandRepository;
import com.cosmetics.ecommerce.repository.CategoryRepository;
import com.cosmetics.ecommerce.repository.ProductRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.nio.charset.StandardCharsets;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FileUploadIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private static final byte[] VALID_PNG = new byte[]{
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52
    };

    private Category testCategory;
    private Brand testBrand;

    @BeforeEach
    void setUp() {
        if (categoryRepository.findAll().isEmpty()) {
            testCategory = categoryRepository.save(new Category("Jewellery Category", "Fine jewels and accessories"));
        } else {
            testCategory = categoryRepository.findAll().get(0);
        }

        if (brandRepository.findAll().isEmpty()) {
            testBrand = brandRepository.save(new Brand("MALKAZ Atelier", "Heritage jewellery"));
        } else {
            testBrand = brandRepository.findAll().get(0);
        }
    }

    @Test
    @DisplayName("Unauthenticated upload request to /admin/uploads/image returns 401")
    void unauthenticatedUploadReturns401() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "gem.png", "image/png", VALID_PNG);
        mockMvc.perform(multipart("/admin/uploads/image").file(file))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("CUSTOMER role cannot upload images to /admin/uploads/image (returns 403)")
    @WithMockUser(username = "customer@example.com", roles = {"CUSTOMER"})
    void customerCannotUploadImage() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "gem.png", "image/png", VALID_PNG);
        mockMvc.perform(multipart("/admin/uploads/image").file(file))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("ADMIN role can upload image to /admin/uploads/image (returns 200 with accessible URL)")
    @WithMockUser(username = "admin@example.com", roles = {"ADMIN"})
    void adminCanUploadImage() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "necklace.png", "image/png", VALID_PNG);
        mockMvc.perform(multipart("/admin/uploads/image").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.fileName").isString())
                .andExpect(jsonPath("$.data.imageUrl").isString())
                .andExpect(jsonPath("$.data.relativeUrl").isString());
    }

    @Test
    @DisplayName("STAFF role can upload image to /uploads/image (returns 200)")
    @WithMockUser(username = "staff@example.com", roles = {"STAFF"})
    void staffCanUploadImage() throws Exception {
        MockMultipartFile file = new MockMultipartFile("image", "earrings.png", "image/png", VALID_PNG);
        mockMvc.perform(multipart("/uploads/image").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.fileName").isString());
    }

    @Test
    @DisplayName("Anonymous users can retrieve uploaded images via GET /uploads/{fileName}")
    @WithMockUser(username = "admin@example.com", roles = {"ADMIN"})
    void anonymousUserCanRetrieveUploadedImage() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "ring-retrieve.png", "image/png", VALID_PNG);
        MvcResult uploadResult = mockMvc.perform(multipart("/admin/uploads/image").file(file))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = uploadResult.getResponse().getContentAsString();
        String fileName = objectMapper.readTree(responseBody).path("data").path("fileName").asText();

        // Perform GET anonymously (no WithMockUser)
        mockMvc.perform(get("/uploads/" + fileName))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(header().string("Cache-Control", "public, max-age=86400"));
    }

    @Test
    @DisplayName("Non-existent image via GET /uploads/{fileName} returns 404")
    void nonExistentImageReturns404() throws Exception {
        mockMvc.perform(get("/uploads/missing-image-999.png"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Uploading invalid content with image extension returns 400 Bad Request")
    @WithMockUser(username = "admin@example.com", roles = {"ADMIN"})
    void uploadingInvalidContentReturns400() throws Exception {
        MockMultipartFile fakeFile = new MockMultipartFile("file", "malware.png", "image/png",
                "<!DOCTYPE html><html><body>executable</body></html>".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/admin/uploads/image").file(fakeFile))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Existing JSON product creation and update endpoints continue working seamlessly")
    @WithMockUser(username = "admin@example.com", roles = {"ADMIN"})
    void existingJsonProductCreationAndUpdatesWork() throws Exception {
        String uniqueSku = "SKU-JSON-" + System.currentTimeMillis();
        ProductRequest request = new ProductRequest(
                "Royal Kundan Choker", uniqueSku, BigDecimal.valueOf(450.00), null,
                15, 3, testBrand.getId(), testCategory.getId(),
                "Handcrafted choker necklace", "Royal Kundan",
                "https://images.unsplash.com/photo-1599643478518-a784e5dc4c8f",
                true, true, null, null, null, null, null, "Standard", null
        );

        MvcResult result = mockMvc.perform(post("/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Royal Kundan Choker"))
                .andExpect(jsonPath("$.data.sku").value(uniqueSku))
                .andReturn();

        Long productId = objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("id").asLong();

        // Also test admin path /admin/products/{id} with JSON update
        ProductRequest updateReq = new ProductRequest(
                "Royal Kundan Choker Updated", uniqueSku, BigDecimal.valueOf(480.00), null,
                12, 3, testBrand.getId(), testCategory.getId(),
                "Updated description", "Royal Kundan",
                "https://images.unsplash.com/photo-1599643478518-a784e5dc4c8f",
                true, true, null, null, null, null, null, "Standard", null
        );

        mockMvc.perform(put("/admin/products/" + productId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Royal Kundan Choker Updated"));
    }

    @Test
    @DisplayName("Product creation with multipart/form-data and actual image file succeeds")
    @WithMockUser(username = "admin@example.com", roles = {"ADMIN"})
    void multipartProductCreationWithImageFileSucceeds() throws Exception {
        String uniqueSku = "SKU-MP-" + System.currentTimeMillis();
        MockMultipartFile image = new MockMultipartFile("image", "emerald-ring.png", "image/png", VALID_PNG);

        mockMvc.perform(multipart("/admin/products")
                        .file(image)
                        .param("name", "Emerald Cocktail Ring")
                        .param("sku", uniqueSku)
                        .param("price", "299.99")
                        .param("stockQuantity", "10")
                        .param("lowStockThreshold", "2")
                        .param("brandId", String.valueOf(testBrand.getId()))
                        .param("categoryId", String.valueOf(testCategory.getId()))
                        .param("description", "Vibrant Colombian emerald set in 18k yellow gold")
                        .param("active", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Emerald Cocktail Ring"))
                .andExpect(jsonPath("$.data.sku").value(uniqueSku))
                .andExpect(jsonPath("$.data.imageUrl").isString());
    }

    @Test
    @DisplayName("Direct product image upload via POST /products/{id}/image updates product image")
    @WithMockUser(username = "admin@example.com", roles = {"ADMIN"})
    void directProductImageUploadWorks() throws Exception {
        String uniqueSku = "SKU-IMG-" + System.currentTimeMillis();
        ProductRequest request = new ProductRequest(
                "Solitaire Diamond Pendant", uniqueSku, BigDecimal.valueOf(799.00), null,
                5, 2, testBrand.getId(), testCategory.getId(),
                "Brilliant cut diamond pendant", "Diamond pendant",
                null, true, false, null, null, null, null, null, null, null
        );

        MvcResult createResult = mockMvc.perform(post("/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        Long productId = objectMapper.readTree(createResult.getResponse().getContentAsString()).path("data").path("id").asLong();

        MockMultipartFile image = new MockMultipartFile("file", "pendant.png", "image/png", VALID_PNG);
        mockMvc.perform(multipart("/admin/products/" + productId + "/image").file(image))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.imageUrl").isString());
    }

    @Test
    @DisplayName("Category creation via JSON and multipart/form-data both succeed")
    @WithMockUser(username = "admin@example.com", roles = {"ADMIN"})
    void categoryCreationJsonAndMultipartWork() throws Exception {
        // 1. JSON creation
        String catJson = "{\"name\":\"Bridal Jewels " + System.currentTimeMillis() + "\",\"description\":\"Wedding sets\"}";
        MvcResult jsonResult = mockMvc.perform(post("/admin/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(catJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();

        Long catId = objectMapper.readTree(jsonResult.getResponse().getContentAsString()).path("data").path("id").asLong();

        // 2. Direct Category image upload
        MockMultipartFile catImage = new MockMultipartFile("file", "bridal.png", "image/png", VALID_PNG);
        mockMvc.perform(multipart("/admin/categories/" + catId + "/image").file(catImage))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.imageUrl").isString());

        // 3. Multipart category creation with file
        MockMultipartFile catFile = new MockMultipartFile("image", "vintage.png", "image/png", VALID_PNG);
        mockMvc.perform(multipart("/admin/categories")
                        .file(catFile)
                        .param("name", "Vintage Collection " + System.currentTimeMillis())
                        .param("description", "Antique finished pieces"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.imageUrl").isString());
    }
}

