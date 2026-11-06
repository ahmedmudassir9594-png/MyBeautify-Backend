# MALKAZ TiDB Cloud Foreign Key Cross-Schema Root Cause Analysis & Audit Report

**Date:** November 2026  
**Target Environment:** E2E Networks VPS (`malkaz.service`)  
**Production Database:** TiDB Cloud Serverless (`8.0.11-TiDB-v8.5.3-serverless`)  
**Production Schema:** `MALKAZ`  
**Investigation Mode:** Strictly READ-ONLY (Zero code, database, or configuration changes executed)

---

## 1. Executive Summary

During production database inspection of the MALKAZ Jewellery E-Commerce backend on TiDB Cloud Serverless, four foreign key constraints inside the `MALKAZ` database schema were discovered pointing across schemas to the `test` database:

| Constraint Name (Typical) | Child Table in `MALKAZ` | Child Column | Referenced (Parent) Table & Schema | Expected Reference in `MALKAZ` |
| :--- | :--- | :--- | :--- | :--- |
| `FK_orders_coupon` | `MALKAZ.orders` | `coupon_id` | **`test.coupons (id)`** | `MALKAZ.coupons (id)` |
| `FK_orders_shipping_address` | `MALKAZ.orders` | `shipping_address_id` | **`test.addresses (id)`** | `MALKAZ.addresses (id)` |
| `FK_orders_user` | `MALKAZ.orders` | `user_id` | **`test.users (id)`** | `MALKAZ.users (id)` |
| `FK_payments_order` | `MALKAZ.payments` | `order_id` | **`test.orders (id)`** | `MALKAZ.orders (id)` |

### Critical Risks Identified
1. **Broken Transactional Integrity & Split-Brain State:**
   - Any order placed in `MALKAZ.orders` validates that the `user_id`, `shipping_address_id`, and `coupon_id` exist in the `test` schema, not `MALKAZ`.
   - When a payment is created in `MALKAZ.payments`, TiDB enforces that `order_id` exists in **`test.orders`**. If the order only exists in `MALKAZ.orders`, payment insertion fails with MySQL/TiDB error:
     `Error 1452: Cannot add or update a child row: a foreign key constraint fails ('MALKAZ'.'payments', CONSTRAINT ... FOREIGN KEY ('order_id') REFERENCES 'test'.'orders' ('id'))`
2. **Accidental Deletion Catastrophe:**
   - If anyone runs `DROP DATABASE test;` or cleans up the default `test` schema in TiDB Cloud, foreign key constraints in `MALKAZ` will break or block table operations, causing complete checkout failure.
3. **Data Isolation Violation:**
   - The production application running on E2E Networks VPS connects to `MALKAZ`, but foreign key dependencies cross into `test`.

---

## 2. Root Cause Analysis

### A. Codebase Audit: Zero Hardcoded Schemas
A comprehensive search was performed across all 23 JPA entities, repository interfaces, configuration classes, and application properties in the Spring Boot backend (`src/main/java/**` and `src/main/resources/**`):
- **JPA `@Table` annotations:** None of the 23 entity classes specify `schema = "test"` or `schema = "MALKAZ"`. Every entity uses unqualified table names:
  - `Order.java`: `@Table(name = "orders", ...)`
  - `Payment.java`: `@Table(name = "payments", ...)`
  - `User.java`: `@Table(name = "users", ...)`
  - `Address.java`: `@Table(name = "addresses", ...)`
  - `Coupon.java`: `@Table(name = "coupons", ...)`
- **Hibernate Default Schema:** The property `spring.jpa.properties.hibernate.default_schema` is **not configured** anywhere in `application.yml` or profile files.
- **Entity Relationship Mappings:** All `@ManyToOne` and `@OneToOne` associations use standard JPA mapping annotations without schema qualifiers.

### B. TiDB Cloud Cluster Initialization & MySQL Resolution Behavior
The root cause stems from the combination of **TiDB Cloud Serverless provisioning defaults** and **MySQL/TiDB cross-database DDL resolution semantics**:

1. **TiDB Cloud Default Database:**
   - When a TiDB Cloud Serverless cluster is created, TiDB automatically provisions a default database named **`test`**.
   - The standard connection string provided in the TiDB Cloud Console defaults to `/test`:
     `jdbc:mysql://<tidb-host>:4000/test?sslMode=VERIFY_IDENTITY`
2. **Historical Hibernate DDL Auto Execution:**
   - In earlier versions of this project (e.g. commit `de66c84`), `application.yml` had:
     ```yaml
     spring:
       jpa:
         hibernate:
           ddl-auto: ${DDL_AUTO:update}
     ```
   - When the backend was first tested against the TiDB Cloud cluster using the default connection string, Hibernate automatically generated the initial tables (`users`, `addresses`, `coupons`, `orders`, `payments`, etc.) inside the **`test`** schema.
3. **The Schema Transition Mechanism (How `test.*` FKs were created):**
   When the production database `MALKAZ` was subsequently created (`CREATE DATABASE MALKAZ;`), one of the following two mechanisms occurred:
   - **Mechanism 1: DDL executed while `test` was the active session database (`USE test;`):**
     In MySQL and TiDB, when an unqualified foreign key reference is executed against a qualified table name:
     ```sql
     -- Current active database: test
     CREATE TABLE MALKAZ.orders (
         id BIGINT AUTO_INCREMENT PRIMARY KEY,
         user_id BIGINT NOT NULL,
         shipping_address_id BIGINT NOT NULL,
         coupon_id BIGINT NULL,
         CONSTRAINT fk_orders_user FOREIGN KEY (user_id) REFERENCES users(id),
         CONSTRAINT fk_orders_address FOREIGN KEY (shipping_address_id) REFERENCES addresses(id),
         CONSTRAINT fk_orders_coupon FOREIGN KEY (coupon_id) REFERENCES coupons(id)
     );
     ```
     Because the referenced tables (`users`, `addresses`, `coupons`) were **not** prefixed with `MALKAZ.`, MySQL and TiDB resolve unqualified table names against the **session's current default database** (`DATABASE()`, which was `test`).
     Consequently, TiDB compiled the foreign keys as:
     `REFERENCES test.users(id)`  
     `REFERENCES test.addresses(id)`  
     `REFERENCES test.coupons(id)`  
     And subsequently when `MALKAZ.payments` was created while in session `test`:
     `REFERENCES test.orders(id)`!
   - **Mechanism 2: Cross-Schema Table Rename (`RENAME TABLE`):**
     If an administrator ran:
     ```sql
     RENAME TABLE test.orders TO MALKAZ.orders;
     ```
     while `users`, `addresses`, and `coupons` remained in `test`, MySQL/TiDB automatically rewrites the foreign key definitions on `MALKAZ.orders` to preserve their link to `test.users`, `test.addresses`, and `test.coupons`.

---

## 3. Flyway Migration Audit (`V1` to `V7`)

Inspection of `src/main/resources/db/migration` and Maven build dependencies revealed:

1. **Flyway is NOT executed by Spring Boot:**
   - `pom.xml` does **not** include `flyway-core` or `flyway-mysql`.
   - `application.yml` contains no `spring.flyway.*` configuration.
   - The SQL scripts in `src/main/resources/db/migration` are manual migration scripts, not auto-applied by Flyway.
2. **Migration Files Present:**
   - **`V1`**: **Does not exist.** The base schema was never managed by a Flyway `V1` script. It was originally generated by Hibernate `ddl-auto: update` or manual SQL.
   - **`V2__create_audit_logs_table.sql`**: Creates `audit_logs` table (no FKs).
   - **`V3__create_combos_and_offers_tables.sql`**: Creates `combos`, `combo_items`, `combo_discount_rules`. References `combos(id)` and `products(id)` (unqualified).
   - **`V4__create_product_relationships_table.sql`**: Creates `product_relationships`. References `products(id)` (unqualified).
   - **`V5__create_subcategories_table.sql`**: Creates `subcategories` table. References `categories(id)` and `subcategories(id)` (unqualified).
   - **`V6__update_combos_image_file_name.sql`**: Schema refactoring for combo images (no FKs).
   - **`V7__create_razorpay_payment_and_webhook_tables.sql`**: Upgrades `payments` table and adds `payment_webhook_events` (no FKs).
3. **Conclusion:**
   No migration file `V1`–`V7` introduced the `test.` cross-schema foreign keys. All migrations use unqualified table references.

---

## 4. Production Deployment Environment Audit (E2E Networks)

### Deployment Architecture
- **Host:** E2E Networks Linux VPS (`/var/www/malkaz/MyBeautify-Backend`)
- **Service:** Systemd unit `malkaz.service`
- **CI/CD:** GitHub Actions (`.github/workflows/backend-ci-cd.yml`):
  1. SSH connection into E2E Networks VPS.
  2. `git reset --hard && git pull origin main`.
  3. `mvn clean package -DskipTests` inside `artifacts/api-server/spring-app`.
  4. `systemctl restart malkaz.service`.

### Re-creation Risk Assessment on E2E Networks
1. **Can deploying the app recreate this problem?**
   - **NO.** In `application.yml`, `spring.jpa.hibernate.ddl-auto` defaults to `none`:
     ```yaml
     spring:
       jpa:
         hibernate:
           ddl-auto: ${DDL_AUTO:none}
     ```
   - With `ddl-auto: none`, Hibernate never generates or modifies DDL on startup.
2. **What happens if someone sets `DDL_AUTO=update` on E2E Networks?**
   - Hibernate's `update` mode only creates missing tables or missing columns.
   - **Hibernate NEVER drops or updates existing foreign keys.** It will leave the existing `test.*` foreign keys completely untouched.
3. **Database URL Risk:**
   - In the VPS environment file (e.g. `/var/www/malkaz/.env` or `/etc/systemd/system/malkaz.service`), `DB_URL` must point to `/MALKAZ`:
     `jdbc:mysql://<tidb-host>:4000/MALKAZ?useSSL=true&verifyServerCertificate=true`
   - If `DB_URL` points to `/MALKAZ` while the foreign keys reference `/test`, write operations to `orders` and `payments` are bound to data in `test`.

---

## 5. Enum Audit: Java Entities vs Database Definitions

### A. Java Entity Enum Definitions

#### 1. `PaymentStatus` (`com.cosmetics.ecommerce.enums.PaymentStatus`)
```java
public enum PaymentStatus {
    PENDING,
    AUTHORIZED,
    SUCCESS,
    FAILED,
    REFUND_PENDING,
    REFUNDED,
    PARTIALLY_REFUNDED
}
```

#### 2. `OrderStatus` (`com.cosmetics.ecommerce.enums.OrderStatus`)
```java
public enum OrderStatus {
    PENDING,
    CONFIRMED,
    PROCESSING,
    PACKED,
    SHIPPED,
    OUT_FOR_DELIVERY,
    DELIVERED,
    CANCELLED,
    RETURN_REQUESTED,
    RETURNED
}
```

#### 3. `PaymentMethod` (`com.cosmetics.ecommerce.enums.PaymentMethod`)
```java
public enum PaymentMethod {
    UPI("UPI"),
    UPI_QR("UPI_QR"),
    NET_BANKING("NET_BANKING"),
    CASH_ON_DELIVERY("CASH_ON_DELIVERY"),
    COD("CASH_ON_DELIVERY");
}
```

### B. JPA Mapping Configuration
- In `Order.java`:
  ```java
  @Enumerated(EnumType.STRING) @Column(nullable = false) private PaymentStatus paymentStatus = PaymentStatus.PENDING;
  @Enumerated(EnumType.STRING) @Column(nullable = false) private OrderStatus orderStatus = OrderStatus.PENDING;
  @Enumerated(EnumType.STRING) @Column(nullable = false) private PaymentMethod paymentMethod;
  ```
  Hibernate maps `@Enumerated(EnumType.STRING)` without explicit `columnDefinition` to `VARCHAR(255)`.
- In `Payment.java`:
  ```java
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 50)
  private PaymentMethod paymentMethod;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 50)
  private PaymentStatus paymentStatus;
  ```
  Maps to `VARCHAR(50)`.

### C. Database Risk: Native MySQL ENUM vs VARCHAR
- **If the TiDB database column is `VARCHAR` (as Hibernate creates):**
  - All values (`AUTHORIZED`, `REFUND_PENDING`, `PARTIALLY_REFUNDED`, `CONFIRMED`, `PACKED`, `OUT_FOR_DELIVERY`, `RETURN_REQUESTED`, `RETURNED`, `UPI`, `UPI_QR`, `NET_BANKING`, `CASH_ON_DELIVERY`) are fully supported.
- **If the TiDB database column was manually created as native `ENUM(...)`:**
  - If any newer Java enum value (e.g. `AUTHORIZED`, `REFUND_PENDING`, `PARTIALLY_REFUNDED` added during Razorpay integration, or `OUT_FOR_DELIVERY`) is missing from the database `ENUM(...)` list, TiDB will abort any INSERT/UPDATE with:
    `Data truncated for column 'payment_status' at row 1` (SQL Error 1265).
- **Audit Query Provided Below:** A read-only verification query is included in Section 6 to confirm whether the column datatype in TiDB is `varchar` or `enum`.

---

## 6. TiDB Read-Only Verification Queries

Run the following queries against TiDB Cloud to verify the current foreign keys and column data types without modifying any data:

### Query 1: Verify Cross-Schema Foreign Keys
```sql
SELECT 
    CONSTRAINT_SCHEMA,
    CONSTRAINT_NAME,
    TABLE_NAME,
    COLUMN_NAME,
    REFERENCED_TABLE_SCHEMA,
    REFERENCED_TABLE_NAME,
    REFERENCED_COLUMN_NAME
FROM information_schema.KEY_COLUMN_USAGE
WHERE CONSTRAINT_SCHEMA = 'MALKAZ'
  AND REFERENCED_TABLE_SCHEMA IS NOT NULL
  AND REFERENCED_TABLE_SCHEMA != 'MALKAZ';
```
*Expected Output:* Shows the 4 foreign keys on `orders` and `payments` where `REFERENCED_TABLE_SCHEMA = 'test'`.

### Query 2: Inspect Column Data Types for Enums
```sql
SELECT 
    TABLE_SCHEMA,
    TABLE_NAME,
    COLUMN_NAME,
    DATA_TYPE,
    COLUMN_TYPE,
    IS_NULLABLE,
    COLUMN_DEFAULT
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = 'MALKAZ'
  AND (
      (TABLE_NAME = 'orders' AND COLUMN_NAME IN ('payment_status', 'order_status', 'payment_method'))
      OR
      (TABLE_NAME = 'payments' AND COLUMN_NAME IN ('payment_status', 'payment_method'))
  );
```
*Verification Check:* If `DATA_TYPE = 'varchar'`, the schema is aligned with JPA. If `DATA_TYPE = 'enum'`, the column should be altered to `VARCHAR(50)` to prevent enum truncation errors.

### Query 3: Check for Orphaned Records Prior to FK Realignment
Before dropping and recreating foreign keys pointing to `MALKAZ`, verify that all referenced parent IDs exist inside `MALKAZ`:
```sql
-- Check for orders referencing users not in MALKAZ.users
SELECT o.id, o.user_id 
FROM MALKAZ.orders o 
LEFT JOIN MALKAZ.users u ON o.user_id = u.id 
WHERE u.id IS NULL AND o.user_id IS NOT NULL;

-- Check for orders referencing addresses not in MALKAZ.addresses
SELECT o.id, o.shipping_address_id 
FROM MALKAZ.orders o 
LEFT JOIN MALKAZ.addresses a ON o.shipping_address_id = a.id 
WHERE a.id IS NULL AND o.shipping_address_id IS NOT NULL;

-- Check for orders referencing coupons not in MALKAZ.coupons
SELECT o.id, o.coupon_id 
FROM MALKAZ.orders o 
LEFT JOIN MALKAZ.coupons c ON o.coupon_id = c.id 
WHERE c.id IS NULL AND o.coupon_id IS NOT NULL;

-- Check for payments referencing orders not in MALKAZ.orders
SELECT p.id, p.order_id 
FROM MALKAZ.payments p 
LEFT JOIN MALKAZ.orders o ON p.order_id = o.id 
WHERE o.id IS NULL AND p.order_id IS NOT NULL;
```
*Requirement:* All 4 queries must return 0 rows. If any return rows, copy the missing parent records from `test` to `MALKAZ` before altering foreign keys.

---

## 7. Recommended Safe Correction Strategy (For Approval)

> [!IMPORTANT]
> Do NOT execute these SQL statements until thoroughly reviewed. Perform this maintenance window during low traffic. Always generate a full TiDB Cloud cluster backup before running DDL modifications.

### Step 1: Pre-Migration Backup
1. In TiDB Cloud Console, navigate to **Backups** and trigger a **Manual Backup** of the cluster.
2. Confirm backup status is `SUCCESS`.

### Step 2: Dynamically Identify Existing Constraint Names
Because constraint names can be randomly generated by Hibernate (e.g. `FKc31vhv282tcv77ny973e4b77q`), run:
```sql
SELECT 
    TABLE_NAME,
    CONSTRAINT_NAME,
    COLUMN_NAME,
    REFERENCED_TABLE_NAME
FROM information_schema.KEY_COLUMN_USAGE
WHERE CONSTRAINT_SCHEMA = 'MALKAZ'
  AND REFERENCED_TABLE_SCHEMA = 'test';
```
Note the exact constraint names returned for:
- `orders.coupon_id` (e.g. `fk_orders_coupon` or `FK_...`)
- `orders.shipping_address_id` (e.g. `fk_orders_address` or `FK_...`)
- `orders.user_id` (e.g. `fk_orders_user` or `FK_...`)
- `payments.order_id` (e.g. `fk_payments_order` or `FK_...`)

### Step 3: Safe DDL Execution Script (Re-point FKs to `MALKAZ`)
Execute inside database `MALKAZ`:
```sql
USE MALKAZ;

-- 1. Drop the cross-schema foreign keys on 'orders'
-- (Replace <actual_fk_name> with the exact constraint name found in Step 2)
ALTER TABLE orders DROP FOREIGN KEY <fk_orders_coupon_name>;
ALTER TABLE orders DROP FOREIGN KEY <fk_orders_shipping_address_name>;
ALTER TABLE orders DROP FOREIGN KEY <fk_orders_user_name>;

-- 2. Drop the cross-schema foreign key on 'payments'
ALTER TABLE payments DROP FOREIGN KEY <fk_payments_order_name>;

-- 3. Re-create foreign keys explicitly referencing MALKAZ schema
ALTER TABLE orders
    ADD CONSTRAINT fk_orders_user 
        FOREIGN KEY (user_id) REFERENCES MALKAZ.users(id),
    ADD CONSTRAINT fk_orders_shipping_address 
        FOREIGN KEY (shipping_address_id) REFERENCES MALKAZ.addresses(id),
    ADD CONSTRAINT fk_orders_coupon 
        FOREIGN KEY (coupon_id) REFERENCES MALKAZ.coupons(id) ON DELETE SET NULL;

ALTER TABLE payments
    ADD CONSTRAINT fk_payments_order 
        FOREIGN KEY (order_id) REFERENCES MALKAZ.orders(id) ON DELETE RESTRICT;
```

### Step 4: If Native DB ENUMs Exist, Convert to VARCHAR
If Query 2 from Section 6 showed `DATA_TYPE = 'enum'`, standardize them to `VARCHAR(50)` to align with JPA `@Enumerated(EnumType.STRING)`:
```sql
USE MALKAZ;

ALTER TABLE orders 
    MODIFY COLUMN payment_status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    MODIFY COLUMN order_status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    MODIFY COLUMN payment_method VARCHAR(50) NOT NULL;

ALTER TABLE payments 
    MODIFY COLUMN payment_status VARCHAR(50) NOT NULL,
    MODIFY COLUMN payment_method VARCHAR(50) NOT NULL;
```

### Step 5: Post-Execution Verification
```sql
-- Ensure ZERO cross-schema foreign keys remain
SELECT COUNT(*) AS cross_schema_fk_count
FROM information_schema.KEY_COLUMN_USAGE
WHERE CONSTRAINT_SCHEMA = 'MALKAZ'
  AND REFERENCED_TABLE_SCHEMA IS NOT NULL
  AND REFERENCED_TABLE_SCHEMA != 'MALKAZ';
-- MUST RETURN 0.

-- Verify the new constraints reference MALKAZ
SELECT 
    CONSTRAINT_NAME, TABLE_NAME, COLUMN_NAME, REFERENCED_TABLE_SCHEMA, REFERENCED_TABLE_NAME
FROM information_schema.KEY_COLUMN_USAGE
WHERE CONSTRAINT_SCHEMA = 'MALKAZ'
  AND TABLE_NAME IN ('orders', 'payments')
  AND REFERENCED_TABLE_SCHEMA IS NOT NULL;
-- All REFERENCED_TABLE_SCHEMA values MUST BE 'MALKAZ'.
```

---

## 8. Pre-Deployment Safety Checklist for E2E Networks

- [ ] **TiDB Cloud Backup:** Verified manual snapshot created before executing any DDL.
- [ ] **Orphan Records Verification:** Confirmed that `orders` and `payments` in `MALKAZ` have corresponding parent keys in `MALKAZ.users`, `MALKAZ.addresses`, `MALKAZ.coupons`.
- [ ] **Cross-Schema FK Count:** Verified via `information_schema.KEY_COLUMN_USAGE` that cross-schema FK count is 0 after running the correction script.
- [ ] **VPS Environment File (`/var/www/malkaz/.env` or systemd):**
  - Confirmed `DB_URL` specifies `/MALKAZ` database:
    `DB_URL=jdbc:mysql://<tidb-host>:4000/MALKAZ?useSSL=true&verifyServerCertificate=true`
  - Confirmed `DDL_AUTO=none` is set.
- [ ] **Service Restart Verification on VPS:**
  - Execute: `sudo systemctl restart malkaz.service`
  - Verify active state: `sudo systemctl status malkaz.service`
  - Tail logs to verify clean boot without Hibernate or SQL errors:
    `sudo journalctl -u malkaz.service -n 100 -f`
- [ ] **End-to-End Test Checkout:**
  - Place a test order via the storefront.
  - Verify order record in `MALKAZ.orders` and payment record in `MALKAZ.payments` without any foreign key violation errors.

