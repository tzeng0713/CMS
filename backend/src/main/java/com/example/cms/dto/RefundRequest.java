package com.example.cms.dto;

import java.math.BigDecimal;

public record RefundRequest(
        Long customerId,
        Long contractId,
        Long chargeListId,
        String refundReason,
        BigDecimal adjustmentAmount,
        String adjustmentNote,
        BigDecimal deductionTotal,
        String paymentMethod,
        String bankCode,
        String bankAccount,
        String bankAccountName,
        String refundStatus,
        String refundedAt,
        Long staffId
) {
}
