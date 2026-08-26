package com.example.cms.dto;

import java.math.BigDecimal;

public record ManualPerformanceBonusRequest(
        Long staffId,
        Long beneficiaryStaffId,
        Long bonusRuleId,
        BigDecimal amount,
        String period,
        String note
) {
}
