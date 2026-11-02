package com.cosmetics.ecommerce.repository;

import com.cosmetics.ecommerce.entity.Product;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.math.BigDecimal;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {
    Optional<Product> findBySlug(String slug);
    Optional<Product> findBySku(String sku);
    Page<Product> findByActiveTrue(Pageable pageable);
    Page<Product> findByFeaturedTrueAndActiveTrue(Pageable pageable);
    Page<Product> findByCategoryIdAndActiveTrue(Long categoryId, Pageable pageable);
    Page<Product> findByBrandIdAndActiveTrue(Long brandId, Pageable pageable);
    @Query("select p from Product p where p.active = true and p.price between :minPrice and :maxPrice")
    Page<Product> findByPriceBetween(@Param("minPrice") BigDecimal minPrice, @Param("maxPrice") BigDecimal maxPrice, Pageable pageable);

    @Query("""
      select p from Product p
      where p.active = true and
      (lower(p.name) like lower(concat('%', :query, '%'))
       or lower(p.description) like lower(concat('%', :query, '%'))
       or lower(p.ingredients) like lower(concat('%', :query, '%'))
       or lower(p.skinType) like lower(concat('%', :query, '%'))
       or lower(p.brand.name) like lower(concat('%', :query, '%'))
       or lower(p.category.name) like lower(concat('%', :query, '%')))
      """)
    Page<Product> search(@Param("query") String query, Pageable pageable);

    @Query("select p from Product p where p.stockQuantity <= p.lowStockThreshold order by p.stockQuantity asc")
    java.util.List<Product> findLowStock();

    @Query("select coalesce(avg(r.rating), 0) from Review r where r.product.id = :productId and r.approved = true")
    Double averageRating(@Param("productId") Long productId);

    long countByImageUrl(String imageUrl);
}