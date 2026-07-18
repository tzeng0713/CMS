package com.example.cms.dto;

import java.math.BigDecimal;

public record ContractRequest(
        Long customerId,
        Long officeId,
        String rentalItem,
        String rentalStatus,
        String signedDateText,
        Long signerStaffId,
        Long partnerStaffId,
        String sourceText,
        Integer paymentMonths,
        String startDateText,
        String endDateText,
        String terminationDateText,
        BigDecimal rent,
        BigDecimal deposit,
        String leaseImagePath,
        String leaseStatus,
        Long updatedBy
) {
}
