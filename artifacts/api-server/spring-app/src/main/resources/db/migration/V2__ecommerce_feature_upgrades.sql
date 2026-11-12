-- ==============================================================================
-- MALKAZ E-Commerce - Incremental Schema Changes (V2)
-- Features: Combos & Offers, Product Relationships, Subcategories,
--           Razorpay Payments & Webhook Audit, and FK Realignment
-- Database Engine: MySQL 8+ / TiDB Cloud Serverless
-- Character Set: utf8mb4 | Collation: utf8mb4_unicode_ci
-- ==============================================================================

SET FOREIGN_KEY_CHECKS = 0;

-- ------------------------------------------------------------------------------
-- 1. Feature: Combos and Promotional Offers
-- Entities: Combo, ComboItem, ComboDiscountRule
-- ------------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS combos (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(180) NOT NULL,
    slug VARCHAR(220) NOT NULL,
    description VARCHAR(2000) NULL,
    image_file_name VARCHAR(255) NULL,
    original_price DECIMAL(12, 2) NOT NULL DEFAULT 0.00,
    selling_price DECIMAL(12, 2) NOT NULL DEFAULT 0.00,
    discount_amount DECIMAL(12, 2) NOT NULL DEFAULT 0.00,
    start_date DATETIME NULL,
    end_date DATETIME NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    featured BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_combo_name UNIQUE (name),
    CONSTRAINT uk_combo_slug UNIQUE (slug),
    INDEX idx_combo_slug (slug),
    INDEX idx_combo_active (active),
    INDEX idx_combo_dates (start_date, end_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS combo_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    combo_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    quantity INT NOT NULL DEFAULT 1,
    custom_price DECIMAL(12, 2) NULL,
    INDEX idx_combo_items_combo (combo_id),
    INDEX idx_combo_items_product (product_id),
    CONSTRAINT fk_combo_items_combo FOREIGN KEY (combo_id) REFERENCES combos(id) ON DELETE CASCADE,
    CONSTRAINT fk_combo_items_product FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS combo_discount_rules (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    description VARCHAR(500) NULL,
    min_products INT NOT NULL DEFAULT 2,
    discount_type VARCHAR(32) NOT NULL DEFAULT 'PERCENTAGE',
    discount_value DECIMAL(12, 2) NOT NULL DEFAULT 10.00,
    min_subtotal DECIMAL(12, 2) NULL,
    max_discount_amount DECIMAL(12, 2) NULL,
    start_date DATETIME NULL,
    end_date DATETIME NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_combo_rules_active (active),
    INDEX idx_combo_rules_dates (start_date, end_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Modify cart_items to support combo grouping
ALTER TABLE cart_items ADD COLUMN IF NOT EXISTS combo_id BIGINT NULL;
ALTER TABLE cart_items ADD COLUMN IF NOT EXISTS combo_group_id VARCHAR(64) NULL;
ALTER TABLE cart_items ADD COLUMN IF NOT EXISTS combo_type VARCHAR(32) NULL;
ALTER TABLE cart_items ADD COLUMN IF NOT EXISTS combo_name VARCHAR(180) NULL;
ALTER TABLE cart_items ADD COLUMN IF NOT EXISTS original_price DECIMAL(12, 2) NULL;
ALTER TABLE cart_items ADD COLUMN IF NOT EXISTS discount_amount DECIMAL(12, 2) NULL;

-- Modify order_items to support combo attribution
ALTER TABLE order_items ADD COLUMN IF NOT EXISTS combo_id BIGINT NULL;
ALTER TABLE order_items ADD COLUMN IF NOT EXISTS combo_group_id VARCHAR(64) NULL;
ALTER TABLE order_items ADD COLUMN IF NOT EXISTS combo_type VARCHAR(32) NULL;
ALTER TABLE order_items ADD COLUMN IF NOT EXISTS combo_name VARCHAR(180) NULL;
ALTER TABLE order_items ADD COLUMN IF NOT EXISTS original_price DECIMAL(12, 2) NULL;
ALTER TABLE order_items ADD COLUMN IF NOT EXISTS discount_amount DECIMAL(12, 2) NULL;

-- Seed default discount rule if not present
INSERT INTO combo_discount_rules (name, description, min_products, discount_type, discount_value, active)
SELECT 'Combo Discount (10%)', 'Select 2 or more products to get an exciting 10% combo discount!', 2, 'PERCENTAGE', 10.00, TRUE
WHERE NOT EXISTS (SELECT 1 FROM combo_discount_rules WHERE name = 'Combo Discount (10%)');


-- ------------------------------------------------------------------------------
-- 2. Feature: Similar Products / Product Relationships
-- Entity: ProductRelationship
-- ------------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS product_relationships (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    source_product_id BIGINT NOT NULL,
    related_product_id BIGINT NOT NULL,
    relationship_type VARCHAR(32) NOT NULL DEFAULT 'SIMILAR',
    display_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_product_relationship UNIQUE (source_product_id, related_product_id, relationship_type),
    CONSTRAINT fk_rel_source_product FOREIGN KEY (source_product_id) REFERENCES products(id) ON DELETE CASCADE,
    CONSTRAINT fk_rel_related_product FOREIGN KEY (related_product_id) REFERENCES products(id) ON DELETE CASCADE,
    INDEX idx_rel_source (source_product_id),
    INDEX idx_rel_related (related_product_id),
    INDEX idx_rel_type (relationship_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- ------------------------------------------------------------------------------
-- 3. Feature: Category & Subcategory Management
-- Entity: Subcategory
-- ------------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS subcategories (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    slug VARCHAR(150) NULL,
    description VARCHAR(1000) NULL,
    image_url VARCHAR(500) NULL,
    category_id BIGINT NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_subcategory_category FOREIGN KEY (category_id) REFERENCES categories(id) ON DELETE RESTRICT,
    CONSTRAINT uk_subcategory_category_name UNIQUE (category_id, name),
    INDEX idx_subcategory_category (category_id),
    INDEX idx_subcategory_active (active),
    INDEX idx_subcategory_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Associate products with subcategories
ALTER TABLE products
    ADD COLUMN IF NOT EXISTS subcategory_id BIGINT NULL;


-- ------------------------------------------------------------------------------
-- 4. Feature: Razorpay Payment Integration & Webhook Event Audit
-- Entities: Payment (Upgraded), PaymentWebhookEvent
-- ------------------------------------------------------------------------------

ALTER TABLE payments ADD COLUMN IF NOT EXISTS razorpay_order_id VARCHAR(100) NULL;
ALTER TABLE payments ADD COLUMN IF NOT EXISTS razorpay_payment_id VARCHAR(100) NULL;
ALTER TABLE payments ADD COLUMN IF NOT EXISTS razorpay_signature VARCHAR(255) NULL;
ALTER TABLE payments ADD COLUMN IF NOT EXISTS currency VARCHAR(10) NOT NULL DEFAULT 'INR';
ALTER TABLE payments ADD COLUMN IF NOT EXISTS failure_code VARCHAR(100) NULL;
ALTER TABLE payments ADD COLUMN IF NOT EXISTS failure_reason TEXT NULL;
ALTER TABLE payments ADD COLUMN IF NOT EXISTS refund_id VARCHAR(100) NULL;
ALTER TABLE payments ADD COLUMN IF NOT EXISTS refund_status VARCHAR(50) NULL;
ALTER TABLE payments ADD COLUMN IF NOT EXISTS refund_amount DECIMAL(12, 2) NULL;
ALTER TABLE payments ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;

CREATE TABLE IF NOT EXISTS payment_webhook_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    razorpay_event_id VARCHAR(100) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    processing_status VARCHAR(50) NOT NULL,
    payload LONGTEXT NULL,
    received_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP NULL,
    error_message TEXT NULL,
    CONSTRAINT uk_payment_webhook_event_id UNIQUE (razorpay_event_id),
    INDEX idx_webhook_event_type (event_type),
    INDEX idx_webhook_processing_status (processing_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;



-- ------------------------------------------------------------------------------
-- 5. Foreign Key & Index Optimization (Direct TiDB DDL)
-- ------------------------------------------------------------------------------
-- Subcategory FK
ALTER TABLE products 
    ADD CONSTRAINT fk_product_subcategory 
    FOREIGN KEY (subcategory_id) REFERENCES subcategories(id) ON DELETE SET NULL;

-- Razorpay Indexes
CREATE INDEX IF NOT EXISTS idx_payment_razorpay_order_id ON payments(razorpay_order_id);
CREATE INDEX IF NOT EXISTS idx_payment_razorpay_payment_id ON payments(razorpay_payment_id);

SET FOREIGN_KEY_CHECKS = 1;