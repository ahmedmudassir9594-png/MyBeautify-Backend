package com.cosmetics.ecommerce.config;

import com.cosmetics.ecommerce.entity.*;
import com.cosmetics.ecommerce.enums.DiscountType;
import com.cosmetics.ecommerce.enums.Role;
import com.cosmetics.ecommerce.repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.*;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Configuration
public class DevelopmentDataInitializer {
    @Bean
    CommandLineRunner seedData(UserRepository users, CategoryRepository categories, ProductRepository products,
                               CouponRepository coupons, PasswordEncoder encoder,
                               @Value("${app.seed-data:false}") boolean enabled) {
        return args -> {
            if (!enabled || users.count() > 0) return;
            String defaultPassword = System.getenv("SEED_DEFAULT_PASSWORD");
            if (defaultPassword == null || defaultPassword.length() < 8) return;

            User admin = users.save(new User("Ava", "Admin", "admin@cosmetics.local", encoder.encode(defaultPassword), Role.ADMIN));
            User staff = users.save(new User("Sam", "Staff", "staff@cosmetics.local", encoder.encode(defaultPassword), Role.STAFF));
            users.save(new User("Maya", "Customer", "maya@cosmetics.local", encoder.encode(defaultPassword), Role.CUSTOMER));
            users.save(new User("Noah", "Customer", "noah@cosmetics.local", encoder.encode(defaultPassword), Role.CUSTOMER));
            users.save(new User("Lina", "Customer", "lina@cosmetics.local", encoder.encode(defaultPassword), Role.CUSTOMER));

            List<Category> categoryList = categories.saveAll(List.of(
                    new Category("Skincare", "Cleansers, serums, moisturizers, and daily essentials."),
                    new Category("Makeup", "Makeup for everyday looks and special occasions."),
                    new Category("Haircare", "Care and styling products for healthy hair."),
                    new Category("Fragrance", "Perfumes, mists, and scent discovery."),
                    new Category("Body Care", "Nourishing body washes, lotions, and scrubs."),
                    new Category("Lip Care", "Hydrating and colorful lip essentials."),
                    new Category("Eye Makeup", "Mascara, liner, and eyeshadow."),
                    new Category("Beauty Tools", "Brushes, applicators, and beauty accessories.")));


            for (int i = 1; i <= 20; i++) {
                Category category = categoryList.get((i - 1) % categoryList.size());
                Product product = new Product("Demo " + category.getName() + " " + i, "demo-product-" + i,
                        "DEMO-" + String.format("%04d", i), BigDecimal.valueOf(12 + i * 2L), 25 + i, category);
                product.setShortDescription("A development catalog product for testing storefront flows.");
                product.setDescription("This seeded product is available only in development and can be replaced with real catalog data.");
                product.setImageUrl("https://images.unsplash.com/photo-1556228578-8c89e6adf883?w=800");
                product.setFeatured(i <= 6);
                products.save(product);
            }
            Coupon coupon = new Coupon("WELCOME10", DiscountType.PERCENTAGE, BigDecimal.TEN,
                    Instant.now().minus(1, ChronoUnit.DAYS), Instant.now().plus(90, ChronoUnit.DAYS));
            coupon.setDescription("10% off your first order."); coupon.setMaximumDiscount(BigDecimal.valueOf(25));
            coupons.save(coupon);
        };
    }
}