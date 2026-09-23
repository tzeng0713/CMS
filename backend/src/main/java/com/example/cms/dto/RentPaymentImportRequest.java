package com.example.cms.dto;

import java.util.List;

public record RentPaymentImportRequest(
        List<RentPaymentImportRow> rows,
        Long updatedBy
) {
}
