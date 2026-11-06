package com.cosmetics.ecommerce.controller;

import com.cosmetics.ecommerce.payment.RazorpayWebhookService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/payments/razorpay")
public class RazorpayWebhookController {

    private final RazorpayWebhookService webhookService;

    public RazorpayWebhookController(RazorpayWebhookService webhookService) {
        this.webhookService = webhookService;
    }

    @PostMapping("/webhook")
    public ResponseEntity<Map<String, String>> handleWebhook(
            @RequestBody String rawPayload,
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String signatureHeader) {
        if (signatureHeader == null || signatureHeader.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing X-Razorpay-Signature header"));
        }
        webhookService.processWebhook(rawPayload, signatureHeader);
        return ResponseEntity.ok(Map.of("status", "ok"));
    }
}

