package com.cosmetics.ecommerce.payment;

import com.razorpay.RazorpayClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.util.Arrays;

@Configuration
public class RazorpayConfig {
    private static final Logger log = LoggerFactory.getLogger(RazorpayConfig.class);

    private final RazorpayProperties properties;
    private final Environment environment;

    public RazorpayConfig(RazorpayProperties properties, Environment environment) {
        this.properties = properties;
        this.environment = environment;
    }

    @Bean
    public RazorpayClient razorpayClient() {
        boolean isProduction = Arrays.asList(environment.getActiveProfiles()).contains("prod")
                || Arrays.asList(environment.getActiveProfiles()).contains("production");

        String key = properties.getKeyId();
        String secret = properties.getKeySecret();

        if (isProduction) {
            if (key == null || key.isBlank() || secret == null || secret.isBlank() || key.startsWith("rzp_test_")) {
                throw new IllegalStateException(
                        "FATAL: Production profile active ('" + Arrays.toString(environment.getActiveProfiles())
                                + "') but valid live Razorpay credentials are not configured! Set RAZORPAY_KEY_ID (rzp_live_...) and RAZORPAY_KEY_SECRET."
                );
            }
        }

        if (key != null && !key.isBlank() && secret != null && !secret.isBlank()) {
            try {
                log.info("Initializing RazorpayClient with Key ID: {}", key.substring(0, Math.min(key.length(), 8)) + "...");
                return new RazorpayClient(key, secret);
            } catch (Exception e) {
                log.error("Failed to initialize RazorpayClient: {}", e.getMessage(), e);
                if (isProduction) {
                    throw new IllegalStateException("Failed to initialize production RazorpayClient: " + e.getMessage(), e);
                }
            }
        }

        if (!isProduction) {
            try {
                return new RazorpayClient("rzp_test_placeholder", "placeholder_secret");
            } catch (Exception e) {
                log.warn("Could not create fallback RazorpayClient in non-production mode: {}", e.getMessage());
                return null;
            }
        }

        return null;
    }
}

