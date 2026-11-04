package com.cosmetics.ecommerce.repository;

import com.cosmetics.ecommerce.entity.Combo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ComboRepository extends JpaRepository<Combo, Long> {

    Optional<Combo> findBySlug(String slug);

    boolean existsByNameIgnoreCase(String name);

    boolean existsBySlugIgnoreCase(String slug);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    boolean existsBySlugIgnoreCaseAndIdNot(String slug, Long id);

    @Query("SELECT c FROM Combo c WHERE c.active = true AND (c.startDate IS NULL OR c.startDate <= :now) AND (c.endDate IS NULL OR c.endDate >= :now)")
    Page<Combo> findActiveAndValid(@Param("now") Instant now, Pageable pageable);

    @Query("SELECT c FROM Combo c WHERE c.active = true AND c.featured = true AND (c.startDate IS NULL OR c.startDate <= :now) AND (c.endDate IS NULL OR c.endDate >= :now)")
    List<Combo> findFeaturedActiveAndValid(@Param("now") Instant now);

    Page<Combo> findByNameContainingIgnoreCase(String query, Pageable pageable);
}

