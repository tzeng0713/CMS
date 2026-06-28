package com.example.cms.dto;

import java.math.BigDecimal;

public record RentPaymentRequest(
        Long customerId,
        Long contractId,
        Integer paymentMonth,
        String paymentDateText,
        String feeStartDateText,
        String feeEndDateText,
        BigDecimal amount,
        String receiptNo,
        String note,
        Long updatedBy
) {
}
