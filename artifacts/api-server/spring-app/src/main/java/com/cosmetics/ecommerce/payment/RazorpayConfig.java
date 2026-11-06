package com.cosmetics.ecommerce.payment;

import com.razorpay.RazorpayClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RazorpayConfig {
    private static final Logger log = LoggerFactory.getLogger(RazorpayConfig.class);

    private final RazorpayProperties properties;

    public RazorpayConfig(RazorpayProperties properties) {
        this.properties = properties;
    }

    @Bean
    public RazorpayClient razorpayClient() {
        String key = properties.getKeyId();
        String secret = properties.getKeySecret();
        if (key != null && !key.isBlank() && secret != null && !secret.isBlank()) {
            try {
                log.info("Initializing RazorpayClient with Key ID: {}", key.substring(0, Math.min(key.length(), 8)) + "...");
                return new RazorpayClient(key, secret);
            } catch (Exception e) {
                log.error("Failed to initialize RazorpayClient: {}", e.getMessage(), e);
            }
        }
        try {
            return new RazorpayClient("rzp_test_placeholder", "placeholder_secret");
        } catch (Exception e) {
            log.warn("Could not create fallback RazorpayClient: {}", e.getMessage());
            return null;
        }
    }
}

