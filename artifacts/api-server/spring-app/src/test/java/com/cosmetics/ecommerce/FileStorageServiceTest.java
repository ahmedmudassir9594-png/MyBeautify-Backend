package com.cosmetics.ecommerce;

import com.cosmetics.ecommerce.dto.StoredFile;
import com.cosmetics.ecommerce.exception.BadRequestException;
import com.cosmetics.ecommerce.exception.ResourceNotFoundException;
import com.cosmetics.ecommerce.service.FileStorageServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class FileStorageServiceTest {

    @TempDir
    Path tempDir;

    private FileStorageServiceImpl storageService;

    // Minimal valid headers
    private static final byte[] VALID_PNG = new byte[]{
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52
    };

    private static final byte[] VALID_JPEG = new byte[]{
            (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10, 0x4A, 0x46, 0x49, 0x46, 0x00, 0x01
    };

    private static final byte[] VALID_WEBP = new byte[]{
            'R', 'I', 'F', 'F', 0x20, 0x00, 0x00, 0x00, 'W', 'E', 'B', 'P', 'V', 'P', '8', ' '
    };

    @BeforeEach
    void setUp() {
        storageService = new FileStorageServiceImpl(tempDir.toString(), 5 * 1024 * 1024, "https://malkaz.example.com");
        storageService.init();
    }

    @Test
    @DisplayName("Valid PNG upload succeeds and generates unique filename and URLs")
    void validPngUploadSucceeds() {
        MockMultipartFile file = new MockMultipartFile("file", "ring.png", "image/png", VALID_PNG);
        StoredFile stored = storageService.storeFile(file);

        assertNotNull(stored);
        assertTrue(stored.fileName().endsWith(".png"));
        assertTrue(stored.relativeUrl().startsWith("/api/uploads/"));
        assertTrue(stored.fullUrl().startsWith("https://malkaz.example.com/api/uploads/"));
        assertEquals("image/png", stored.contentType());

        Resource resource = storageService.loadAsResource(stored.fileName());
        assertTrue(resource.exists());
    }

    @Test
    @DisplayName("Valid JPEG upload succeeds")
    void validJpegUploadSucceeds() {
        MockMultipartFile file = new MockMultipartFile("file", "necklace.jpg", "image/jpeg", VALID_JPEG);
        StoredFile stored = storageService.storeFile(file);

        assertNotNull(stored);
        assertTrue(stored.fileName().endsWith(".jpg"));
        assertEquals("image/jpeg", stored.contentType());
    }

    @Test
    @DisplayName("Valid WebP upload succeeds")
    void validWebpUploadSucceeds() {
        MockMultipartFile file = new MockMultipartFile("file", "bangle.webp", "image/webp", VALID_WEBP);
        StoredFile stored = storageService.storeFile(file);

        assertNotNull(stored);
        assertTrue(stored.fileName().endsWith(".webp"));
        assertEquals("image/webp", stored.contentType());
    }

    @Test
    @DisplayName("Empty file is rejected with BadRequestException")
    void emptyFileIsRejected() {
        MockMultipartFile file = new MockMultipartFile("file", "empty.png", "image/png", new byte[0]);
        BadRequestException ex = assertThrows(BadRequestException.class, () -> storageService.storeFile(file));
        assertTrue(ex.getMessage().contains("cannot be empty"));
    }

    @Test
    @DisplayName("Oversized file is rejected with BadRequestException")
    void oversizedFileIsRejected() {
        byte[] oversized = new byte[6 * 1024 * 1024];
        System.arraycopy(VALID_PNG, 0, oversized, 0, VALID_PNG.length);

        MockMultipartFile file = new MockMultipartFile("file", "big.png", "image/png", oversized);
        BadRequestException ex = assertThrows(BadRequestException.class, () -> storageService.storeFile(file));
        assertTrue(ex.getMessage().contains("exceeds maximum allowed limit"));
    }

    @Test
    @DisplayName("Unsupported file extension is rejected")
    void unsupportedExtensionIsRejected() {
        MockMultipartFile file = new MockMultipartFile("file", "script.exe", "image/png", VALID_PNG);
        BadRequestException ex = assertThrows(BadRequestException.class, () -> storageService.storeFile(file));
        assertTrue(ex.getMessage().contains("Unsupported file extension"));
    }

    @Test
    @DisplayName("Disguised text or executable file with image extension is rejected by magic bytes")
    void disguisedFileIsRejectedByMagicBytes() {
        byte[] fakeContent = "MZ... this is a fake executable pretending to be jpg".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "malicious.jpg", "image/jpeg", fakeContent);

        BadRequestException ex = assertThrows(BadRequestException.class, () -> storageService.storeFile(file));
        assertTrue(ex.getMessage().contains("not a valid JPEG, PNG, or WebP image"));
    }

    @Test
    @DisplayName("Path traversal attempts in filename are rejected or sanitized")
    void pathTraversalInFilenameIsRejected() {
        MockMultipartFile file = new MockMultipartFile("file", "../../etc/passwd.png", "image/png", VALID_PNG);
        assertThrows(BadRequestException.class, () -> storageService.storeFile(file));
    }

    @Test
    @DisplayName("Path traversal in loadAsResource is rejected")
    void pathTraversalInLoadIsRejected() {
        assertThrows(BadRequestException.class, () -> storageService.loadAsResource("../secret.txt"));
        assertThrows(BadRequestException.class, () -> storageService.loadAsResource("subdir/test.png"));
    }

    @Test
    @DisplayName("Non-existent file load throws ResourceNotFoundException")
    void nonExistentFileThrowsResourceNotFound() {
        assertThrows(ResourceNotFoundException.class, () -> storageService.loadAsResource("non-existent-file.png"));
    }

    @Test
    @DisplayName("isLocalUpload correctly distinguishes local uploads from external URLs")
    void isLocalUploadDetection() {
        assertTrue(storageService.isLocalUpload("https://malkaz.example.com/api/uploads/1234-5678.jpg"));
        assertTrue(storageService.isLocalUpload("/api/uploads/my-sample-photo.png"));
        assertFalse(storageService.isLocalUpload("https://images.unsplash.com/photo-1556228578"));
        assertFalse(storageService.isLocalUpload("/products/jewels/royal-emerald-necklace.jpg"));
        assertFalse(storageService.isLocalUpload(null));
        assertFalse(storageService.isLocalUpload(""));
    }

    @Test
    @DisplayName("deleteFileIfExists safely deletes orphaned local file")
    void deleteFileIfExistsSafelyDeletesFile() throws IOException {
        Path created = tempDir.resolve("delete-me.png");
        Files.write(created, VALID_PNG);
        assertTrue(Files.exists(created));

        storageService.deleteFileIfExists("/api/uploads/delete-me.png");
        assertFalse(Files.exists(created));

        // Calling again does not throw
        assertDoesNotThrow(() -> storageService.deleteFileIfExists("/api/uploads/delete-me.png"));
        // Calling with external URL does nothing and does not throw
        assertDoesNotThrow(() -> storageService.deleteFileIfExists("https://unsplash.com/photo.jpg"));
    }
}

