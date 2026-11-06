# Category & Subcategory Management — MALKAZ Backend

## 1. Feature Overview & Architecture

This document describes the complete architecture and implementation of the **Category and Subcategory Management Engine** for the MALKAZ Fine Jewellery Spring Boot backend (`cosmetics-ecommerce-backend`).

### 1.1 The Business Need
The MALKAZ Admin Panel allows jewellery administrators to organize luxury collections with hierarchical precision:
- **Parent Category Level**: High-level jewellery collections (e.g., *Snake*, *I6*, *Necklaces*, *Earrings*, *Rings*).
- **Subcategory Level**: Specific styles and designs under each parent category (e.g., *Pendant Necklaces*, *Gold Chains*, *Jhumka Earrings*).
- **Customer Experience**: Customers browsing the store can navigate from top-level categories to targeted subcategories (`Home → Categories → Necklaces → Subcategories → Products → Product Details`), while receiving accurate subcategory counts and image representations without duplicate or dummy data.

```mermaid
flowchart TD
    subgraph Database["Database Schema"]
        C[(categories)]
        S[(subcategories)]
        P[(products)]
        C -->|1 : N (RESTRICT)| S
        C -->|1 : N| P
        S -.->|1 : N (SET NULL)| P
    end

    subgraph AdminLayer["Admin Operations (ADMIN / STAFF)"]
        AC[AdminSubcategoryController]
        CC_Admin[CatalogController - /admin/categories]
        AC -->|CRUD + Status + Multipart Image| SS[SubcategoryService]
        CC_Admin -->|Safe Archive / Counts| CS[CatalogService]
    end

    subgraph CustomerLayer["Customer Public APIs (Anonymous)"]
        Pub_C[CatalogController - /api/categories]
        Pub_S[SubcategoryController - /api/subcategories]
        Pub_P[ProductController - /api/products]
        Pub_C -->|Enriched with Active Subcategories & Counts| CS
        Pub_S -->|Active Subcategories by Category/Slug| SS
        Pub_P -->|Filter by Subcategory ID| PS[ProductService]
    end

    SS --> S
    CS --> C
    CS --> SS
    PS --> P
```

---

## 2. Category-to-Subcategory Domain Relationship

### 2.1 One-Level Hierarchy
- Every subcategory **must** belong to a valid parent category (`optional = false`, `nullable = false`).
- A parent category contains **zero or more** subcategories.
- Deleting or archiving a category is guarded by **safe deletion rules** (will reject deletion if active products or subcategories exist).
- Subcategory names are validated for **case-insensitive uniqueness** within the same parent category, while allowing the same subcategory name under different parent categories (e.g., "Classic" can exist under *Necklaces* and under *Earrings*).

---

## 3. Database Schema & Migration (`V5__create_subcategories_table.sql`)

The migration script is located at `src/main/resources/db/migration/V5__create_subcategories_table.sql`:

```sql
-- Migration: V5 Create subcategories table and associate with categories and products

CREATE TABLE IF NOT EXISTS subcategories (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    slug VARCHAR(150),
    description VARCHAR(1000),
    image_url VARCHAR(500),
    category_id BIGINT NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_subcategory_category FOREIGN KEY (category_id) REFERENCES categories(id) ON DELETE RESTRICT,
    CONSTRAINT uk_subcategory_category_name UNIQUE (category_id, name)
);

CREATE INDEX idx_subcategory_category ON subcategories(category_id);
CREATE INDEX idx_subcategory_active ON subcategories(active);
CREATE INDEX idx_subcategory_name ON subcategories(name);

ALTER TABLE products ADD COLUMN subcategory_id BIGINT NULL;
ALTER TABLE products ADD CONSTRAINT fk_product_subcategory FOREIGN KEY (subcategory_id) REFERENCES subcategories(id) ON DELETE SET NULL;
CREATE INDEX idx_product_subcategory ON products(subcategory_id);
```

---

## 4. Entity, Repository, Service & Controller Architecture

### 4.1 Entities & Repositories
- **`Subcategory`** (`com.cosmetics.ecommerce.entity.Subcategory`):
  - Primary key `id`, `name`, `slug`, `description`, `imageUrl`, `@ManyToOne Category category`, `active`, timestamps (`createdAt`, `updatedAt`).
  - `@PrePersist` and `@PreUpdate` sanitize names and auto-generate clean slugs.
- **`SubcategoryRepository`** (`com.cosmetics.ecommerce.repository.SubcategoryRepository`):
  - High-performance queries with `JOIN FETCH s.category` to prevent $N+1$ select issues.
  - Aggregated count queries: `countSubcategoriesGroupedByCategory()` and `countActiveSubcategoriesGroupedByCategory()` compute counts in a single SQL operation.
- **`Product`** (`com.cosmetics.ecommerce.entity.Product`):
  - Optional `@ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "subcategory_id") private Subcategory subcategory;` linking products to specific jewellery subcategories.

### 4.2 DTOs (`SubcategoryDtos.java` & `CatalogDtos.java`)
- **`SubcategoryRequest`**:
  ```java
  public record SubcategoryRequest(
      @NotBlank @Size(max = 120) String name,
      @NotNull Long categoryId,
      @Size(max = 1000) String description,
      String imageUrl,
      Boolean active
  ) {}
  ```
- **`SubcategoryResponse`**:
  ```java
  public record SubcategoryResponse(
      Long id, String name, String slug, String description, String imageUrl,
      Long categoryId, String categoryName, boolean active, long productCount,
      Instant createdAt, Instant updatedAt
  ) {}
  ```
- **`CategoryResponse` (Backward-Compatible Record)**:
  ```java
  public record CategoryResponse(
      Long id, String name, String description, String imageUrl, boolean active,
      long subcategoriesCount, List<SubcategoryResponse> subcategories
  ) { ... }
  ```

### 4.3 Core Services
- **`SubcategoryService`** (`com.cosmetics.ecommerce.service.SubcategoryService`):
  - Full CRUD lifecycle with input validation, duplicate checks, image cleanup, safe deletion, and audit logging.
- **`CatalogService`** (`com.cosmetics.ecommerce.service.CatalogService`):
  - Category retrieval enriched with `subcategoriesCount` and active `subcategories`.
  - Guarded deletion: rejects deleting categories that contain active products or subcategories.

---

## 5. API Reference

### 5.1 Admin Endpoints (Requires `ADMIN` or `STAFF` Role)

Base Path: `/api/admin/subcategories` (or `/admin/subcategories`)

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/admin/subcategories` | Create subcategory (JSON or `multipart/form-data`) |
| `POST` | `/api/admin/subcategories/upload-image` | Standalone image upload for subcategory |
| `GET` | `/api/admin/subcategories` | Paginated listing with optional `query`, `categoryId`, `active` |
| `GET` | `/api/admin/subcategories/{id}` | Retrieve subcategory by ID with product count |
| `PUT` | `/api/admin/subcategories/{id}` | Update subcategory (JSON or `multipart/form-data`) |
| `PATCH` | `/api/admin/subcategories/{id}/status` | Activate or deactivate subcategory |
| `DELETE` | `/api/admin/subcategories/{id}` | Safely delete subcategory (fails if products attached) |
| `GET` | `/api/admin/categories/{categoryId}/subcategories` | List all subcategories under category (active & inactive) |

#### A. Create Subcategory (JSON)
- **URL**: `POST /api/admin/subcategories`
- **Headers**: `Authorization: Bearer <ADMIN_TOKEN>`, `Content-Type: application/json`
- **Request Body**:
  ```json
  {
    "name": "Pendant Necklaces",
    "categoryId": 1,
    "description": "Elegant pendant necklaces collection",
    "imageUrl": "https://malkaz.com/images/pendant.jpg",
    "active": true
  }
  ```
- **Response (HTTP 200)**:
  ```json
  {
    "success": true,
    "message": "Subcategory created successfully.",
    "data": {
      "id": 10,
      "name": "Pendant Necklaces",
      "slug": "pendant-necklaces",
      "description": "Elegant pendant necklaces collection",
      "imageUrl": "https://malkaz.com/images/pendant.jpg",
      "categoryId": 1,
      "categoryName": "Snake",
      "active": true,
      "productCount": 0,
      "createdAt": "2026-11-05T18:00:00Z",
      "updatedAt": "2026-11-05T18:00:00Z"
    }
  }
  ```

#### B. Update Subcategory Status
- **URL**: `PATCH /api/admin/subcategories/10/status?active=false`
- **Response (HTTP 200)**:
  ```json
  {
    "success": true,
    "message": "Subcategory status updated successfully.",
    "data": {
      "id": 10,
      "name": "Pendant Necklaces",
      "active": false
    }
  }
  ```

#### C. Delete Subcategory Safely
- **URL**: `DELETE /api/admin/subcategories/10`
- **Response (HTTP 200)**:
  ```json
  {
    "success": true,
    "message": "Subcategory deleted successfully.",
    "data": null
  }
  ```

---

### 5.2 Public Customer-Facing Endpoints (No Authentication Required)

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/categories` | Returns active categories with subcategory count and active subcategories |
| `GET` | `/api/categories/{categoryId}/subcategories` | Returns active subcategories belonging to a category |
| `GET` | `/api/subcategories` | List active subcategories (optional `?categoryId=1`) |
| `GET` | `/api/subcategories/{id}` | Get active subcategory by ID |
| `GET` | `/api/subcategories/slug/{slug}` | Get active subcategory by slug |
| `GET` | `/api/products?subcategoryId={id}` | Filter catalogue products by subcategory |
| `GET` | `/api/products/subcategories/{id}/products` | Dedicated subcategory products endpoint |

#### A. Customer Categories Listing (Section 12)
- **URL**: `GET /api/categories`
- **Response (HTTP 200)**:
  ```json
  {
    "success": true,
    "message": "Category fetched successfully.",
    "data": [
      {
        "id": 1,
        "name": "Snake",
        "description": "Premium snake design collection",
        "imageUrl": "https://malkaz.com/images/snake.jpg",
        "active": true,
        "subcategoriesCount": 3,
        "subcategories": [
          {
            "id": 10,
            "name": "Pendant Necklaces",
            "slug": "pendant-necklaces",
            "description": "Elegant pendant necklaces collection",
            "imageUrl": "https://malkaz.com/images/pendant.jpg",
            "categoryId": 1,
            "categoryName": "Snake",
            "active": true,
            "productCount": 14
          },
          {
            "id": 11,
            "name": "Gold Chains",
            "slug": "gold-chains",
            "description": "Premium gold chain designs",
            "imageUrl": "https://malkaz.com/images/chains.jpg",
            "categoryId": 1,
            "categoryName": "Snake",
            "active": true,
            "productCount": 8
          }
        ]
      }
    ]
  }
  ```

---

## 6. Validation & Error Handling

| Scenario | HTTP Status | Response Details |
|---|---|---|
| Blank subcategory name | `400 Bad Request` | `"Subcategory name cannot be blank."` |
| Non-existent parent category | `404 Not Found` | `"Parent category not found with id: 999"` |
| Duplicate name under same category | `409 Conflict` | `"Subcategory with name 'Pendant Necklaces' already exists under category 'Snake'."` |
| Delete category with subcategories | `400 Bad Request` | `"Cannot delete category 'Snake' because 3 subcategory(ies) are associated with it. Please reassign or delete subcategories first."` |
| Delete subcategory with products | `400 Bad Request` | `"Cannot delete subcategory 'Gold Chains' because 8 product(s) are associated with it. Please reassign or delete the products first."` |
| Non-admin access to `/admin/**` | `401 / 403` | `"Full authentication is required..."` / `"You do not have permission..."` |

---

## 7. Image Handling & Orphan Cleanup

- Subcategories support file uploads via `multipart/form-data` with `image` or `file` form parameters.
- Reuses `FileStorageService.storeFile(file)` returning `/uploads/{uuid}.{ext}`.
- When a subcategory image is replaced or deleted, `cleanupImageIfOrphaned(imageUrl)` checks if any other category, subcategory, or product references the file before deleting it from local storage.

---

## 8. Audit Logging

All administrative operations emit audit entries via `AuditLogService`:
- `AuditAction.SUBCATEGORY_CREATE`
- `AuditAction.SUBCATEGORY_UPDATE`
- `AuditAction.SUBCATEGORY_STATUS_CHANGE`
- `AuditAction.SUBCATEGORY_DELETE`
- `AuditAction.CATEGORY_CREATE`
- `AuditAction.CATEGORY_UPDATE`
- `AuditAction.CATEGORY_DELETE`

---

## 9. Automated Testing Guide & Results

### 9.1 Test Classes
1. **`SubcategoryServiceTest.java`** (12 unit tests):
   - Creation validation, blank names, null parent, duplicate prevention, cross-category name reuse.
   - Status toggle, safe deletion rejection when products exist, image cleanup.
2. **`SubcategoryIntegrationTest.java`** (12 integration tests):
   - MockMvc tests for `POST /admin/subcategories`, `PUT`, `PATCH /status`, `DELETE`.
   - Security verification (401 unauthenticated, 403 customer).
   - Enriched customer `GET /categories` with `subcategoriesCount` and `subcategories`.
   - Category safe deletion rules.

### 9.2 Execution Command
```powershell
$env:JAVA_HOME="C:\Users\HP\.jdks\ms-21.0.9"; $env:Path = "$env:JAVA_HOME\bin;$env:Path"
cd cosmeticszainab\Cosmetics-E-Commerce-Backend\artifacts\api-server\spring-app
mvn test
```

### 9.3 Verification Results
- Subcategory unit tests: **12 passed, 0 failures**
- Subcategory integration tests: **12 passed, 0 failures**
- Total application test suite: **133 passed, 0 failures, 0 skipped**

