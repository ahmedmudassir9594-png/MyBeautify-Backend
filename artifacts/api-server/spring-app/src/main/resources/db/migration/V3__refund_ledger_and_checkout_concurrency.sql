-- ==============================================================================
-- MALKAZ E-Commerce - Incremental Schema Changes (V3)
-- Feature: Refund Ledger and Checkout Concurrency Protection
-- Database Engine: MySQL 8+ / TiDB Cloud Serverless
-- Character Set: utf8mb4 | Collation: utf8mb4_unicode_ci
-- ==============================================================================

SET FOREIGN_KEY_CHECKS = 0;

CREATE TABLE IF NOT EXISTS refunds (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    payment_id BIGINT NOT NULL,
    internal_refund_ref VARCHAR(100) NOT NULL,
    razorpay_refund_id VARCHAR(100) NULL,
    amount DECIMAL(12, 2) NOT NULL,
    currency VARCHAR(10) NOT NULL DEFAULT 'INR',
    status VARCHAR(50) NOT NULL,
    reason VARCHAR(255) NULL,
    initiated_by VARCHAR(150) NULL,
    failure_reason TEXT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP NULL,
    updated_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_refund_internal_ref UNIQUE (internal_refund_ref),
    CONSTRAINT uk_refund_razorpay_id UNIQUE (razorpay_refund_id),
    CONSTRAINT fk_refund_payment FOREIGN KEY (payment_id) REFERENCES payments(id) ON DELETE RESTRICT,
    INDEX idx_refund_payment_id (payment_id),
    INDEX idx_refund_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET FOREIGN_KEY_CHECKS = 1;

