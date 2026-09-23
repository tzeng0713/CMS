package com.example.cms.dto;

import java.util.List;

public record RentPaymentImportRow(
        Integer rowNumber,
        String companyName,
        String paymentMonth,
        String paymentDateText,
        String feeStartDateText,
        String feeEndDateText,
        String amount,
        String receiptNo,
        String note,
        Boolean valid,
        List<String> errors
) {
}
