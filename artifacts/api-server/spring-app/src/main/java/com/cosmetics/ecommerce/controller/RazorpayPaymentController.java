package com.cosmetics.ecommerce.controller;

import com.cosmetics.ecommerce.dto.ApiResponse;
import com.cosmetics.ecommerce.dto.PaymentDtos.*;
import com.cosmetics.ecommerce.entity.User;
import com.cosmetics.ecommerce.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/payments")
public class RazorpayPaymentController {

    private final OrderService orderService;

    public RazorpayPaymentController(OrderService orderService) {
        this.orderService = orderService;
    }

    /**
     * Phase A: Create Razorpay Order
     */
    @PostMapping("/razorpay/create-order")
    public ApiResponse<RazorpayOrderResponse> createRazorpayOrder(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody RazorpayOrderRequest request) {
        RazorpayOrderResponse response = orderService.createRazorpayOrder(user, request);
        return ApiResponse.ok("Razorpay payment order created successfully.", response);
    }

    /**
     * Phase B: Verify Razorpay Payment Signature
     */
    @PostMapping("/razorpay/verify")
    public ApiResponse<RazorpayVerifyResponse> verifyPayment(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody RazorpayVerifyRequest request) {
        RazorpayVerifyResponse response = orderService.verifyRazorpayPayment(user, request);
        return ApiResponse.ok("Payment verified and order confirmed successfully.", response);
    }

    /**
     * Dynamic UPI QR Code Generation
     */
    @PostMapping("/razorpay/qr-code")
    public ApiResponse<RazorpayQrResponse> createQrCode(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody RazorpayQrRequest request) {
        RazorpayQrResponse response = orderService.createRazorpayQrCode(user, request);
        return ApiResponse.ok("Dynamic UPI QR code generated successfully.", response);
    }

    /**
     * Get Payment Details by Order ID
     */
    @GetMapping("/{orderId}")
    public ApiResponse<PaymentStatusResponse> getPaymentStatus(
            @AuthenticationPrincipal User user,
            @PathVariable Long orderId) {
        PaymentStatusResponse response = orderService.getPaymentStatus(user, orderId);
        return ApiResponse.ok("Payment details retrieved successfully.", response);
    }
}

