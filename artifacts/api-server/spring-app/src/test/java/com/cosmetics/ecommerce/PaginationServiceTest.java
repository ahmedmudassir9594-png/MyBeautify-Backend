package com.cosmetics.ecommerce;

import com.cosmetics.ecommerce.dto.CatalogDtos.CategoryResponse;
import com.cosmetics.ecommerce.dto.PageResponse;
import com.cosmetics.ecommerce.dto.UserResponse;
import com.cosmetics.ecommerce.entity.Category;
import com.cosmetics.ecommerce.entity.Coupon;
import com.cosmetics.ecommerce.entity.User;
import com.cosmetics.ecommerce.enums.DiscountType;
import com.cosmetics.ecommerce.enums.Role;
import com.cosmetics.ecommerce.repository.CategoryRepository;
import com.cosmetics.ecommerce.repository.CouponRepository;
import com.cosmetics.ecommerce.repository.UserRepository;
import com.cosmetics.ecommerce.service.CatalogService;
import com.cosmetics.ecommerce.service.CouponService;
import com.cosmetics.ecommerce.service.UserAdminService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PaginationServiceTest {

    @Test
    void userAdminServiceReturnsPagedResponse() {
        UserRepository users = Mockito.mock(UserRepository.class);
        UserAdminService service = new UserAdminService(users);
        User user = new User("Zainab", "Ali", "zainab@example.com", "secret", Role.ADMIN);
        Page<User> page = new PageImpl<>(List.of(user), PageRequest.of(1, 2), 5);
        Mockito.when(users.findAll(Mockito.any(Pageable.class))).thenReturn(page);

        PageResponse<UserResponse> response = service.list(PageRequest.of(1, 2));

        assertEquals(2, response.size());
        assertEquals(1, response.page());
        assertEquals(5, response.totalElements());
        assertEquals(3, response.totalPages());
        assertEquals("zainab@example.com", response.content().get(0).email());
    }

    @Test
    void couponServiceReturnsPagedResponse() {
        CouponRepository coupons = Mockito.mock(CouponRepository.class);
        com.cosmetics.ecommerce.service.ReferralService referralService = Mockito.mock(com.cosmetics.ecommerce.service.ReferralService.class); CouponService service = new CouponService(coupons, referralService);
        Coupon coupon = new Coupon("SAVE10", DiscountType.PERCENTAGE, new BigDecimal("10.00"), Instant.now(), Instant.now().plusSeconds(3600));
        coupon.setDescription("Test coupon");
        Page<Coupon> page = new PageImpl<>(List.of(coupon), PageRequest.of(0, 10), 1);
        Mockito.when(coupons.findAll(Mockito.any(Pageable.class))).thenReturn(page);

        PageResponse<com.cosmetics.ecommerce.dto.CouponDtos.CouponResponse> response = service.list(PageRequest.of(0, 10));

        assertEquals(10, response.size());
        assertEquals(0, response.page());
        assertEquals(1, response.totalElements());
        assertEquals(1, response.totalPages());
        assertEquals("SAVE10", response.content().get(0).code());
    }

    @Test
    void catalogServiceReturnsPagedCategoryAndBrandResponses() {
        CategoryRepository categories = Mockito.mock(CategoryRepository.class);
        CatalogService service = new CatalogService(categories);

        Category category = new Category("Cleansers", "Face cleansers for daily makeup and impurity removal.");
        category.setActive(true);


        Mockito.when(categories.findByActiveTrueOrderByNameAsc(Mockito.any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(category), PageRequest.of(0, 20), 1));

        PageResponse<CategoryResponse> categoriesResponse = service.categories(PageRequest.of(0, 20));

        assertEquals(1, categoriesResponse.totalElements());
        assertEquals("Cleansers", categoriesResponse.content().get(0).name());

    }
}
