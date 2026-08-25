package com.example.cms.dto;

public record SettleMonthlyBonusRequest(
        String yearMonth,
        Long staffId
) {
}
