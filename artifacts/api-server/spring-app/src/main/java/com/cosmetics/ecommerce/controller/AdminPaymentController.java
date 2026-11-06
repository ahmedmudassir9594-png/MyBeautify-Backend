package com.cosmetics.ecommerce.controller;

import com.cosmetics.ecommerce.dto.ApiResponse;
import com.cosmetics.ecommerce.dto.PaymentDtos.RefundRequest;
import com.cosmetics.ecommerce.dto.PaymentDtos.RefundResponse;
import com.cosmetics.ecommerce.entity.Payment;
import com.cosmetics.ecommerce.enums.AuditAction;
import com.cosmetics.ecommerce.enums.AuditResult;
import com.cosmetics.ecommerce.exception.ResourceNotFoundException;
import com.cosmetics.ecommerce.payment.RazorpayService;
import com.cosmetics.ecommerce.repository.PaymentRepository;
import com.cosmetics.ecommerce.service.AuditLogService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/payments")
public class AdminPaymentController {

    private final PaymentRepository paymentRepository;
    private final RazorpayService razorpayService;
    private final ObjectProvider<AuditLogService> auditLogServiceProvider;

    public AdminPaymentController(PaymentRepository paymentRepository,
                                  RazorpayService razorpayService,
                                  ObjectProvider<AuditLogService> auditLogServiceProvider) {
        this.paymentRepository = paymentRepository;
        this.razorpayService = razorpayService;
        this.auditLogServiceProvider = auditLogServiceProvider;
    }

    @PostMapping("/{paymentId}/refund")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<RefundResponse> processRefund(
            @PathVariable Long paymentId,
            @Valid @RequestBody(required = false) RefundRequest request) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment record not found with ID: " + paymentId));

        java.math.BigDecimal amount = request != null ? request.amount() : null;
        String reason = request != null ? request.reason() : "Customer requested refund";

        RefundResponse response = razorpayService.processRefund(payment, amount, reason);

        AuditLogService audit = auditLogServiceProvider.getIfAvailable();
        if (audit != null) {
            String details = String.format("{\"paymentId\":%d,\"refundId\":\"%s\",\"refundAmount\":%s,\"refundStatus\":\"%s\",\"reason\":\"%s\"}",
                    paymentId, response.refundId(), response.refundAmount(), response.refundStatus(), reason);
            audit.logEvent(AuditAction.PAYMENT_REFUND, AuditResult.SUCCESS, "PAYMENT", response.refundId(),
                    "Admin processed refund of " + response.refundAmount() + " for payment " + paymentId, null, details);
        }

        return ApiResponse.ok(response.message(), response);
    }
}

