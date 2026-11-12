package com.cosmetics.ecommerce;

import com.cosmetics.ecommerce.dto.CouponDtos.ApplyCouponRequest;
import com.cosmetics.ecommerce.entity.Coupon;
import com.cosmetics.ecommerce.enums.DiscountType;
import com.cosmetics.ecommerce.repository.CouponRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CouponIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    @AfterEach
    void cleanup() {
        couponRepository.deleteAll();
    }

    @Test
    @DisplayName("Public GET /coupons returns active and valid coupons without authentication")
    void testPublicActiveCoupons() throws Exception {
        Instant now = Instant.now();

        // 1. Active & Valid Coupon
        Coupon validCoupon = new Coupon("MALKAZ20", DiscountType.PERCENTAGE, new BigDecimal("20.00"),
                now.minus(1, ChronoUnit.DAYS), now.plus(10, ChronoUnit.DAYS));
        validCoupon.setDescription("20% off luxury collection");
        validCoupon.setMinimumOrderAmount(new BigDecimal("100.00"));
        validCoupon.setActive(true);
        couponRepository.save(validCoupon);

        // 2. Expired Coupon
        Coupon expiredCoupon = new Coupon("EXPIRED10", DiscountType.PERCENTAGE, new BigDecimal("10.00"),
                now.minus(10, ChronoUnit.DAYS), now.minus(1, ChronoUnit.DAYS));
        expiredCoupon.setActive(true);
        couponRepository.save(expiredCoupon);

        // 3. Inactive Coupon
        Coupon inactiveCoupon = new Coupon("INACTIVE30", DiscountType.PERCENTAGE, new BigDecimal("30.00"),
                now.minus(1, ChronoUnit.DAYS), now.plus(10, ChronoUnit.DAYS));
        inactiveCoupon.setActive(false);
        couponRepository.save(inactiveCoupon);

        mockMvc.perform(get("/coupons")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].code").value("MALKAZ20"))
                .andExpect(jsonPath("$.data[0].discountValue").value(20.00));
    }

    @Test
    @DisplayName("Public POST /coupons/apply verifies valid coupon")
    void testApplyCoupon() throws Exception {
        Instant now = Instant.now();
        Coupon coupon = new Coupon("GOLD50", DiscountType.FIXED, new BigDecimal("50.00"),
                now.minus(1, ChronoUnit.DAYS), now.plus(5, ChronoUnit.DAYS));
        coupon.setMinimumOrderAmount(new BigDecimal("200.00"));
        coupon.setActive(true);
        couponRepository.save(coupon);

        ApplyCouponRequest request = new ApplyCouponRequest("GOLD50", new BigDecimal("300.00"));

        mockMvc.perform(post("/coupons/apply")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.code").value("GOLD50"))
                .andExpect(jsonPath("$.data.discountValue").value(50.00));
    }
}
