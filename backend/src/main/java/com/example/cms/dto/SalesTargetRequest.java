package com.example.cms.dto;

public record SalesTargetRequest(
        Long branchId,
        Integer targetMonth,
        String category,
        Integer targetCount,
        Long staffId
) {
}
