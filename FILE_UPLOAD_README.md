# 📁 File Upload & Image Storage System — Backend Documentation

> **Complete Architecture, API Specifications, Security Protocols, and Implementation Guide for File & Image Uploads in the MyBeautify E-Commerce Backend (Spring Boot 3 + Java 21).**

---

## 📑 Table of Contents

1. [Executive Overview](#1-executive-overview)
2. [Key Capabilities & Architectural Highlights](#2-key-capabilities--architectural-highlights)
3. [Architecture & Component Breakdown](#3-architecture--component-breakdown)
   - [Service Layer (`FileStorageService`)](#31-service-layer-filestorageservice)
   - [Controller Endpoints (`FileUploadController`, `AdminController`, `ProductController`, `CatalogController`)](#32-controller-endpoints)
   - [Data Transfer Objects (`ImageUploadResponse`, `StoredFile`)](#33-data-transfer-objects)
4. [Configuration Reference (`application.yml` & Environment Variables)](#4-configuration-reference)
5. [REST API Endpoint Specifications & cURL Examples](#5-rest-api-endpoint-specifications--curl-examples)
   - [`POST /api/uploads/image` (Standalone Image Upload)](#51-post-apiuploadsimage)
   - [`GET /api/uploads/{fileName}` (Public Image Retrieval)](#52-get-apiuploadsfilename)
   - [`POST /api/products/{id}/image` (Update Product Image)](#53-post-apiproductsidimage)
   - [`POST /api/products` (Multipart Product Creation)](#54-post-apiproducts-multipart)
   - [`POST /api/admin/categories/{id}/image` (Update Category Image)](#55-post-apiadmincategoriesidimage)
6. [Security Architecture & Threat Mitigation](#6-security-architecture--threat-mitigation)
   - [Magic Byte Deep Content Inspection](#61-magic-byte-deep-content-inspection)
   - [Path Traversal & Directory Escape Prevention](#62-path-traversal-prevention)
   - [UUID-Based Safe Filename Sanitization](#63-uuid-based-safe-filename-sanitization)
   - [Orphaned File Auto-Cleanup Protection](#64-orphaned-file-auto-cleanup-protection)
   - [Role-Based Access Control (RBAC)](#65-role-based-access-control-rbac)
7. [Automated Testing Suite](#7-automated-testing-suite)
8. [Production Deployment & Persistent Storage (Docker / Fly.io / VPS)](#8-production-deployment--persistent-storage)

---

## 1. Executive Overview

In modern e-commerce systems, high-quality product imagery and brand visual assets are critical for user experience, search visibility, and conversion rates. Previously, the backend only supported external image URLs (e.g. Unsplash, static CDN links).

The updated backend implementation introduces a **production-grade, secure, local and cloud-ready file upload subsystem**. Built directly into the Spring Boot application, it supports:
- **Direct binary multipart image uploads** (`image/jpeg`, `image/png`, `image/webp`).
- **Deep byte-level validation** (verifying file magic headers, not just file extensions or browser-declared MIME types).
- **Public static asset serving with HTTP caching** (`Cache-Control: public, max-age=86400`).
- **Orphan file lifecycle management** (automatically deleting superseded local images when products or categories are updated/deleted).
- **100% backward compatibility** (JSON payloads with external image URLs continue to function seamlessly alongside multipart uploads).

---

## 2. Key Capabilities & Architectural Highlights

| Feature | Technical Implementation | Business & Operational Value |
| :--- | :--- | :--- |
| **Multi-format Support** | JPEG (`.jpg`, `.jpeg`), PNG (`.png`), and modern WebP (`.webp`). | Optimized compression and mobile storefront performance. |
| **Magic Byte Inspection** | Reads raw initial 12 bytes of file stream to verify genuine binary signatures. | Prevents executable/script injection disguised as image files (`.php.png`, `.exe`). |
| **Path Traversal Shield** | Rejects filenames with `..`, `/`, `\`, and verifies target path canonical boundaries. | Prevents overwriting system files or escaping the designated upload directory. |
| **UUID Sanitization** | Assigns `UUID.randomUUID().toString() + "." + canonicalExt`. | Guarantees collision-free storage and eliminates special-character URL encoding bugs. |
| **Orphan File Cleanup** | Detects local uploads via `isLocalUpload` and deletes old files on replacement. | Prevents disk leakage and orphaned asset accumulation over time. |
| **Dual Parameter Flexibility** | Accepts either `file` or `image` parameter names in multipart requests. | Compatible with diverse frontend form builders, mobile clients, and third-party tools. |
| **Dynamic URL Resolution** | Builds `/api/uploads/{uuid}.{ext}` via context path or configured CDN domain. | Supports running behind reverse proxies, Vercel rewrites, custom domains, or CDNs. |

---

## 3. Architecture & Component Breakdown

```
[ Frontend / Admin Client ]
          │
          │ Multipart / Binary Form Upload
          ▼
┌─────────────────────────────────────────────────────────────┐
│                      Spring Security Filter                 │
│  - GET /api/uploads/**          ──► Public Access (permitAll)│
│  - POST /api/uploads/image      ──► Requires ADMIN or STAFF  │
│  - POST /api/products/**        ──► Requires ADMIN           │
└──────────────────────────────┬──────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────┐
│                  Controller Layer                           │
│  - FileUploadController     (/api/uploads)                  │
│  - AdminController          (/api/admin/uploads)            │
│  - ProductController        (/api/products)                 │
│  - CatalogController        (/api/catalog/admin/categories) │
└──────────────────────────────┬──────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────┐
│            FileStorageService / FileStorageServiceImpl      │
│  1. Check file emptiness & size (max 5 MB)                  │
│  2. Path traversal sanitization                             │
│  3. Extension whitelist (.jpg, .jpeg, .png, .webp)          │
│  4. Inspect raw magic bytes (FF D8 FF, 89 50 4E..., RIFF)   │
│  5. Generate secure UUID filename                           │
│  6. Atomic write to storage directory (uploads/)            │
│  7. Return StoredFile(uniqueName, relativeUrl, fullUrl)     │
└──────────────────────────────┬──────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────┐
│                   Local Storage Directory                   │
│                     ./uploads/{uuid}.png                    │
└─────────────────────────────────────────────────────────────┘
```

### 3.1 Service Layer (`FileStorageService`)

Defined in package `com.cosmetics.ecommerce.service`:

```java
public interface FileStorageService {
    StoredFile storeFile(MultipartFile file);
    Resource loadAsResource(String fileName);
    void deleteFileIfExists(String imagePathOrUrl);
    boolean isLocalUpload(String imagePathOrUrl);
    String getUploadDir();
}
```

#### Core Implementation Details (`FileStorageServiceImpl`):
- **Initialization**: Automatically creates target directory upon Spring Boot startup (`Files.createDirectories(this.storageLocation)`).
- **Atomic File Saving**: Uses `Files.copy(inputStream, targetLocation, StandardCopyOption.REPLACE_EXISTING)`.
- **Resource Loading**: Implements `UrlResource` loading with strict canonical boundary checks to safely stream files to clients.
- **Orphan File Detection**: `isLocalUpload` uses regex pattern `^[a-zA-Z0-9\\-_.]+\\.(jpg|jpeg|png|webp)$` ensuring only genuine local uploaded images are deleted, safely ignoring external URLs like Unsplash or AWS S3.

### 3.2 Controller Endpoints

- **`FileUploadController` (`/api/uploads`)**:
  - `POST /api/uploads/image`: Dedicated endpoint for uploading images independently.
  - `GET /api/uploads/{fileName:.+}`: Public retrieval endpoint that dynamically probes content-type and serves file with HTTP caching.
- **`AdminController` (`/api/admin/uploads/image`)**:
  - Administrator convenience route for back-office dashboard media library.
- **`ProductController`**:
  - `POST /api/products`: Supports multipart creation with embedded `image` or `file`.
  - `PUT /api/products/{id}`: Supports multipart update with embedded `image` or `file`.
  - `POST /api/products/{id}/image`: Dedicated endpoint to update/replace only the main image of a product.
- **`CatalogController`**:
  - `POST /api/catalog/admin/categories/{id}/image`: Dedicated category thumbnail replacement.
  - `POST /api/catalog/admin/categories`: Multipart category creation.

### 3.3 Data Transfer Objects

#### `StoredFile` (Internal Service Result Record):
```java
public record StoredFile(
        String fileName,
        String relativeUrl,
        String fullUrl,
        long size,
        String contentType
) {}
```

#### `ImageUploadResponse` (REST API Response Record):
```java
public record ImageUploadResponse(
        String imageUrl,      // Full qualified URL (e.g. https://malkaz.fly.dev/api/uploads/uuid.png)
        String relativeUrl,  // Context-relative URL (e.g. /api/uploads/uuid.png)
        String fileName,     // Generated safe UUID filename
        long size,           // File size in bytes
        String contentType   // Verified MIME type (e.g. image/png)
) {
    public static ImageUploadResponse from(StoredFile stored) {
        return new ImageUploadResponse(
                stored.fullUrl(),
                stored.relativeUrl(),
                stored.fileName(),
                stored.size(),
                stored.contentType()
        );
    }
}
```

---

## 4. Configuration Reference

The subsystem is configured in `application.yml` and can be customized via environment variables:

```yaml
app:
  storage:
    upload-dir: ${UPLOAD_DIR:uploads}
    max-file-size-bytes: ${MAX_FILE_SIZE:5242880} # 5 MB
    base-url: ${STORAGE_BASE_URL:}

server:
  port: ${PORT:8080}
  servlet:
    context-path: /api

spring:
  servlet:
    multipart:
      enabled: true
      max-file-size: 5MB
      max-request-size: 10MB
```

### Environment Variables

| Variable | Default Value | Description |
| :--- | :--- | :--- |
| `UPLOAD_DIR` | `uploads` | Folder path on disk where files are saved. |
| `MAX_FILE_SIZE` | `5242880` (5 MB) | Strict maximum allowed size in bytes per file. |
| `STORAGE_BASE_URL` | *(empty / auto-detect)* | Override for public image URLs (e.g. `https://cdn.example.com` or `https://malkaz.fly.dev`). |

---

## 5. REST API Endpoint Specifications & cURL Examples

### 5.1 `POST /api/uploads/image`
Uploads an image independently to the server.

- **URL**: `POST /api/uploads/image` (or `/api/admin/uploads/image`)
- **Headers**:
  - `Authorization: Bearer <ADMIN_OR_STAFF_JWT_TOKEN>`
  - `Content-Type: multipart/form-data`
- **Form Parameters** *(either one accepted)*:
  - `file`: Binary image file (JPEG, PNG, WebP)
  - `image`: Binary image file (alternative parameter name)

#### cURL Example:
```bash
curl -X POST "https://malkaz.fly.dev/api/uploads/image" \
  -H "Authorization: Bearer YOUR_ADMIN_TOKEN" \
  -F "file=@emerald-ring.png"
```

#### Successful Response (`200 OK`):
```json
{
  "success": true,
  "message": "Image uploaded successfully.",
  "data": {
    "imageUrl": "https://malkaz.fly.dev/api/uploads/e4b95da8-7c8a-4422-b9e1-6784d12c8230.png",
    "relativeUrl": "/api/uploads/e4b95da8-7c8a-4422-b9e1-6784d12c8230.png",
    "fileName": "e4b95da8-7c8a-4422-b9e1-6784d12c8230.png",
    "size": 154820,
    "contentType": "image/png"
  }
}
```

---

### 5.2 `GET /api/uploads/{fileName}`
Retrieves and renders a stored image.

- **URL**: `GET /api/uploads/{fileName}`
- **Authentication**: **Public (No token required)**
- **Response Headers**:
  - `Content-Type: image/png` (or `image/jpeg`, `image/webp`)
  - `Cache-Control: public, max-age=86400` (Enables 24-hour browser & CDN caching)
- **Response Body**: Binary image stream

#### cURL Example:
```bash
curl -i "https://malkaz.fly.dev/api/uploads/e4b95da8-7c8a-4422-b9e1-6784d12c8230.png"
```

---

### 5.3 `POST /api/products/{id}/image`
Directly updates/replaces the primary image of an existing product.

- **URL**: `POST /api/products/{id}/image`
- **Headers**:
  - `Authorization: Bearer <ADMIN_TOKEN>`
  - `Content-Type: multipart/form-data`
- **Form Parameters**: `file` or `image`

#### cURL Example:
```bash
curl -X POST "https://malkaz.fly.dev/api/products/8/image" \
  -H "Authorization: Bearer YOUR_ADMIN_TOKEN" \
  -F "file=@new-diamond-necklace.jpg"
```

#### Successful Response (`200 OK`):
```json
{
  "success": true,
  "message": "Product image updated successfully.",
  "data": {
    "id": 8,
    "name": "Floral Diamond Ring",
    "slug": "floral-diamond-ring",
    "sku": "MZ-RING-08",
    "price": 35000.00,
    "imageUrl": "/api/uploads/c7128f80-60b5-4b08-9df2-f5c71cfb9ad1.jpg",
    "stockQuantity": 9,
    "active": true
  }
}
```

> **Note**: The previous local image is safely and automatically deleted from the server to prevent orphaned file bloat.

---

### 5.4 `POST /api/products` (Multipart Product Creation)
Creates a product while uploading an image in a single HTTP request.

- **URL**: `POST /api/products`
- **Headers**:
  - `Authorization: Bearer <ADMIN_TOKEN>`
  - `Content-Type: multipart/form-data`
- **Form Parameters**:
  - `name`: Product name
  - `sku`: Unique SKU
  - `price`: Product price
  - `stockQuantity`: Stock count
  - `categoryId`: Category ID
  - `brandId`: Brand ID
  - `file` *(optional)*: Binary image file

#### cURL Example:
```bash
curl -X POST "https://malkaz.fly.dev/api/products" \
  -H "Authorization: Bearer YOUR_ADMIN_TOKEN" \
  -F "name=Royal Emerald Pendant" \
  -F "sku=MZ-PEND-01" \
  -F "price=45000" \
  -F "stockQuantity=12" \
  -F "categoryId=2" \
  -F "brandId=1" \
  -F "file=@emerald-pendant.webp"
```

---

### 5.5 `POST /api/admin/categories/{id}/image`
Uploads and updates category banner/thumbnail image.

- **URL**: `POST /api/catalog/admin/categories/{id}/image`
- **Headers**:
  - `Authorization: Bearer <ADMIN_TOKEN>`
  - `Content-Type: multipart/form-data`
- **Form Parameters**: `file` or `image`

#### cURL Example:
```bash
curl -X POST "https://malkaz.fly.dev/api/catalog/admin/categories/3/image" \
  -H "Authorization: Bearer YOUR_ADMIN_TOKEN" \
  -F "file=@rings-category-banner.jpg"
```

---

## 6. Security Architecture & Threat Mitigation

File upload functionality is historically one of the most critical attack vectors in web applications. The subsystem implements **defense-in-depth**:

| Threat Vector | Attack Scenario | Mitigation Strategy in Code |
| :--- | :--- | :--- |
| **Extension Spoofing** | Attacker uploads `malware.exe` renamed to `malware.jpg`. | **Magic Byte Inspection**: Verifies raw binary headers (`FF D8 FF`, `89 50 4E 47...`, `RIFF...WEBP`). Rejects mismatched files with `400 Bad Request`. |
| **Path Traversal** | Attacker uploads `../../etc/cron.d/exploit.png` to write outside upload folder. | Filename sanitization via `StringUtils.cleanPath`, rejection of `..` or path separators, and `targetLocation.startsWith(storageLocation)` verification. |
| **HTML / SVG Stored XSS** | Attacker uploads SVG with embedded JavaScript. | **Strict Format Whitelist**: Only JPEG, PNG, and WebP are allowed. SVGs, PDFs, and HTML are disallowed. |
| **Overwriting Existing Files** | Attacker uploads a file with the same name as an existing asset. | **UUID-based Naming**: Files are renamed with `UUID.randomUUID()` guaranteeing uniqueness. |
| **Denial of Service (Storage Exhaustion)** | Attacker uploads massive files to exhaust server disk space. | Strict two-layer limits: Spring multipart limit (5MB/10MB) + programmatic size validation throwing `400 Bad Request`. |
| **Unauthorized File Access** | Public user tries to upload or delete images. | Spring Security method security `@PreAuthorize("hasAnyRole('ADMIN','STAFF')")`. |

### 6.1 Magic Byte Deep Content Inspection

The service validates the first 12 bytes of the uploaded stream:

```java
// JPEG Magic Bytes: FF D8 FF
if ((header[0] & 0xFF) == 0xFF && (header[1] & 0xFF) == 0xD8 && (header[2] & 0xFF) == 0xFF) {
    return "image/jpeg";
}

// PNG Magic Bytes: 89 50 4E 47 0D 0A 1A 0A
if ((header[0] & 0xFF) == 0x89 && header[1] == 0x50 && header[2] == 0x4E && header[3] == 0x47
        && header[4] == 0x0D && header[5] == 0x0A && header[6] == 0x1A && header[7] == 0x0A) {
    return "image/png";
}

// WebP: RIFF at offset 0..3, WEBP at offset 8..11
if (header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F'
        && header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P') {
    return "image/webp";
}
```

### 6.2 Path Traversal Prevention

Both upload and retrieval enforce canonical directory boundaries:
```java
Path filePath = this.storageLocation.resolve(fileName).normalize();
if (!filePath.startsWith(this.storageLocation)) {
    throw new BadRequestException("Access denied: File path outside upload directory.");
}
```

### 6.3 UUID-Based Safe Filename Sanitization

Original user-submitted filenames (e.g. `My Wedding Ring (Final) #1.png`) are replaced with clean, safe UUID strings:
```java
String uniqueFilename = UUID.randomUUID().toString() + "." + canonicalExt;
// Output: a4e7975d-e593-4e4d-b638-552da91e4599.png
```

### 6.4 Orphaned File Auto-Cleanup Protection

When an image is replaced or deleted:
1. `isLocalUpload(url)` checks if the file belongs to `/uploads/` and matches safe UUID format.
2. If confirmed, `deleteFileIfExists` deletes the file on disk.
3. If the URL belongs to an external CDN or Unsplash, it is safely left untouched.

### 6.5 Role-Based Access Control (RBAC)

```java
// Public access for storefront customers & browsers:
.requestMatchers(HttpMethod.GET, "/uploads/**").permitAll()

// Protected upload routes (Admin & Staff):
@PreAuthorize("hasAnyRole('ADMIN','STAFF')")
@PostMapping(value = "/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
```

---

## 7. Automated Testing Suite

The implementation includes a test suite covering unit logic and full Spring integration:

### 1. `FileStorageServiceTest.java` (Unit Tests)
- `storeValidPngImageSuccessfully()`: Verifies valid PNG store, UUID name, and URL resolution.
- `storeValidJpegImageSuccessfully()`: Verifies valid JPEG store and `.jpg` canonical extension.
- `storeValidWebpImageSuccessfully()`: Verifies valid WebP store.
- `rejectEmptyFile()`: Asserts `400 Bad Request` for 0-byte upload.
- `rejectOversizedFile()`: Asserts `400 Bad Request` when exceeding max size limit.
- `rejectUnsupportedExtension()`: Asserts rejection of `.exe`, `.pdf`, `.sh`.
- `rejectSpoofedMagicBytes()`: Asserts rejection when text file is renamed to `.jpg`.
- `sanitizePathTraversalFilename()`: Verifies rejection of `../../etc/passwd.png`.
- `deleteFileIfExistsSafelyDeletesFile()`: Verifies deletion of local orphaned files while ignoring external URLs.

### 2. `FileUploadIntegrationTest.java` (MockMvc Integration Tests)
- `unauthenticatedUploadReturns401()`: Verifies unauthorized upload is blocked.
- `customerCannotUploadImage()`: Verifies `CUSTOMER` role receives `403 Forbidden`.
- `adminCanUploadImage()`: Verifies `ADMIN` role can successfully upload.
- `staffCanUploadImage()`: Verifies `STAFF` role can successfully upload.
- `anonymousUserCanRetrieveUploadedImage()`: Verifies public `GET /api/uploads/{fileName}` returns `200 OK` with valid `Content-Type` and `Cache-Control`.
- `nonExistentImageReturns404()`: Verifies missing file returns standard `404 Not Found`.
- `uploadingInvalidContentReturns400()`: Verifies spoofed content returns `400 Bad Request`.
- `multipartProductCreationWithImageFileSucceeds()`: Verifies end-to-end product creation with image.
- `directProductImageUploadWorks()`: Verifies `POST /api/products/{id}/image`.
- `existingJsonProductCreationAndUpdatesWork()`: Verifies backward compatibility with JSON.

Run the test suite using Maven:
```bash
./mvnw test -Dtest=FileStorageServiceTest,FileUploadIntegrationTest
```

---

## 8. Production Deployment & Persistent Storage

When deploying the backend in Docker containers (e.g. Fly.io, AWS ECS, VPS Docker Compose), uploaded files are stored in the `./uploads` directory.

### Persistent Volume Configuration (Fly.io / VPS):
To ensure uploaded files persist across container restarts and deployments:

#### Dockerfile:
```dockerfile
# Create uploads directory and ensure correct application permissions
RUN mkdir -p /app/uploads && chown -R appuser:appgroup /app/uploads
VOLUME ["/app/uploads"]
```

#### Fly.io (`fly.toml`):
```toml
[mounts]
  source = "malkaz_uploads_data"
  destination = "/app/uploads"
```

#### Docker Compose:
```yaml
services:
  backend:
    image: malkaz-backend:latest
    volumes:
      - malkaz_uploads:/app/uploads
    environment:
      - UPLOAD_DIR=/app/uploads
      - MAX_FILE_SIZE=5242880

volumes:
  malkaz_uploads:
```

---

## 9. Summary & Quick Reference

| Endpoint | Method | Role Required | Request Format | Response Data |
| :--- | :--- | :--- | :--- | :--- |
| `/api/uploads/image` | `POST` | `ADMIN`, `STAFF` | Multipart (`file` or `image`) | `ImageUploadResponse` (URL, filename, size) |
| `/api/uploads/{fileName}` | `GET` | **Public** | Path Parameter | Binary image stream + Cache header |
| `/api/products/{id}/image` | `POST` | `ADMIN` | Multipart (`file` or `image`) | Updated `ProductResponse` |
| `/api/products` | `POST` | `ADMIN` | Multipart / JSON | Created `ProductResponse` |
| `/api/catalog/admin/categories/{id}/image` | `POST` | `ADMIN` | Multipart (`file` or `image`) | Updated `CategoryResponse` |
| `/api/admin/uploads/image` | `POST` | `ADMIN`, `STAFF` | Multipart (`file` or `image`) | `ImageUploadResponse` |

