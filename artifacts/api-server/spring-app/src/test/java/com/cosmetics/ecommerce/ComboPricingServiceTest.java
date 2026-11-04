package com.cosmetics.ecommerce;

import com.cosmetics.ecommerce.dto.ComboDtos.CustomComboItemRequest;
import com.cosmetics.ecommerce.entity.Category;
import com.cosmetics.ecommerce.entity.Combo;
import com.cosmetics.ecommerce.entity.ComboDiscountRule;
import com.cosmetics.ecommerce.entity.ComboItem;
import com.cosmetics.ecommerce.entity.Product;
import com.cosmetics.ecommerce.enums.DiscountType;
import com.cosmetics.ecommerce.repository.ComboDiscountRuleRepository;
import com.cosmetics.ecommerce.service.ComboPricingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ComboPricingServiceTest {

    @Mock
    private ComboDiscountRuleRepository ruleRepository;

    private ComboPricingService pricingService;
    private Category jewelleryCategory;

    @BeforeEach
    void setUp() {
        pricingService = new ComboPricingService(ruleRepository);
        jewelleryCategory = new Category("Royal Jewellery", "High end gold and diamond jewellery");
    }

    @Test
    @DisplayName("Custom combo with 2 items gets default 10% discount when no DB rule is configured")
    void testCustomComboDefaultTenPercentDiscount() {
        Product necklace = new Product("Royal Necklace", "necklace", "SKU-N1", BigDecimal.valueOf(799.00), 10, jewelleryCategory);
        Product earrings = new Product("Royal Earrings", "earrings", "SKU-E1", BigDecimal.valueOf(399.00), 10, jewelleryCategory);

        Map<Long, Product> productMap = new HashMap<>();
        productMap.put(1L, necklace);
        productMap.put(2L, earrings);

        when(ruleRepository.findActiveAndValidRules(any())).thenReturn(Collections.emptyList());

        List<CustomComboItemRequest> items = List.of(
                new CustomComboItemRequest(1L, 1),
                new CustomComboItemRequest(2L, 1)
        );

        ComboPricingService.CalculatedComboPricing result =
                pricingService.calculateCustomCombo(items, productMap, Instant.now());

        // Subtotal = 799 + 399 = 1198.00
        assertThat(result.originalTotal()).isEqualByComparingTo("1198.00");
        // 10% of 1198 = 119.80
        assertThat(result.discountAmount()).isEqualByComparingTo("119.80");
        // Final total = 1198.00 - 119.80 = 1078.20
        assertThat(result.finalTotal()).isEqualByComparingTo("1078.20");
        assertThat(result.eligible()).isTrue();

        // Check proportional allocation sums to exactly discountAmount
        BigDecimal sumItemDiscount = result.items().stream()
                .map(ComboPricingService.AllocatedItemPricing::allocatedDiscount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sumItemDiscount).isEqualByComparingTo(result.discountAmount());

        // Check allocated discounted unit prices sum to exactly finalTotal
        BigDecimal sumItemFinal = result.items().stream()
                .map(ComboPricingService.AllocatedItemPricing::discountedSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sumItemFinal).isEqualByComparingTo(result.finalTotal());
    }

    @Test
    @DisplayName("Custom combo with 3 items matching screenshot examples (Necklace 799 + Earrings 399 + Ring 299)")
    void testCustomComboMatchesScreenshotExample() {
        Product necklace = new Product("Necklace", "necklace", "SKU-NECK", BigDecimal.valueOf(799.00), 10, jewelleryCategory);
        Product earrings = new Product("Earrings", "earrings", "SKU-EAR", BigDecimal.valueOf(399.00), 10, jewelleryCategory);
        Product ring = new Product("Ring", "ring", "SKU-RING", BigDecimal.valueOf(299.00), 10, jewelleryCategory);

        Map<Long, Product> productMap = new HashMap<>();
        productMap.put(1L, necklace);
        productMap.put(2L, earrings);
        productMap.put(3L, ring);

        when(ruleRepository.findActiveAndValidRules(any())).thenReturn(Collections.emptyList());

        List<CustomComboItemRequest> items = List.of(
                new CustomComboItemRequest(1L, 1),
                new CustomComboItemRequest(2L, 1),
                new CustomComboItemRequest(3L, 1)
        );

        ComboPricingService.CalculatedComboPricing result =
                pricingService.calculateCustomCombo(items, productMap, Instant.now());

        // Subtotal = 799 + 399 + 299 = 1497.00
        assertThat(result.originalTotal()).isEqualByComparingTo("1497.00");
        // 10% of 1497 = 149.70 -> rounds to 150 in UI or 149.70 in 2-dec precision
        assertThat(result.discountAmount()).isEqualByComparingTo("149.70");
        // Final total = 1497.00 - 149.70 = 1347.30
        assertThat(result.finalTotal()).isEqualByComparingTo("1347.30");
        assertThat(result.eligible()).isTrue();

        // Exact penny conservation
        BigDecimal sumAllocated = result.items().stream()
                .map(ComboPricingService.AllocatedItemPricing::allocatedDiscount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sumAllocated).isEqualByComparingTo("149.70");
    }

    @Test
    @DisplayName("Custom combo with only 1 item receives 0 discount and eligible=false")
    void testCustomComboSingleItemNoDiscount() {
        Product necklace = new Product("Necklace", "necklace", "SKU-NECK", BigDecimal.valueOf(799.00), 10, jewelleryCategory);

        Map<Long, Product> productMap = new HashMap<>();
        productMap.put(1L, necklace);

        when(ruleRepository.findActiveAndValidRules(any())).thenReturn(Collections.emptyList());

        List<CustomComboItemRequest> items = List.of(
                new CustomComboItemRequest(1L, 1)
        );

        ComboPricingService.CalculatedComboPricing result =
                pricingService.calculateCustomCombo(items, productMap, Instant.now());

        assertThat(result.originalTotal()).isEqualByComparingTo("799.00");
        assertThat(result.discountAmount()).isEqualByComparingTo("0.00");
        assertThat(result.finalTotal()).isEqualByComparingTo("799.00");
        assertThat(result.eligible()).isFalse();
    }

    @Test
    @DisplayName("Predefined combo price allocation matches selling price with penny-level accuracy")
    void testPredefinedComboProportionalAllocation() {
        // Royal Elegance combo: Necklace (1200) + Earrings (799), original=1999.00, sellingPrice=1499.00
        Product necklace = new Product("Necklace", "necklace", "SKU-N1", BigDecimal.valueOf(1200.00), 10, jewelleryCategory);
        Product earrings = new Product("Earrings", "earrings", "SKU-E1", BigDecimal.valueOf(799.00), 10, jewelleryCategory);

        Combo combo = new Combo("Royal Elegance", "royal-elegance", "A regal combo", BigDecimal.valueOf(1499.00));
        combo.setOriginalPrice(BigDecimal.valueOf(1999.00));
        combo.addItem(new ComboItem(combo, necklace, 1));
        combo.addItem(new ComboItem(combo, earrings, 1));

        List<ComboPricingService.AllocatedItemPricing> allocated =
                pricingService.allocatePredefinedCombo(combo, 1);

        assertThat(allocated).hasSize(2);

        BigDecimal totalDiscount = allocated.stream()
                .map(ComboPricingService.AllocatedItemPricing::allocatedDiscount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        // Original 1999 - Selling 1499 = 500
        assertThat(totalDiscount).isEqualByComparingTo("500.00");

        BigDecimal totalAllocatedSelling = allocated.stream()
                .map(ComboPricingService.AllocatedItemPricing::discountedSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(totalAllocatedSelling).isEqualByComparingTo("1499.00");
    }

    @Test
    @DisplayName("Custom rule with max discount cap is strictly enforced")
    void testCustomRuleWithMaxDiscountCap() {
        Product necklace = new Product("Diamond Necklace", "diamond-necklace", "SKU-D1", BigDecimal.valueOf(5000.00), 10, jewelleryCategory);
        Product bangles = new Product("Gold Bangles", "gold-bangles", "SKU-B1", BigDecimal.valueOf(3000.00), 10, jewelleryCategory);

        Map<Long, Product> productMap = new HashMap<>();
        productMap.put(1L, necklace);
        productMap.put(2L, bangles);

        ComboDiscountRule cappedRule = new ComboDiscountRule("Big Spender 20%", 2, DiscountType.PERCENTAGE, BigDecimal.valueOf(20.00));
        cappedRule.setMaxDiscountAmount(BigDecimal.valueOf(500.00)); // Cap at 500

        when(ruleRepository.findActiveAndValidRules(any())).thenReturn(List.of(cappedRule));

        List<CustomComboItemRequest> items = List.of(
                new CustomComboItemRequest(1L, 1),
                new CustomComboItemRequest(2L, 1)
        );

        ComboPricingService.CalculatedComboPricing result =
                pricingService.calculateCustomCombo(items, productMap, Instant.now());

        // Original = 8000.00, 20% = 1600.00, but capped at 500.00
        assertThat(result.originalTotal()).isEqualByComparingTo("8000.00");
        assertThat(result.discountAmount()).isEqualByComparingTo("500.00");
        assertThat(result.finalTotal()).isEqualByComparingTo("7500.00");
    }
}

