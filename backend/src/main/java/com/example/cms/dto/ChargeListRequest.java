package com.example.cms.dto;

import java.math.BigDecimal;

public record ChargeListRequest(
        Long customerId,
        Long contractId,
        String feeMonth,
        BigDecimal managementFee,
        BigDecimal electricityFee,
        BigDecimal printingFee,
        BigDecimal meetingRoomFee,
        BigDecimal tax,
        BigDecimal advancePayment,
        BigDecimal repairFee,
        Long updatedBy
) {
}
