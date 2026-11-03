package com.cosmetics.ecommerce;

import com.cosmetics.ecommerce.dto.CartDtos.CartItemRequest;
import com.cosmetics.ecommerce.entity.*;
import com.cosmetics.ecommerce.enums.Role;
import com.cosmetics.ecommerce.exception.InsufficientStockException;
import com.cosmetics.ecommerce.repository.*;
import com.cosmetics.ecommerce.service.CartService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(org.mockito.junit.jupiter.MockitoExtension.class)
class CartServiceTest {
    @Mock CartRepository carts;
    @Mock CartItemRepository items;
    @Mock ProductRepository products;
    @InjectMocks CartService service;

    @Test
    void addRejectsQuantityAboveAvailableStock() {
        User user = new User("Maya", "Perera", "maya@example.com", "hashed", Role.CUSTOMER);
        Category category = new Category("Skincare", "Care");
        Product product = new Product("Serum", "serum", "SKU-1", BigDecimal.TEN, 2, category);
        when(carts.findByUserId(any())).thenReturn(Optional.of(new Cart(user)));
        when(products.findById(1L)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> service.add(user, new CartItemRequest(1L, 3)))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessage("Only 2 units are available.");
        verify(carts, never()).save(any());
    }
}