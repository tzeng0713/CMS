package com.example.cms.dto;

import java.math.BigDecimal;
import java.util.List;

public record CustomerRequest(
        String companyName,
        String taxId,
        Integer status,
        String rentalItem,
        Integer rentalStatus,
        String ownerName,
        String ownerBirthday,
        String contactPerson,
        String contactBirthday,
        String phone,
        String forwardingAddress,
        BigDecimal pettyCash,
        String referrer,
        String accountantInfo,
        String accountInfo,
        Boolean isAgent,
        List<String> relatedCompanyNames,
        String notes,
        String registrationType,
        Long updatedBy
) {
}
