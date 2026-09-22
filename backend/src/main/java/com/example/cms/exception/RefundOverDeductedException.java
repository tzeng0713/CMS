package com.example.cms.exception;

public class RefundOverDeductedException extends RuntimeException {

    private final Long chargeListId;
    private final Long customerId;
    private final Long contractId;

    public RefundOverDeductedException(String message, Long chargeListId, Long customerId, Long contractId) {
        super(message);
        this.chargeListId = chargeListId;
        this.customerId = customerId;
        this.contractId = contractId;
    }

    public Long getChargeListId() {
        return chargeListId;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public Long getContractId() {
        return contractId;
    }
}
