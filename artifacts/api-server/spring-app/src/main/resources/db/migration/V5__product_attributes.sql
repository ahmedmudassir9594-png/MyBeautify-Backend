-- ==============================================================================
-- Flyway Migration V5: Product Attributes (Dynamic Category-Wise Attributes)
-- TiDB Cloud, MySQL 8.0 & H2 Compatible Schema
-- ==============================================================================

ALTER TABLE products
    ADD COLUMN IF NOT EXISTS attributes_json TEXT NULL;

