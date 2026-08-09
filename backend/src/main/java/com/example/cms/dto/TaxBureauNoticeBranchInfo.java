package com.example.cms.dto;

public record TaxBureauNoticeBranchInfo(
        Long branchId,
        String taxOfficeName,
        String responsiblePerson,
        String contactPhone
) {
}
