package com.cosmetics.ecommerce.controller;

import com.cosmetics.ecommerce.dto.ApiResponse;
import com.cosmetics.ecommerce.dto.ImageUploadResponse;
import com.cosmetics.ecommerce.dto.StoredFile;
import com.cosmetics.ecommerce.exception.BadRequestException;
import com.cosmetics.ecommerce.service.FileStorageService;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.nio.file.Files;

@RestController
@RequestMapping("/uploads")
public class FileUploadController {

    private final FileStorageService fileStorageService;

    public FileUploadController(FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }

    @GetMapping("/{fileName:.+}")
    public ResponseEntity<Resource> serveImage(@PathVariable String fileName) {
        Resource resource = fileStorageService.loadAsResource(fileName);
        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;

        try {
            File file = resource.getFile();
            String probed = Files.probeContentType(file.toPath());
            if (probed != null && !probed.isBlank()) {
                mediaType = MediaType.parseMediaType(probed);
            } else {
                mediaType = determineMediaTypeFromExtension(fileName);
            }
        } catch (Exception ignored) {
            mediaType = determineMediaTypeFromExtension(fileName);
        }

        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
                .body(resource);
    }

    @PostMapping(value = "/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    public ApiResponse<ImageUploadResponse> uploadImage(
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "image", required = false) MultipartFile image) {
        MultipartFile uploadFile = file != null ? file : image;
        if (uploadFile == null || uploadFile.isEmpty()) {
            throw new BadRequestException("Uploaded file cannot be empty.");
        }
        StoredFile stored = fileStorageService.storeFile(uploadFile);
        return ApiResponse.ok("Image uploaded successfully.", ImageUploadResponse.from(stored));
    }

    private MediaType determineMediaTypeFromExtension(String fileName) {
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".png")) {
            return MediaType.IMAGE_PNG;
        } else if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return MediaType.IMAGE_JPEG;
        } else if (lower.endsWith(".webp")) {
            return MediaType.parseMediaType("image/webp");
        }
        return MediaType.APPLICATION_OCTET_STREAM;
    }
}

