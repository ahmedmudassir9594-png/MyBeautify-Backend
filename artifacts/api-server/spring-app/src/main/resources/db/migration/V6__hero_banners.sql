

-- ==============================================================================
-- Flyway Migration V6: Admin-Managed Storefront Hero Banners
-- TiDB Cloud, MySQL 8.0 & H2 Compatible Schema
-- ==============================================================================

CREATE TABLE IF NOT EXISTS hero_banners (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    subtitle VARCHAR(255) NULL,
    description VARCHAR(500) NULL,
    cta_text VARCHAR(100) DEFAULT 'Explore Collection',
    cta_link VARCHAR(255) DEFAULT '/shop',
    image_url TEXT NOT NULL,
    display_order INT DEFAULT 0,
    active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Seed initial default banners using existing storefront hero image assets
INSERT INTO hero_banners (title, subtitle, description, cta_text, cta_link, image_url, display_order, active)
VALUES
('Elegance in Every Detail', 'FINE JEWELLERY COLLECTION', 'Discover handcrafted fine jewellery designed to illuminate every celebration with enduring radiance.', 'Explore Collection', '/shop', '/hero-jewellery.jpg', 1, TRUE),
('Modern Craft, Timeless Beauty', 'NEW ARRIVALS 2026', 'Explore timeless designs selected for everyday elegance and royal festivities.', 'Shop the Collection', '/shop', '/products/jewels/designer-gold-bangles.jpg', 2, TRUE),
('Adorn Yourself in Splendour', 'EXCLUSIVE EDIT', 'Curated royal sets, emerald drop earrings, and statement solitaires.', 'View Collection', '/categories', '/products/jewels/emerald-drop-earrings.jpg', 3, TRUE);

