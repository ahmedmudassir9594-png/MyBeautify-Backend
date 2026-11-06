-- ==============================================================================
-- MALKAZ E-Commerce - Baseline Database Schema (V1)
-- Database Engine: MySQL 8+ / TiDB Cloud Serverless
-- Character Set: utf8mb4 | Collation: utf8mb4_unicode_ci
-- ==============================================================================

SET FOREIGN_KEY_CHECKS = 0;

-- ------------------------------------------------------------------------------
-- 1. Table: users
-- Entity: com.cosmetics.ecommerce.entity.User
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    first_name VARCHAR(255) NOT NULL,
    last_name VARCHAR(255) NOT NULL,
    full_name VARCHAR(255) NOT NULL,
    email VARCHAR(190) NOT NULL,
    google_id VARCHAR(255) NULL,
    password VARCHAR(255) NOT NULL,
    phone_number VARCHAR(255) NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'CUSTOMER',
    profile_image VARCHAR(255) NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    referral_code VARCHAR(40) NULL,
    referred_by_id BIGINT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_user_email UNIQUE (email),
    CONSTRAINT uk_user_google_id UNIQUE (google_id),
    CONSTRAINT uk_user_referral_code UNIQUE (referral_code),
    CONSTRAINT fk_user_referred_by FOREIGN KEY (referred_by_id) REFERENCES users(id) ON DELETE SET NULL,
    INDEX idx_user_email (email),
    INDEX idx_user_role (role)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------------------------------------------------
-- 2. Table: addresses
-- Entity: com.cosmetics.ecommerce.entity.Address
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS addresses (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    full_name VARCHAR(255) NOT NULL,
    phone_number VARCHAR(255) NOT NULL,
    address_line1 VARCHAR(255) NOT NULL,
    address_line2 VARCHAR(255) NULL,
    city VARCHAR(255) NOT NULL,
    state VARCHAR(255) NULL,
    postal_code VARCHAR(255) NOT NULL,
    country VARCHAR(255) NOT NULL,
    address_type VARCHAR(20) NOT NULL DEFAULT 'HOME',
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_address_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_address_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------------------------------------------------
-- 3. Table: categories
-- Entity: com.cosmetics.ecommerce.entity.Category
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS categories (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    description VARCHAR(1000) NULL,
    image_url VARCHAR(255) NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_category_name UNIQUE (name),
    INDEX idx_category_name (name),
    INDEX idx_category_active (active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------------------------------------------------
-- 4. Table: coupons
-- Entity: com.cosmetics.ecommerce.entity.Coupon
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS coupons (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(40) NOT NULL,
    description VARCHAR(500) NULL,
    discount_type VARCHAR(20) NOT NULL,
    discount_value DECIMAL(12, 2) NOT NULL,
    minimum_order_amount DECIMAL(12, 2) NOT NULL DEFAULT 0.00,
    maximum_discount DECIMAL(12, 2) NULL,
    start_date TIMESTAMP NOT NULL,
    expiry_date TIMESTAMP NOT NULL,
    usage_limit INT NULL,
    used_count INT NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uk_coupon_code UNIQUE (code),
    INDEX idx_coupon_code (code),
    INDEX idx_coupon_active (active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------------------------------------------------
-- 5. Table: products
-- Entity: com.cosmetics.ecommerce.entity.Product
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS products (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(180) NOT NULL,
    slug VARCHAR(220) NOT NULL,
    sku VARCHAR(80) NOT NULL,
    description VARCHAR(3000) NULL,
    short_description VARCHAR(500) NULL,
    price DECIMAL(12, 2) NOT NULL,
    discount_price DECIMAL(12, 2) NULL,
    stock_quantity INT NOT NULL DEFAULT 0,
    low_stock_threshold INT NOT NULL DEFAULT 5,
    category_id BIGINT NOT NULL,
    image_url VARCHAR(255) NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    featured BOOLEAN NOT NULL DEFAULT FALSE,
    ingredients VARCHAR(1000) NULL,
    benefits VARCHAR(1000) NULL,
    how_to_use VARCHAR(1000) NULL,
    skin_type VARCHAR(255) NULL,
    shade VARCHAR(255) NULL,
    size VARCHAR(255) NULL,
    expiry_date TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_product_slug UNIQUE (slug),
    CONSTRAINT uk_product_sku UNIQUE (sku),
    CONSTRAINT fk_product_category FOREIGN KEY (category_id) REFERENCES categories(id) ON DELETE RESTRICT,
    INDEX idx_product_slug (slug),
    INDEX idx_product_sku (sku),
    INDEX idx_product_active (active),
    INDEX idx_product_category (category_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------------------------------------------------
-- 6. Table: product_variants
-- Entity: com.cosmetics.ecommerce.entity.ProductVariant
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS product_variants (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT NOT NULL,
    variant_name VARCHAR(255) NOT NULL,
    sku VARCHAR(255) NOT NULL,
    price DECIMAL(12, 2) NOT NULL,
    stock_quantity INT NOT NULL DEFAULT 0,
    image_url VARCHAR(255) NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uk_variant_sku UNIQUE (sku),
    CONSTRAINT fk_variant_product FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE,
    INDEX idx_variant_sku (sku),
    INDEX idx_variant_product (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------------------------------------------------
-- 7. Table: product_images
-- Entity: com.cosmetics.ecommerce.entity.ProductImage
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS product_images (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT NOT NULL,
    image_url VARCHAR(255) NOT NULL,
    display_order INT NOT NULL DEFAULT 0,
    CONSTRAINT fk_image_product FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE,
    INDEX idx_image_product (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------------------------------------------------
-- 8. Table: carts
-- Entity: com.cosmetics.ecommerce.entity.Cart
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS carts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_cart_user UNIQUE (user_id),
    CONSTRAINT fk_cart_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_cart_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------------------------------------------------
-- 9. Table: cart_items
-- Entity: com.cosmetics.ecommerce.entity.CartItem
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS cart_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    cart_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    quantity INT NOT NULL DEFAULT 1,
    price DECIMAL(12, 2) NOT NULL,
    CONSTRAINT fk_cart_items_cart FOREIGN KEY (cart_id) REFERENCES carts(id) ON DELETE CASCADE,
    CONSTRAINT fk_cart_items_product FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE,
    INDEX idx_cart_items_cart (cart_id),
    INDEX idx_cart_items_product (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------------------------------------------------
-- 10. Table: orders
-- Entity: com.cosmetics.ecommerce.entity.Order
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS orders (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_number VARCHAR(40) NOT NULL,
    user_id BIGINT NOT NULL,
    shipping_address_id BIGINT NOT NULL,
    subtotal DECIMAL(12, 2) NOT NULL,
    discount_amount DECIMAL(12, 2) NOT NULL DEFAULT 0.00,
    shipping_charge DECIMAL(12, 2) NOT NULL DEFAULT 0.00,
    tax_amount DECIMAL(12, 2) NOT NULL DEFAULT 0.00,
    total_amount DECIMAL(12, 2) NOT NULL,
    coupon_id BIGINT NULL,
    payment_status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    order_status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    payment_method VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_order_number UNIQUE (order_number),
    CONSTRAINT fk_orders_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_orders_shipping_address FOREIGN KEY (shipping_address_id) REFERENCES addresses(id) ON DELETE RESTRICT,
    CONSTRAINT fk_orders_coupon FOREIGN KEY (coupon_id) REFERENCES coupons(id) ON DELETE SET NULL,
    INDEX idx_order_number (order_number),
    INDEX idx_order_user (user_id),
    INDEX idx_order_shipping_address (shipping_address_id),
    INDEX idx_order_coupon (coupon_id),
    INDEX idx_order_status (order_status),
    INDEX idx_order_payment_status (payment_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------------------------------------------------
-- 11. Table: order_items
-- Entity: com.cosmetics.ecommerce.entity.OrderItem
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS order_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    product_name VARCHAR(255) NOT NULL,
    quantity INT NOT NULL DEFAULT 1,
    price DECIMAL(12, 2) NOT NULL,
    subtotal DECIMAL(12, 2) NOT NULL,
    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE,
    CONSTRAINT fk_order_items_product FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE RESTRICT,
    INDEX idx_order_items_order (order_id),
    INDEX idx_order_items_product (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------------------------------------------------
-- 12. Table: payments
-- Entity: com.cosmetics.ecommerce.entity.Payment
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS payments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    transaction_id VARCHAR(100) NOT NULL,
    payment_gateway VARCHAR(50) NOT NULL,
    amount DECIMAL(12, 2) NOT NULL,
    payment_method VARCHAR(50) NOT NULL,
    payment_status VARCHAR(50) NOT NULL,
    paid_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_payment_transaction UNIQUE (transaction_id),
    CONSTRAINT fk_payments_order FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE RESTRICT,
    INDEX idx_payment_transaction (transaction_id),
    INDEX idx_payment_order (order_id),
    INDEX idx_payment_status (payment_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------------------------------------------------
-- 13. Table: reviews
-- Entity: com.cosmetics.ecommerce.entity.Review
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS reviews (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    order_id BIGINT NOT NULL,
    rating INT NOT NULL,
    comment VARCHAR(2000) NOT NULL,
    approved BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_review_user_product_order UNIQUE (user_id, product_id, order_id),
    CONSTRAINT fk_review_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_review_product FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE,
    CONSTRAINT fk_review_order FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE,
    INDEX idx_review_user (user_id),
    INDEX idx_review_product (product_id),
    INDEX idx_review_order (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------------------------------------------------
-- 14. Table: wishlists
-- Entity: com.cosmetics.ecommerce.entity.Wishlist
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS wishlists (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_wishlist_user_product UNIQUE (user_id, product_id),
    CONSTRAINT fk_wishlist_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_wishlist_product FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE,
    INDEX idx_wishlist_user (user_id),
    INDEX idx_wishlist_product (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------------------------------------------------
-- 15. Table: referrals
-- Entity: com.cosmetics.ecommerce.entity.Referral
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS referrals (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    referrer_id BIGINT NOT NULL,
    referred_user_id BIGINT NOT NULL,
    referral_code VARCHAR(40) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'REGISTERED',
    first_order_id BIGINT NULL,
    reward_amount DECIMAL(12, 2) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    completed_at TIMESTAMP NULL,
    CONSTRAINT uk_referral_referred UNIQUE (referred_user_id),
    CONSTRAINT fk_referral_referrer FOREIGN KEY (referrer_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_referral_referred_user FOREIGN KEY (referred_user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_referral_first_order FOREIGN KEY (first_order_id) REFERENCES orders(id) ON DELETE SET NULL,
    INDEX idx_referral_referrer (referrer_id),
    INDEX idx_referral_referred (referred_user_id),
    INDEX idx_referral_code (referral_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------------------------------------------------
-- 16. Table: audit_logs
-- Entity: com.cosmetics.ecommerce.entity.AuditLog
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS audit_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    actor_id BIGINT NULL,
    actor_email VARCHAR(190) NULL,
    actor_role VARCHAR(50) NULL,
    action VARCHAR(50) NOT NULL,
    resource_type VARCHAR(100) NULL,
    resource_id VARCHAR(100) NULL,
    result VARCHAR(20) NOT NULL DEFAULT 'SUCCESS',
    description VARCHAR(1000) NULL,
    previous_state TEXT NULL,
    new_state TEXT NULL,
    request_method VARCHAR(10) NULL,
    request_endpoint VARCHAR(255) NULL,
    ip_address VARCHAR(64) NULL,
    correlation_id VARCHAR(64) NULL,
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_audit_logs_timestamp (timestamp),
    INDEX idx_audit_logs_actor_id (actor_id),
    INDEX idx_audit_logs_action (action),
    INDEX idx_audit_logs_resource (resource_type, resource_id),
    INDEX idx_audit_logs_result (result)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------------------------------------------------
-- 17. Table: password_reset_tokens
-- Entity: com.cosmetics.ecommerce.entity.PasswordResetToken
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS password_reset_tokens (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    token VARCHAR(120) NOT NULL,
    user_id BIGINT NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_reset_token UNIQUE (token),
    CONSTRAINT fk_reset_token_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_reset_token (token),
    INDEX idx_reset_token_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET FOREIGN_KEY_CHECKS = 1;

