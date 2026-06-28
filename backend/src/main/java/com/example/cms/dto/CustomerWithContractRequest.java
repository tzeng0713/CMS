package com.example.cms.dto;

public record CustomerWithContractRequest(
        CustomerRequest customer,
        ContractRequest contract
) {
}
