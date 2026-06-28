package com.example.cms.dto;

import java.math.BigDecimal;

public record CustomerRequest(
        String companyName,
        String taxId,
        Integer status,
        String rentalItem,
        Integer rentalStatus,
        String ownerName,
        String ownerBirthday,
        String contactPerson,
        String phone,
        String forwardingAddress,
        BigDecimal pettyCash,
        String referrer,
        String notes,
        String registrationType,
        Long updatedBy
) {
}
