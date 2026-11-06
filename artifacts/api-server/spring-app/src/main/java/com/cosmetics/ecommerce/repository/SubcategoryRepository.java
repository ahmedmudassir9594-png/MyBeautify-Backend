package com.cosmetics.ecommerce.repository;

import com.cosmetics.ecommerce.entity.Subcategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SubcategoryRepository extends JpaRepository<Subcategory, Long> {

    @Query("SELECT s FROM Subcategory s JOIN FETCH s.category WHERE s.id = :id")
    Optional<Subcategory> findByIdWithCategory(@Param("id") Long id);

    @Query("SELECT s FROM Subcategory s JOIN FETCH s.category WHERE s.id = :id AND s.active = true")
    Optional<Subcategory> findActiveByIdWithCategory(@Param("id") Long id);

    @Query("SELECT s FROM Subcategory s JOIN FETCH s.category WHERE s.slug = :slug AND s.active = true")
    Optional<Subcategory> findActiveBySlugWithCategory(@Param("slug") String slug);

    @Query("SELECT s FROM Subcategory s JOIN FETCH s.category WHERE s.category.id = :categoryId ORDER BY s.name ASC")
    List<Subcategory> findByCategoryIdOrderByNameAsc(@Param("categoryId") Long categoryId);

    @Query("SELECT s FROM Subcategory s JOIN FETCH s.category WHERE s.category.id = :categoryId AND s.active = true ORDER BY s.name ASC")
    List<Subcategory> findByCategoryIdAndActiveTrueOrderByNameAsc(@Param("categoryId") Long categoryId);

    @Query("SELECT s FROM Subcategory s JOIN FETCH s.category WHERE s.active = true ORDER BY s.name ASC")
    List<Subcategory> findByActiveTrueOrderByNameAsc();

    @Query(value = "SELECT s FROM Subcategory s JOIN FETCH s.category ORDER BY s.name ASC",
           countQuery = "SELECT count(s) FROM Subcategory s")
    Page<Subcategory> findAllWithCategory(Pageable pageable);

    @Query(value = "SELECT s FROM Subcategory s JOIN FETCH s.category WHERE " +
            "(:categoryId IS NULL OR s.category.id = :categoryId) AND " +
            "(:active IS NULL OR s.active = :active) AND " +
            "(:query IS NULL OR LOWER(s.name) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(s.description) LIKE LOWER(CONCAT('%', :query, '%')))",
           countQuery = "SELECT count(s) FROM Subcategory s WHERE " +
            "(:categoryId IS NULL OR s.category.id = :categoryId) AND " +
            "(:active IS NULL OR s.active = :active) AND " +
            "(:query IS NULL OR LOWER(s.name) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(s.description) LIKE LOWER(CONCAT('%', :query, '%')))")
    Page<Subcategory> searchAdmin(@Param("query") String query,
                                  @Param("categoryId") Long categoryId,
                                  @Param("active") Boolean active,
                                  Pageable pageable);

    boolean existsByCategoryIdAndNameIgnoreCase(Long categoryId, String name);

    boolean existsByCategoryIdAndNameIgnoreCaseAndIdNot(Long categoryId, String name, Long id);

    long countByCategoryId(Long categoryId);

    long countByCategoryIdAndActiveTrue(Long categoryId);

    long countByImageUrl(String imageUrl);

    @Query("SELECT s.category.id, count(s) FROM Subcategory s GROUP BY s.category.id")
    List<Object[]> countSubcategoriesGroupedByCategory();

    @Query("SELECT s.category.id, count(s) FROM Subcategory s WHERE s.active = true GROUP BY s.category.id")
    List<Object[]> countActiveSubcategoriesGroupedByCategory();
}

