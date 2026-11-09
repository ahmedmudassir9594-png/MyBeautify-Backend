package com.cosmetics.ecommerce.service;

import com.cosmetics.ecommerce.dto.CatalogDtos.ProductResponse;
import com.cosmetics.ecommerce.entity.*;
import com.cosmetics.ecommerce.exception.*;
import com.cosmetics.ecommerce.repository.*;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Objects;

@Service
public class WishlistService {
    private final WishlistRepository wishlists;
    private final ProductRepository products;
    private final ProductService productService;

    public WishlistService(WishlistRepository wishlists, ProductRepository products, ProductService productService) {
        this.wishlists = wishlists;
        this.products = products;
        this.productService = productService;
    }

    @Transactional
    public List<ProductResponse> list(User user) {
        return wishlists.findByUserIdOrderByCreatedAtDesc(user.getId()).stream()
                .map(w -> {
                    try {
                        Product product = w.getProduct();
                        if (product == null || product.getId() == null) {
                            wishlists.delete(w);
                            return null;
                        }
                        return productService.response(product);
                    } catch (EntityNotFoundException ex) {
                        wishlists.delete(w);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .toList();
    }

    @Transactional
    public List<ProductResponse> add(User user, Long productId) {
        Product product = products.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found."));
        if (wishlists.findByUserIdAndProductId(user.getId(), productId).isEmpty()) {
            wishlists.save(new Wishlist(user, product));
        }
        return list(user);
    }

    @Transactional
    public void remove(User user, Long productId) {
        Wishlist wishlist = wishlists.findByUserIdAndProductId(user.getId(), productId)
                .orElseThrow(() -> new ResourceNotFoundException("Wishlist item not found."));
        wishlists.delete(wishlist);
    }
}