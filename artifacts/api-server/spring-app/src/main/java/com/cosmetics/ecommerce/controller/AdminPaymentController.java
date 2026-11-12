package com.cosmetics.ecommerce.controller;

import com.cosmetics.ecommerce.dto.ApiResponse;
import com.cosmetics.ecommerce.dto.PaymentDtos.PaymentStatusResponse;
import com.cosmetics.ecommerce.dto.PaymentDtos.RefundRecordResponse;
import com.cosmetics.ecommerce.dto.PaymentDtos.RefundRequest;
import com.cosmetics.ecommerce.dto.PaymentDtos.RefundResponse;
import com.cosmetics.ecommerce.entity.Payment;
import com.cosmetics.ecommerce.entity.Refund;
import com.cosmetics.ecommerce.entity.User;
import com.cosmetics.ecommerce.enums.AuditAction;
import com.cosmetics.ecommerce.enums.AuditResult;
import com.cosmetics.ecommerce.payment.RefundService;
import com.cosmetics.ecommerce.repository.PaymentRepository;
import com.cosmetics.ecommerce.service.AuditLogService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/payments")
public class AdminPaymentController {

    private final PaymentRepository paymentRepository;
    private final RefundService refundService;
    private final ObjectProvider<AuditLogService> auditLogServiceProvider;

    public AdminPaymentController(PaymentRepository paymentRepository,
                                  RefundService refundService,
                                  ObjectProvider<AuditLogService> auditLogServiceProvider) {
        this.paymentRepository = paymentRepository;
        this.refundService = refundService;
        this.auditLogServiceProvider = auditLogServiceProvider;
    }

    @PostMapping("/{paymentId}/refund")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<RefundResponse> processRefund(
            @PathVariable Long paymentId,
            @Valid @RequestBody(required = false) RefundRequest request,
            @AuthenticationPrincipal User adminUser) {

        java.math.BigDecimal amount = request != null ? request.amount() : null;
        String reason = request != null ? request.reason() : "Customer requested refund";
        String initiatedBy = adminUser != null ? adminUser.getEmail() : "ADMIN";

        RefundResponse response = refundService.processRefund(paymentId, amount, reason, initiatedBy);

        AuditLogService audit = auditLogServiceProvider.getIfAvailable();
        if (audit != null) {
            String details = String.format("{\"paymentId\":%d,\"refundId\":\"%s\",\"refundAmount\":%s,\"refundStatus\":\"%s\",\"reason\":\"%s\",\"initiatedBy\":\"%s\"}",
                    paymentId, response.refundId(), response.refundAmount(), response.refundStatus(), reason, initiatedBy);
            audit.logEvent(AuditAction.PAYMENT_REFUND, AuditResult.SUCCESS, "PAYMENT", response.refundId(),
                    "Admin processed refund of " + response.refundAmount() + " for payment " + paymentId, null, details);
        }

        return ApiResponse.ok(response.message(), response);
    }

    @GetMapping("/{paymentId}/refunds")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<List<RefundRecordResponse>> getRefundHistory(@PathVariable Long paymentId) {
        List<Refund> refunds = refundService.getRefundsForPayment(paymentId);
        List<RefundRecordResponse> dtos = refunds.stream()
                .map(RefundRecordResponse::from)
                .toList();
        return ApiResponse.ok("Refund records retrieved successfully.", dtos);
    }

    @GetMapping("/review-required")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<List<PaymentStatusResponse>> listReviewRequiredPayments() {
        List<Payment> payments = paymentRepository.findByPaymentStatus(com.cosmetics.ecommerce.enums.PaymentStatus.REFUND_REVIEW_REQUIRED);
        List<PaymentStatusResponse> dtos = payments.stream()
                .map(PaymentStatusResponse::from)
                .toList();
        return ApiResponse.ok("Payments requiring manual review / refund retrieved successfully.", dtos);
    }
}
