package com.example.cms.dto;

import java.math.BigDecimal;

public record BonusRuleRequest(
        String ruleName,
        String ruleType,
        BigDecimal unitAmount,
        BigDecimal percentage,
        String tierConfig,
        String periodType,
        String description,
        Boolean isActive,
        Long staffId
) {
}
