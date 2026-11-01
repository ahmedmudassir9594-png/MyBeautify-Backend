package com.cosmetics.ecommerce.service;

import com.cosmetics.ecommerce.dto.ReferralDtos.*;
import com.cosmetics.ecommerce.entity.Order;
import com.cosmetics.ecommerce.entity.Referral;
import com.cosmetics.ecommerce.entity.User;
import com.cosmetics.ecommerce.enums.ReferralStatus;
import com.cosmetics.ecommerce.repository.ReferralRepository;
import com.cosmetics.ecommerce.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Service
public class ReferralService {
    private static final String ALPHANUMERIC = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ReferralRepository referralRepository;
    private final UserRepository userRepository;
    private final BigDecimal discountPercent;
    private final BigDecimal minimumOrderAmount;
    private final String discountDescription;
    private final String defaultBaseUrl;

    public ReferralService(
            ReferralRepository referralRepository,
            UserRepository userRepository,
            @Value("${app.referral.discount-percent:10}") BigDecimal discountPercent,
            @Value("${app.referral.min-order:5000.00}") BigDecimal minimumOrderAmount,
            @Value("${app.referral.discount-description:10% off your first jewellery purchase}") String discountDescription,
            @Value("${app.referral.base-url:https://malkaz.vercel.app}") String defaultBaseUrl) {
        this.referralRepository = referralRepository;
        this.userRepository = userRepository;
        this.discountPercent = discountPercent;
        this.minimumOrderAmount = minimumOrderAmount;
        this.discountDescription = discountDescription;
        this.defaultBaseUrl = defaultBaseUrl;
    }

    @Transactional
    public String getOrCreateReferralCode(User user) {
        if (user.getReferralCode() != null && !user.getReferralCode().isBlank()) {
            return user.getReferralCode();
        }
        User managed = userRepository.findById(user.getId()).orElse(user);
        if (managed.getReferralCode() != null && !managed.getReferralCode().isBlank()) {
            return managed.getReferralCode();
        }
        String code = generateUniqueCode();
        managed.setReferralCode(code);
        userRepository.save(managed);
        return code;
    }

    @Transactional
    public ReferralSummaryResponse getSummary(User user, String requestOrigin) {
        String code = getOrCreateReferralCode(user);
        String baseUrl = (requestOrigin != null && !requestOrigin.isBlank()) ? requestOrigin : defaultBaseUrl;
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        String link = baseUrl + "/?ref=" + code;

        long totalReferrals = referralRepository.countByReferrer(user);
        long successfulReferrals = referralRepository.countByReferrerAndStatus(user, ReferralStatus.COMPLETED);

        List<Referral> list = referralRepository.findByReferrerOrderByCreatedAtDesc(user, PageRequest.of(0, 20));
        BigDecimal totalRewards = BigDecimal.ZERO;
        List<ReferralItemDto> recentReferrals = list.stream().map(ref -> {
            String friendName = maskName(ref.getReferredUser().getFullName());
            String friendEmail = maskEmail(ref.getReferredUser().getEmail());
            return new ReferralItemDto(
                    ref.getId(),
                    friendName,
                    friendEmail,
                    ref.getStatus().name(),
                    ref.getCreatedAt(),
                    ref.getCompletedAt()
            );
        }).toList();

        for (Referral r : list) {
            if (r.getRewardAmount() != null) {
                totalRewards = totalRewards.add(r.getRewardAmount());
            }
        }

        return new ReferralSummaryResponse(
                code,
                link,
                discountDescription,
                discountPercent,
                minimumOrderAmount,
                totalReferrals,
                successfulReferrals,
                totalRewards,
                recentReferrals
        );
    }

    @Transactional(readOnly = true)
    public ValidateReferralResponse validateReferralCode(String code, User currentUser) {
        if (code == null || code.trim().isBlank()) {
            return new ValidateReferralResponse(false, "", "", "", BigDecimal.ZERO, "Referral code cannot be blank.");
        }
        String cleanCode = code.trim().toUpperCase();
        Optional<User> referrerOpt = userRepository.findByReferralCodeIgnoreCase(cleanCode);
        if (referrerOpt.isEmpty()) {
            return new ValidateReferralResponse(false, cleanCode, "", "", BigDecimal.ZERO, "Invalid referral code.");
        }
        User referrer = referrerOpt.get();
        if (currentUser != null && referrer.getId().equals(currentUser.getId())) {
            return new ValidateReferralResponse(false, cleanCode, referrer.getFirstName(), "", BigDecimal.ZERO, "You cannot use your own referral code.");
        }
        if (currentUser != null && currentUser.getReferredBy() != null && !currentUser.getReferredBy().getId().equals(referrer.getId())) {
            return new ValidateReferralResponse(false, cleanCode, referrer.getFirstName(), "", BigDecimal.ZERO, "You have already redeemed a referral discount from another member.");
        }
        return new ValidateReferralResponse(
                true,
                cleanCode,
                referrer.getFirstName(),
                discountDescription,
                discountPercent,
                "Valid referral code from " + referrer.getFirstName() + "!"
        );
    }

    @Transactional
    public void linkReferralOnRegister(User newUser, String code) {
        if (code == null || code.trim().isBlank()) return;
        String cleanCode = code.trim().toUpperCase();
        Optional<User> referrerOpt = userRepository.findByReferralCodeIgnoreCase(cleanCode);
        if (referrerOpt.isEmpty()) return;
        User referrer = referrerOpt.get();
        if (referrer.getId().equals(newUser.getId())) return;
        if (referralRepository.existsByReferredUser(newUser)) return;

        newUser.setReferredBy(referrer);
        userRepository.save(newUser);
        Referral referral = new Referral(referrer, newUser, cleanCode);
        referralRepository.save(referral);
    }

    @Transactional
    public void recordQualifyingPurchase(Order order) {
        if (order == null || order.getUser() == null) return;
        User customer = order.getUser();
        if (customer.getReferredBy() == null) return;

        Optional<Referral> referralOpt = referralRepository.findByReferredUser(customer);
        if (referralOpt.isPresent()) {
            Referral referral = referralOpt.get();
            if (referral.getStatus() == ReferralStatus.REGISTERED) {
                referral.setStatus(ReferralStatus.COMPLETED);
                referral.setFirstOrder(order);
                referral.setCompletedAt(Instant.now());
                referral.setRewardAmount(order.getDiscountAmount());
                referralRepository.save(referral);
            }
        }
    }

    public BigDecimal getDiscountPercent() {
        return discountPercent;
    }

    public BigDecimal getMinimumOrderAmount() {
        return minimumOrderAmount;
    }

    public String getDiscountDescription() {
        return discountDescription;
    }

    private String generateUniqueCode() {
        String code;
        int attempts = 0;
        do {
            StringBuilder sb = new StringBuilder("MZ-");
            for (int i = 0; i < 6; i++) {
                sb.append(ALPHANUMERIC.charAt(RANDOM.nextInt(ALPHANUMERIC.length())));
            }
            code = sb.toString();
            attempts++;
            if (attempts > 50) {
                code = "MZ-" + System.currentTimeMillis();
                break;
            }
        } while (userRepository.existsByReferralCode(code));
        return code;
    }

    private String maskEmail(String email) {
        if (email == null || email.isBlank()) return "friend@malkaz.com";
        int at = email.indexOf('@');
        if (at <= 1) return email;
        String namePart = email.substring(0, at);
        String domainPart = email.substring(at);
        if (namePart.length() <= 2) {
            return namePart.charAt(0) + "***" + domainPart;
        }
        return namePart.charAt(0) + "***" + namePart.charAt(namePart.length() - 1) + domainPart;
    }

    private String maskName(String fullName) {
        if (fullName == null || fullName.isBlank()) return "A Friend";
        String[] parts = fullName.trim().split("\\s+");
        if (parts.length == 1) return parts[0];
        return parts[0] + " " + parts[parts.length - 1].charAt(0) + ".";
    }
}
