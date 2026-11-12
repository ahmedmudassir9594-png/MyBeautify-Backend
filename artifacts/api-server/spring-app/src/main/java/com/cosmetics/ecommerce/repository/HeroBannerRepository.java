package com.cosmetics.ecommerce.repository;

import com.cosmetics.ecommerce.entity.HeroBanner;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface HeroBannerRepository extends JpaRepository<HeroBanner, Long> {
    List<HeroBanner> findByActiveTrueOrderByDisplayOrderAsc();
    List<HeroBanner> findAllByOrderByDisplayOrderAsc();
}

