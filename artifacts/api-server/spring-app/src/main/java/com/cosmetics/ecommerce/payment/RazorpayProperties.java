package com.cosmetics.ecommerce.payment;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.razorpay")
public class RazorpayProperties {
    private String keyId;
    private String keySecret;
    private String webhookSecret;
    private String currency = "INR";
    private String companyName = "MALKAZ Luxury Jewellery";
    private String themeColor = "#0A3B2C";

    public String getKeyId() {
        return keyId;
    }

    public void setKeyId(String keyId) {
        this.keyId = keyId;
    }

    public String getKeySecret() {
        return keySecret;
    }

    public void setKeySecret(String keySecret) {
        this.keySecret = keySecret;
    }

    public String getWebhookSecret() {
        return webhookSecret;
    }

    public void setWebhookSecret(String webhookSecret) {
        this.webhookSecret = webhookSecret;
    }

    public String getCurrency() {
        return currency != null && !currency.isBlank() ? currency : "INR";
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getCompanyName() {
        return companyName != null ? companyName : "MALKAZ Luxury Jewellery";
    }

    public void setCompanyName(String companyName) {
        this.companyName = companyName;
    }

    public String getThemeColor() {
        return themeColor != null ? themeColor : "#0A3B2C";
    }

    public void setThemeColor(String themeColor) {
        this.themeColor = themeColor;
    }

    public boolean isConfigured() {
        return keyId != null && !keyId.isBlank() && keySecret != null && !keySecret.isBlank();
    }
}

