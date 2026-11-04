package com.cosmetics.ecommerce.service;

import com.cosmetics.ecommerce.dto.ComboDtos.*;
import com.cosmetics.ecommerce.entity.Combo;
import com.cosmetics.ecommerce.entity.ComboDiscountRule;
import com.cosmetics.ecommerce.entity.ComboItem;
import com.cosmetics.ecommerce.entity.Product;
import com.cosmetics.ecommerce.enums.DiscountType;
import com.cosmetics.ecommerce.repository.ComboDiscountRuleRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;

@Service
public class ComboPricingService {

    private final ComboDiscountRuleRepository ruleRepository;

    public ComboPricingService(ComboDiscountRuleRepository ruleRepository) {
        this.ruleRepository = ruleRepository;
    }

    public record AllocatedItemPricing(
            Long productId,
            Product product,
            int quantity,
            BigDecimal originalUnitPrice,
            BigDecimal originalSubtotal,
            BigDecimal allocatedDiscount,
            BigDecimal discountedUnitPrice,
            BigDecimal discountedSubtotal
    ) {}

    public record CalculatedComboPricing(
            BigDecimal originalTotal,
            BigDecimal discountAmount,
            BigDecimal finalTotal,
            BigDecimal discountPercentage,
            String appliedRuleName,
            boolean eligible,
            String eligibleMessage,
            List<AllocatedItemPricing> items
    ) {}

    /**
     * Calculates the custom combo preview, evaluating applicable discount rules and allocating
     * discounts proportionally across items.
     */
    public CalculatedComboPricing calculateCustomCombo(
            List<CustomComboItemRequest> requestedItems,
            Map<Long, Product> productMap,
            Instant now
    ) {
        int totalItemCount = 0;
        int distinctProductCount = requestedItems.size();
        BigDecimal originalSubtotal = BigDecimal.ZERO;

        List<AllocatedItemPricing> preliminaryItems = new ArrayList<>();

        for (CustomComboItemRequest req : requestedItems) {
            Product p = productMap.get(req.productId());
            BigDecimal unitPrice = (p != null) ? p.sellingPrice() : BigDecimal.ZERO;
            int qty = req.quantity();
            BigDecimal itemSubtotal = unitPrice.multiply(BigDecimal.valueOf(qty));

            totalItemCount += qty;
            originalSubtotal = originalSubtotal.add(itemSubtotal);

            preliminaryItems.add(new AllocatedItemPricing(
                    req.productId(),
                    p,
                    qty,
                    unitPrice,
                    itemSubtotal,
                    BigDecimal.ZERO,
                    unitPrice,
                    itemSubtotal
            ));
        }

        // Find active & valid rules matching criteria
        List<ComboDiscountRule> activeRules = ruleRepository.findActiveAndValidRules(now);
        ComboDiscountRule matchingRule = null;
        for (ComboDiscountRule r : activeRules) {
            if (r.appliesTo(distinctProductCount, originalSubtotal, now)) {
                matchingRule = r;
                break;
            }
        }

        BigDecimal discountAmount = BigDecimal.ZERO;
        String appliedRuleName = null;
        boolean eligible = false;
        String eligibleMessage;

        if (matchingRule != null) {
            eligible = true;
            appliedRuleName = matchingRule.getName();
            discountAmount = matchingRule.calculateDiscount(originalSubtotal);
            eligibleMessage = "Eligible for " + matchingRule.getName() + "!";
        } else if (distinctProductCount >= 2) {
            // Default built-in promotion: 2 or more products get 10% discount (as depicted in screenshot)
            eligible = true;
            appliedRuleName = "Combo Discount (10%)";
            discountAmount = originalSubtotal.multiply(BigDecimal.valueOf(10))
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            eligibleMessage = "Eligible for 10% Combo Discount!";
        } else {
            eligibleMessage = "Select 2 or more products to get an exciting combo discount!";
        }

        discountAmount = discountAmount.min(originalSubtotal).setScale(2, RoundingMode.HALF_UP);
        BigDecimal finalTotal = originalSubtotal.subtract(discountAmount).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);

        BigDecimal discountPercentage = BigDecimal.ZERO;
        if (originalSubtotal.compareTo(BigDecimal.ZERO) > 0 && discountAmount.compareTo(BigDecimal.ZERO) > 0) {
            discountPercentage = discountAmount.multiply(BigDecimal.valueOf(100))
                    .divide(originalSubtotal, 1, RoundingMode.HALF_UP);
        }

        // Proportional discount allocation
        List<AllocatedItemPricing> allocatedItems = allocateDiscountsProportionally(
                preliminaryItems, originalSubtotal, discountAmount
        );

        return new CalculatedComboPricing(
                originalSubtotal,
                discountAmount,
                finalTotal,
                discountPercentage,
                appliedRuleName,
                eligible,
                eligibleMessage,
                allocatedItems
        );
    }

    /**
     * Calculates proportional pricing distribution for a predefined combo so that
     * sum of item unit prices equals the combo sellingPrice.
     */
    public List<AllocatedItemPricing> allocatePredefinedCombo(Combo combo, int comboQuantity) {
        BigDecimal componentSum = BigDecimal.ZERO;
        List<AllocatedItemPricing> preliminaryItems = new ArrayList<>();

        for (ComboItem item : combo.getItems()) {
            Product p = item.getProduct();
            BigDecimal baseUnitPrice = item.effectivePrice();
            int qty = item.getQuantity() * comboQuantity;
            BigDecimal itemSubtotal = baseUnitPrice.multiply(BigDecimal.valueOf(qty));

            componentSum = componentSum.add(itemSubtotal);
            preliminaryItems.add(new AllocatedItemPricing(
                    p != null ? p.getId() : null,
                    p,
                    qty,
                    baseUnitPrice,
                    itemSubtotal,
                    BigDecimal.ZERO,
                    baseUnitPrice,
                    itemSubtotal
            ));
        }

        BigDecimal comboSellingTotal = combo.getSellingPrice().multiply(BigDecimal.valueOf(comboQuantity));
        BigDecimal totalDiscount = componentSum.subtract(comboSellingTotal).max(BigDecimal.ZERO);

        return allocateDiscountsProportionally(preliminaryItems, componentSum, totalDiscount);
    }

    private List<AllocatedItemPricing> allocateDiscountsProportionally(
            List<AllocatedItemPricing> items,
            BigDecimal originalTotal,
            BigDecimal totalDiscount
    ) {
        if (items.isEmpty()) {
            return Collections.emptyList();
        }

        if (originalTotal.compareTo(BigDecimal.ZERO) <= 0 || totalDiscount.compareTo(BigDecimal.ZERO) <= 0) {
            return items;
        }

        List<AllocatedItemPricing> result = new ArrayList<>();
        BigDecimal accumulatedDiscount = BigDecimal.ZERO;

        for (int i = 0; i < items.size(); i++) {
            AllocatedItemPricing item = items.get(i);
            BigDecimal itemDiscount;

            if (i == items.size() - 1) {
                // Last item absorbs remaining rounding difference
                itemDiscount = totalDiscount.subtract(accumulatedDiscount).max(BigDecimal.ZERO);
            } else {
                itemDiscount = totalDiscount.multiply(item.originalSubtotal())
                        .divide(originalTotal, 2, RoundingMode.HALF_UP);
                accumulatedDiscount = accumulatedDiscount.add(itemDiscount);
            }

            BigDecimal discountedSubtotal = item.originalSubtotal().subtract(itemDiscount).max(BigDecimal.ZERO);
            BigDecimal discountedUnitPrice = discountedSubtotal
                    .divide(BigDecimal.valueOf(item.quantity()), 2, RoundingMode.HALF_UP);

            result.add(new AllocatedItemPricing(
                    item.productId(),
                    item.product(),
                    item.quantity(),
                    item.originalUnitPrice(),
                    item.originalSubtotal(),
                    itemDiscount,
                    discountedUnitPrice,
                    discountedSubtotal
            ));
        }

        return result;
    }
}

