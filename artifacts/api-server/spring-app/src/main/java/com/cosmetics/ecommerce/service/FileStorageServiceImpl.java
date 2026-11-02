package com.cosmetics.ecommerce.service;

import com.cosmetics.ecommerce.dto.StoredFile;
import com.cosmetics.ecommerce.exception.BadRequestException;
import com.cosmetics.ecommerce.exception.ResourceNotFoundException;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class FileStorageServiceImpl implements FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(FileStorageServiceImpl.class);

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/jpg",
            "image/png",
            "image/webp"
    );

    private static final Pattern SAFE_FILENAME_PATTERN = Pattern.compile("^[a-zA-Z0-9\\-_.]+\\.(jpg|jpeg|png|webp)$", Pattern.CASE_INSENSITIVE);

    private final Path storageLocation;
    private final String uploadDir;
    private final long maxFileSizeBytes;
    private final String baseUrl;

    public FileStorageServiceImpl(
            @Value("${app.storage.upload-dir:uploads}") String uploadDir,
            @Value("${app.storage.max-file-size-bytes:5242880}") long maxFileSizeBytes,
            @Value("${app.storage.base-url:}") String baseUrl) {
        this.uploadDir = uploadDir;
        this.maxFileSizeBytes = maxFileSizeBytes;
        this.baseUrl = baseUrl != null ? baseUrl.trim() : "";
        this.storageLocation = Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    @PostConstruct
    public void init() {
        try {
            Files.createDirectories(this.storageLocation);
            log.info("Image storage initialized at: {}", this.storageLocation);
        } catch (IOException e) {
            throw new RuntimeException("Could not initialize upload storage folder: " + this.storageLocation, e);
        }
    }

    @Override
    public StoredFile storeFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Uploaded file cannot be empty.");
        }

        if (file.getSize() > maxFileSizeBytes) {
            long maxMb = maxFileSizeBytes / (1024 * 1024);
            throw new BadRequestException(String.format("File size (%d bytes) exceeds maximum allowed limit of %d MB.", file.getSize(), maxMb));
        }

        String rawOriginalFilename = file.getOriginalFilename();
        if (rawOriginalFilename == null || rawOriginalFilename.isBlank()) {
            throw new BadRequestException("Uploaded file must have a valid filename.");
        }

        String cleanFilename = StringUtils.cleanPath(rawOriginalFilename);
        if (cleanFilename.contains("..") || cleanFilename.contains("/") || cleanFilename.contains("\\")) {
            throw new BadRequestException("Filename contains invalid path characters: " + rawOriginalFilename);
        }

        String extension = getExtension(cleanFilename);
        if (!ALLOWED_EXTENSIONS.contains(extension.toLowerCase(Locale.ROOT))) {
            throw new BadRequestException("Unsupported file extension ." + extension + ". Allowed formats: JPG, JPEG, PNG, WEBP.");
        }

        String declaredContentType = file.getContentType();
        if (declaredContentType != null && !declaredContentType.isBlank()) {
            String primaryContentType = declaredContentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
            if (!ALLOWED_CONTENT_TYPES.contains(primaryContentType)) {
                throw new BadRequestException("Unsupported Content-Type: " + declaredContentType + ". Allowed: image/jpeg, image/png, image/webp.");
            }
        }

        // Validate actual file content via magic byte inspection
        byte[] header = new byte[12];
        int bytesRead;
        try (InputStream is = file.getInputStream()) {
            bytesRead = is.readNBytes(header, 0, header.length);
        } catch (IOException e) {
            throw new BadRequestException("Could not read uploaded file: " + e.getMessage());
        }

        if (bytesRead < 12) {
            throw new BadRequestException("Uploaded file is too small or malformed to be a valid image.");
        }

        String detectedMimeType = detectImageMimeType(header);
        if (detectedMimeType == null) {
            throw new BadRequestException("Invalid file content: The uploaded file is not a valid JPEG, PNG, or WebP image.");
        }

        if (!isCompatible(extension, detectedMimeType)) {
            throw new BadRequestException(String.format("File extension '.%s' does not match verified image content type '%s'.", extension, detectedMimeType));
        }

        // Canonical extension based on verified magic bytes
        String canonicalExt = switch (detectedMimeType) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            default -> extension.toLowerCase(Locale.ROOT);
        };

        String uniqueFilename = UUID.randomUUID().toString() + "." + canonicalExt;
        Path targetLocation = this.storageLocation.resolve(uniqueFilename).normalize();

        if (!targetLocation.startsWith(this.storageLocation)) {
            throw new BadRequestException("Security violation: destination path resolves outside the upload directory.");
        }

        try (InputStream is = file.getInputStream()) {
            Files.copy(is, targetLocation, StandardCopyOption.REPLACE_EXISTING);
            log.info("Stored uploaded image as {} (size: {} bytes, type: {})", uniqueFilename, file.getSize(), detectedMimeType);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save uploaded image file: " + e.getMessage(), e);
        }

        String relativeUrl = "/api/uploads/" + uniqueFilename;
        String fullUrl = buildFullUrl(uniqueFilename, relativeUrl);

        return new StoredFile(uniqueFilename, relativeUrl, fullUrl, file.getSize(), detectedMimeType);
    }

    @Override
    public Resource loadAsResource(String fileName) {
        if (fileName == null || fileName.isBlank() || fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
            throw new BadRequestException("Invalid file name: " + fileName);
        }
        try {
            Path filePath = this.storageLocation.resolve(fileName).normalize();
            if (!filePath.startsWith(this.storageLocation)) {
                throw new BadRequestException("Access denied: File path outside upload directory.");
            }
            Resource resource = new UrlResource(filePath.toUri());
            if (resource.exists() && resource.isReadable()) {
                return resource;
            } else {
                throw new ResourceNotFoundException("Image file not found: " + fileName);
            }
        } catch (MalformedURLException e) {
            throw new ResourceNotFoundException("Image file not found: " + fileName);
        }
    }

    @Override
    public boolean isLocalUpload(String imagePathOrUrl) {
        if (imagePathOrUrl == null || imagePathOrUrl.isBlank()) {
            return false;
        }
        int uploadIndex = imagePathOrUrl.lastIndexOf("/uploads/");
        if (uploadIndex == -1) {
            return false;
        }
        String fileName = imagePathOrUrl.substring(uploadIndex + "/uploads/".length()).trim();
        if (fileName.isEmpty() || fileName.contains("/") || fileName.contains("\\") || fileName.contains("..")) {
            return false;
        }
        return SAFE_FILENAME_PATTERN.matcher(fileName).matches();
    }

    @Override
    public void deleteFileIfExists(String imagePathOrUrl) {
        if (!isLocalUpload(imagePathOrUrl)) {
            return;
        }
        try {
            int uploadIndex = imagePathOrUrl.lastIndexOf("/uploads/");
            String fileName = imagePathOrUrl.substring(uploadIndex + "/uploads/".length()).trim();
            Path filePath = this.storageLocation.resolve(fileName).normalize();
            if (filePath.startsWith(this.storageLocation)) {
                boolean deleted = Files.deleteIfExists(filePath);
                if (deleted) {
                    log.info("Safely deleted orphaned uploaded image: {}", fileName);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to delete orphaned image file {}: {}", imagePathOrUrl, e.getMessage());
        }
    }

    @Override
    public String getUploadDir() {
        return this.uploadDir;
    }

    private String detectImageMimeType(byte[] header) {
        if (header == null || header.length < 12) {
            return null;
        }
        // JPEG magic bytes: FF D8 FF
        if ((header[0] & 0xFF) == 0xFF && (header[1] & 0xFF) == 0xD8 && (header[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        // PNG magic bytes: 89 50 4E 47 0D 0A 1A 0A
        if ((header[0] & 0xFF) == 0x89 && header[1] == 0x50 && header[2] == 0x4E && header[3] == 0x47
                && header[4] == 0x0D && header[5] == 0x0A && header[6] == 0x1A && header[7] == 0x0A) {
            return "image/png";
        }
        // WebP: RIFF at 0..3, WEBP at 8..11
        if (header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F'
                && header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P') {
            return "image/webp";
        }
        return null;
    }

    private boolean isCompatible(String extension, String mimeType) {
        String ext = extension.toLowerCase(Locale.ROOT);
        return switch (mimeType) {
            case "image/jpeg" -> ext.equals("jpg") || ext.equals("jpeg");
            case "image/png" -> ext.equals("png");
            case "image/webp" -> ext.equals("webp");
            default -> false;
        };
    }

    private String getExtension(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex == -1 || dotIndex == filename.length() - 1) {
            return "";
        }
        return filename.substring(dotIndex + 1);
    }

    private String buildFullUrl(String fileName, String relativeUrl) {
        if (!baseUrl.isEmpty()) {
            String clean = baseUrl.replaceAll("/+$", "");
            if (!clean.endsWith("/api")) {
                clean = clean + "/api";
            }
            return clean + "/uploads/" + fileName;
        }
        try {
            return ServletUriComponentsBuilder.fromCurrentContextPath()
                    .path("/uploads/")
                    .path(fileName)
                    .toUriString();
        } catch (Exception e) {
            // When outside an active HTTP request (e.g. background job or mock test)
            return relativeUrl;
        }
    }
}

