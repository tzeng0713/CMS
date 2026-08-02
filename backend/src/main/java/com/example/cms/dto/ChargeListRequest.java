package com.example.cms.dto;

import java.math.BigDecimal;

public record ChargeListRequest(
        Long customerId,
        Long contractId,
        String feeStartMonth,
        String feeEndMonth,
        BigDecimal managementFee,
        BigDecimal electricityFee,
        BigDecimal printingFee,
        BigDecimal tax,
        BigDecimal advancePayment,
        BigDecimal repairFee,
        Long updatedBy
) {
}
