package com.cosmetics.ecommerce.service;

import com.cosmetics.ecommerce.dto.BannerDtos.HeroBannerRequest;
import com.cosmetics.ecommerce.dto.BannerDtos.HeroBannerResponse;
import com.cosmetics.ecommerce.entity.HeroBanner;
import com.cosmetics.ecommerce.exception.ResourceNotFoundException;
import com.cosmetics.ecommerce.repository.HeroBannerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class HeroBannerService {

    private final HeroBannerRepository bannerRepository;

    public HeroBannerService(HeroBannerRepository bannerRepository) {
        this.bannerRepository = bannerRepository;
    }

    @Transactional(readOnly = true)
    public List<HeroBannerResponse> getActiveBanners() {
        return bannerRepository.findByActiveTrueOrderByDisplayOrderAsc()
                .stream()
                .map(HeroBannerResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<HeroBannerResponse> getAllBanners() {
        return bannerRepository.findAllByOrderByDisplayOrderAsc()
                .stream()
                .map(HeroBannerResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public HeroBannerResponse getBanner(Long id) {
        HeroBanner banner = bannerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Hero banner not found with id: " + id));
        return HeroBannerResponse.from(banner);
    }

    @Transactional
    public HeroBannerResponse createBanner(HeroBannerRequest request) {
        HeroBanner banner = new HeroBanner(
                request.title(),
                request.subtitle(),
                request.description(),
                request.ctaText(),
                request.ctaLink(),
                request.imageUrl(),
                request.displayOrder() != null ? request.displayOrder() : 0,
                request.active() != null ? request.active() : true
        );
        HeroBanner saved = bannerRepository.save(banner);
        return HeroBannerResponse.from(saved);
    }

    @Transactional
    public HeroBannerResponse updateBanner(Long id, HeroBannerRequest request) {
        HeroBanner banner = bannerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Hero banner not found with id: " + id));

        banner.setTitle(request.title());
        banner.setSubtitle(request.subtitle());
        banner.setDescription(request.description());
        if (request.ctaText() != null && !request.ctaText().isBlank()) {
            banner.setCtaText(request.ctaText());
        }
        if (request.ctaLink() != null && !request.ctaLink().isBlank()) {
            banner.setCtaLink(request.ctaLink());
        }
        if (request.imageUrl() != null && !request.imageUrl().isBlank()) {
            banner.setImageUrl(request.imageUrl());
        }
        if (request.displayOrder() != null) {
            banner.setDisplayOrder(request.displayOrder());
        }
        if (request.active() != null) {
            banner.setActive(request.active());
        }

        HeroBanner saved = bannerRepository.save(banner);
        return HeroBannerResponse.from(saved);
    }

    @Transactional
    public void deleteBanner(Long id) {
        if (!bannerRepository.existsById(id)) {
            throw new ResourceNotFoundException("Hero banner not found with id: " + id);
        }
        bannerRepository.deleteById(id);
    }

    @Transactional
    public HeroBannerResponse toggleBannerActive(Long id, boolean active) {
        HeroBanner banner = bannerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Hero banner not found with id: " + id));
        banner.setActive(active);
        HeroBanner saved = bannerRepository.save(banner);
        return HeroBannerResponse.from(saved);
    }
}

