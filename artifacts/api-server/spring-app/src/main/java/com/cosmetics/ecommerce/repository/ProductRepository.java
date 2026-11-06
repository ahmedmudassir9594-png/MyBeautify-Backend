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
    @Query("select p from Product p where p.active = true and p.price between :minPrice and :maxPrice")
    Page<Product> findByPriceBetween(@Param("minPrice") BigDecimal minPrice, @Param("maxPrice") BigDecimal maxPrice, Pageable pageable);

    @Query("""
      select p from Product p
      where p.active = true and
      (lower(p.name) like lower(concat('%', :query, '%'))
       or lower(p.description) like lower(concat('%', :query, '%'))
       or lower(p.ingredients) like lower(concat('%', :query, '%'))
       or lower(p.skinType) like lower(concat('%', :query, '%'))
       or lower(p.category.name) like lower(concat('%', :query, '%')))
      """)
    Page<Product> search(@Param("query") String query, Pageable pageable);

    @Query("select p from Product p where p.stockQuantity <= p.lowStockThreshold order by p.stockQuantity asc")
    java.util.List<Product> findLowStock();

    @Query("select coalesce(avg(r.rating), 0) from Review r where r.product.id = :productId and r.approved = true")
    Double averageRating(@Param("productId") Long productId);

    long countByImageUrl(String imageUrl);

    long countByCategoryId(Long categoryId);

    long countByCategoryIdAndActiveTrue(Long categoryId);

    long countBySubcategoryId(Long subcategoryId);

    long countBySubcategoryIdAndActiveTrue(Long subcategoryId);

    Page<Product> findBySubcategoryIdAndActiveTrue(Long subcategoryId, Pageable pageable);

    @Query("""
        SELECT p FROM Product p
        JOIN FETCH p.category
        WHERE p.category.id = :categoryId
          AND p.active = true
          AND p.id != :excludeId
        ORDER BY p.featured DESC, p.createdAt DESC
    """)
    java.util.List<Product> findByCategoryExcludingProduct(@Param("categoryId") Long categoryId,
                                                          @Param("excludeId") Long excludeId,
                                                          Pageable pageable);

    @Query("""
        SELECT p FROM Product p
        JOIN FETCH p.category
        WHERE p.active = true
          AND p.id != :excludeId
          AND (
              (p.category.id = :categoryId)
              OR (lower(p.name) LIKE lower(concat('%', :keyword, '%')))
              OR (lower(p.description) LIKE lower(concat('%', :keyword, '%')))
          )
        ORDER BY (CASE WHEN p.category.id = :categoryId THEN 1 ELSE 2 END),
                 p.featured DESC, p.createdAt DESC
    """)
    java.util.List<Product> findCandidateSimilarProducts(@Param("excludeId") Long excludeId,
                                                        @Param("categoryId") Long categoryId,
                                                        @Param("keyword") String keyword,
                                                        Pageable pageable);

    @Query("""
        SELECT p FROM Product p
        JOIN FETCH p.category
        WHERE p.active = true
          AND p.id != :excludeId
        ORDER BY p.featured DESC, p.createdAt DESC
    """)
    java.util.List<Product> findCatalogFallbackProducts(@Param("excludeId") Long excludeId,
                                                       Pageable pageable);
}