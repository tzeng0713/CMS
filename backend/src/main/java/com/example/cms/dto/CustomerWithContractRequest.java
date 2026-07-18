package com.example.cms.dto;

import java.math.BigDecimal;

public record CustomerWithContractRequest(
        CustomerRequest customer,
        ContractRequest contract,
        BigDecimal firstPaymentAmount,
        String firstPaymentDateText
) {
}
