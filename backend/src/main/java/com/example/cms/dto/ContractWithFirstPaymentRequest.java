package com.example.cms.dto;

import java.math.BigDecimal;

public record ContractWithFirstPaymentRequest(
        ContractRequest contract,
        BigDecimal firstPaymentAmount,
        String firstPaymentDateText
) {
}
