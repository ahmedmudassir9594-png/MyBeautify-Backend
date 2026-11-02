package com.cosmetics.ecommerce.repository;

import com.cosmetics.ecommerce.entity.Category;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CategoryRepository extends JpaRepository<Category, Long> {
    List<Category> findByActiveTrueOrderByNameAsc();
    Page<Category> findByActiveTrueOrderByNameAsc(Pageable pageable);
    boolean existsByNameIgnoreCase(String name);
    long countByImageUrl(String imageUrl);
}